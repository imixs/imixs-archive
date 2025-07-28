/*******************************************************************************
 *  Imixs Workflow Technology
 *  Copyright (C) 2001, 2008 Imixs Software Solutions GmbH,
 *  http://www.imixs.com
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You can receive a copy of the GNU General Public
 *  License at http://www.gnu.org/licenses/gpl.html
 *
 *  Contributors:
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika
 *******************************************************************************/
package org.imixs.archive.backup;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.apache.commons.net.ftp.FTPSClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.backup.util.FTPConnector;
import org.imixs.archive.backup.util.LogController;
import org.imixs.archive.backup.util.RestClientHelper;
import org.imixs.melman.DocumentClient;
import org.imixs.melman.EventLogClient;
import org.imixs.melman.RestAPIException;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.exceptions.InvalidAccessException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.EJBException;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.inject.Inject;

/**
 * The FullBackupService starts a full backup of all data stored in a workflow
 * instance. The service simply creates Event Log entries from the topic
 * 'snapshot.backup' for each existing snapshot
 * <p>
 * The service class runs a TimerService based on the given scheduler
 * configuration.
 * <p>
 * The service automatically stops after all backup events where created
 * successful.
 * <p>
 * Note: Data which already exists in the backup instance will not be
 * overwritten.
 *
 *
 * @version 1.0
 * @author rsoika
 */

@Singleton
@Startup
public class FullBackupService {

    private static Logger logger = Logger.getLogger(FullBackupService.class.getName());
    public static final String EVENTLOG_TOPIC_FULLBACKUP = "snapshot.backup";
    @Inject
    LogController logController;

    // timeout interval in ms
    static long interval = 5000;

    @Inject
    @ConfigProperty(name = BackupService.ENV_WORKFLOW_SYNC_INITIALDELAY, defaultValue = "30000")
    long initialDelay;

    @Inject
    @ConfigProperty(name = BackupService.ENV_WORKFLOW_SERVICE_ENDPOINT)
    Optional<String> workflowServiceEndpoint;

    @Inject
    @ConfigProperty(name = BackupService.ENV_BACKUP_MIRROR_ID)
    Optional<String> backupMirrorId;

    @Resource
    TimerService timerService;

    @Inject
    RestClientHelper restClientHelper;

    @Inject
    FTPConnector ftpConnector;

    @Inject
    FullBackupStatusHandler fullBackupStatusHandler;

    @PostConstruct
    public void init() {
        if (!workflowServiceEndpoint.isPresent()) {
            logController.warning(BackupService.TOPIC_FULLBACKUP,
                    "Missing environment param 'WORKFLOW_SERVICE_ENDPOINT' - please verify configuration!");
        }
    }

    /**
     * This is the method which processes the timeout event depending on the running
     * timer settings. The method lookups the event log entries and pushes new
     * snapshots into the archive service.
     * <p>
     * Each eventLogEntry is locked to guaranty exclusive processing.
     *
     * @throws BackupException
     *
     * @throws RestAPIException
     **/
    @SuppressWarnings("unused")
    @Timeout
    public void onTimeout(jakarta.ejb.Timer _timer) throws BackupException {

        int total = 0;
        int success = 0;
        int errors = 0;
        Date syncPoint = null;

        try {
            fullBackupStatusHandler.setTimer(_timer);
            syncPoint = fullBackupStatusHandler.getSyncPoint();
            if (syncPoint == null) {
                syncPoint = new Date(0l); // earliest date
            }

            // init rest clients....
            DocumentClient documentClient = restClientHelper.createDocumentClient();
            EventLogClient eventLogClient = restClientHelper.createEventLogClient(documentClient);
            if (documentClient == null || eventLogClient == null) {
                logController.warning(BackupService.TOPIC_FULLBACKUP,
                        "│   ├── Unable to connect to workflow instance endpoint - please verify configuration!");
                try {
                    stopScheduler(FullBackupStatusHandler.STATUS_CANCELED);
                } catch (BackupException e) {
                }
                return;
            }

            fullBackupStatusHandler.setStatus(FullBackupStatusHandler.STATUS_RUNNING);

            logController.info(BackupService.TOPIC_FULLBACKUP,
                    "│   ├── partial backup started from " + syncPoint + "...");

            // find the oldest workitem to start with...
            documentClient.setPageSize(100);
            documentClient.setPageIndex(0);

            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = formatter.format(syncPoint);

            String jqpl = "SELECT document FROM Document AS document WHERE document.created > {ts '" + timestamp
                    + "'} ORDER BY document.created ASC";
            documentClient.setItems("type,$created,$uniqueid,$snapshotid");
            List<ItemCollection> result = documentClient.queryDocuments(jqpl);
            logger.fine("Query: " + jqpl);
            logger.fine("Found " + result.size() + " itemCollections");

            if (result.size() == 0 || result.size() == 1) { // skip last entry
                logController.info(BackupService.TOPIC_FULLBACKUP, "├── FullBackup completed no more data found");
                stopScheduler(FullBackupStatusHandler.STATUS_STOPPED);
                return;
            }
            FTPSClient ftpClient = null;
            try {
                ftpClient = ftpConnector.getFTPClient();
                for (ItemCollection data : result) {
                    Date created = data.getItemValueDate(WorkflowKernel.CREATED);
                    String snapshotID = data.getItemValueString("$snapshotid");
                    if (!snapshotID.isEmpty()) {
                        logger.fine("...create backup request for snapshot id=" + snapshotID);
                        total++;
                        ItemCollection options = new ItemCollection();
                        options.setItemValue("NO_OVERWRITE", true);
                        eventLogClient.createEventLogEntry(getEventLogTopic(), snapshotID, options);
                    }
                    syncPoint = created;

                }
                fullBackupStatusHandler.setSyncPoint(syncPoint);
                logController.info(BackupService.TOPIC_FULLBACKUP, "│   ├── partial backup completed - " + total
                        + " backup requests created, next SyncPoint=" + syncPoint);
            } finally {
                // close writer...
                try {
                    if (ftpClient != null) {
                        ftpClient.logout();
                        ftpClient.disconnect();
                    }
                } catch (Exception e) {

                }
            }

        } catch (InvalidAccessException | EJBException | IOException | RestAPIException e) {
            // we also catch EJBExceptions here because we do not want to cancel the
            // ManagedScheduledExecutorService
            logController.warning(BackupService.TOPIC_FULLBACKUP, "FullBackup failed: " + e.getMessage());
            try {
                stopScheduler(FullBackupStatusHandler.STATUS_CANCELED);

            } catch (BackupException e1) {

            }
        } finally {

        }
    }

    /**
     * This helper method returns the backup topic respecting the optional MirrorID
     *
     * @return
     */
    public String getEventLogTopic() {
        String result = EVENTLOG_TOPIC_FULLBACKUP;
        String mirrorID = backupMirrorId.orElse("");
        if (!mirrorID.isBlank()) {
            result = result + "." + mirrorID;
        }
        return result;
    }

    /**
     * This method initializes the scheduler.
     * <p>
     * The method also verifies the existence of the archive keyspace by loading the
     * archive session object.
     *
     * @throws BackupException
     */
    public void startScheduler() throws BackupException {
        try {
            restClientHelper.reset();
            logController.reset(BackupService.TOPIC_FULLBACKUP);
            logController.info(BackupService.TOPIC_FULLBACKUP,
                    "├── Starting FullBackup scheduler - initalDelay=0ms  inverval=" + interval + "ms ....");
            // start archive schedulers....
            // Registering a non-persistent Timer Service.
            final TimerConfig timerConfig = new TimerConfig();
            timerConfig.setInfo(""); // empty info string indicates no JSESSIONID!
            timerConfig.setPersistent(false);
            Timer timer = timerService.createIntervalTimer(0, interval, timerConfig);
            fullBackupStatusHandler.setTimer(timer);
            fullBackupStatusHandler.setSyncPoint(null);
            fullBackupStatusHandler.setStatus(FullBackupStatusHandler.STATUS_RUNNING);
        } catch (IllegalArgumentException | IllegalStateException | EJBException e) {
            throw new BackupException("TIMER_EXCEPTION", "Failed to init scheduler ", e);

        }

    }

    /**
     * This method stops the scheduler.
     *
     *
     * @throws BackupException
     */
    public boolean stopScheduler(String status) throws BackupException {
        Timer timer = fullBackupStatusHandler.getTimer();
        if (timer != null) {
            try {
                logController.info(BackupService.TOPIC_FULLBACKUP, "└── Stopping the FullBackup scheduler...");
                timer.cancel();
                fullBackupStatusHandler.setStatus(status);
            } catch (IllegalArgumentException | IllegalStateException | EJBException e) {
                throw new BackupException("TIMER_EXCEPTION", "Failed to stop scheduler ", e);
            }
            // update status message
            logController.info(BackupService.TOPIC_FULLBACKUP, "Timer stopped. ");
        }
        return true;
    }

}
