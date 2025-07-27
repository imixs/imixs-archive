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
import java.util.Date;
import java.util.Optional;
import java.util.logging.Logger;

import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.backup.util.FTPConnector;
import org.imixs.archive.backup.util.LogController;
import org.imixs.archive.backup.util.RestClientHelper;
import org.imixs.melman.DocumentClient;
import org.imixs.melman.RestAPIException;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.InvalidAccessException;
import org.imixs.workflow.xml.XMLDocumentAdapter;

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
import jakarta.ws.rs.NotFoundException;

/**
 * The RestoreService imports the workflow data from a FTP storage into a
 * Imixs-Workflow instance.
 *
 * The service class runs a TimerService based on the given scheduler
 * configuration.
 * <p>
 * The service automatically stops after all data was successful imported.
 * <p>
 * Note: Data which already exists in the workflow instance will not be
 * overwritten.
 *
 *
 * @version 1.0
 * @author rsoika
 */

@Singleton
@Startup
public class RestoreService {

    private static Logger logger = Logger.getLogger(RestoreService.class.getName());

    @Inject
    LogController logController;

    // timeout interval in ms
    @Inject
    @ConfigProperty(name = BackupApi.WORKFLOW_SYNC_INTERVAL, defaultValue = "60000")
    long interval;

    @Inject
    @ConfigProperty(name = BackupApi.WORKFLOW_SYNC_INITIALDELAY, defaultValue = "30000")
    long initialDelay;

    @Inject
    @ConfigProperty(name = BackupApi.WORKFLOW_SERVICE_ENDPOINT)
    Optional<String> workflowServiceEndpoint;

    @Inject
    @ConfigProperty(name = BackupApi.ENV_BACKUP_FTP_PATH)
    Optional<String> ftpPath;

    @Inject
    @ConfigProperty(name = BackupApi.ENV_BACKUP_FTP_HOST)
    Optional<String> ftpServer;

    @Resource
    TimerService timerService;

    @Inject
    FTPConnector ftpConnector;

    @Inject
    RestClientHelper restClientHelper;

    @Inject
    RestoreStatusHandler restoreStatusHandler;

    @PostConstruct
    public void init() {
        if (!workflowServiceEndpoint.isPresent()) {
            logController.warning(BackupApi.TOPIC_RESTORE,
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
        String topic = null;
        String id = null;
        String ref = null;
        int total = 0;
        int success = 0;
        int errors = 0;
        FTPSClient ftpClient = null;

        try {
            restoreStatusHandler.setTimer(_timer);

            // init rest clients....
            DocumentClient documentClient = restClientHelper.createDocumentClient();

            if (documentClient == null) {
                logController.warning(BackupApi.TOPIC_RESTORE,
                        "Unable to connect to workflow instance endpoint - please verify configuration!");
                try {
                    stopScheduler();
                } catch (BackupException e) {
                }
                return;
            }

            restoreStatusHandler.setStatus(RestoreStatusHandler.STATUS_RUNNING);

            logController.info(BackupApi.TOPIC_RESTORE, "Starting import from " + ftpServer.get() + "...");

            ftpClient = ftpConnector.getFTPClient();

            // verify directories
            String ftpRootPath = ftpPath.orElse("/");
            if (!ftpRootPath.startsWith("/")) {
                ftpRootPath = "/" + ftpRootPath;
            }
            if (!ftpRootPath.endsWith("/")) {
                ftpRootPath = ftpRootPath + "/";
            }

            if (!ftpClient.changeWorkingDirectory(ftpRootPath)) {
                throw new BackupException("REMOTE_EXCEPTION", "FTP file transfer failed: missing workfing directory '"
                        + ftpRootPath + "' : " + ftpClient.getReplyString());
            }

            // check subdirectories.....
            String ftpWorkingPath = "";
            FTPFile[] directoryListYears = ftpClient.listDirectories();

            for (FTPFile ftpFileYear : directoryListYears) {
                if (ftpFileYear.isDirectory()) {
                    ftpWorkingPath = ftpFileYear.getName();
                    if (!ftpClient.changeWorkingDirectory(ftpWorkingPath)) {
                        throw new BackupException("REMOTE_EXCEPTION",
                                "FTP file transfer failed: missing working directory '" + ftpWorkingPath + "' : "
                                        + ftpClient.getReplyString());
                    }
                    // now switch to Month....
                    FTPFile[] directoryListMonths = ftpClient.listDirectories();
                    for (FTPFile ftpFileMonth : directoryListMonths) {
                        if (ftpFileMonth.isDirectory()) {
                            ftpWorkingPath = ftpFileMonth.getName();
                            if (!ftpClient.changeWorkingDirectory(ftpWorkingPath)) {
                                throw new BackupException("REMOTE_EXCEPTION",
                                        "FTP file transfer failed: missing working directory '" + ftpWorkingPath
                                                + "' : " + ftpClient.getReplyString());
                            }

                            logController.info(BackupApi.TOPIC_RESTORE,
                                    " ⇨ import: " + ftpFileYear.getName() + "/" + ftpWorkingPath + " ...");
                            // read all files....
                            int count = 0;
                            int verified = 0;
                            int skipped = 0;
                            FTPFile[] importFiles = ftpClient.listFiles();
                            for (FTPFile importFile : importFiles) {
                                if (importFile.isFile()) {
                                    ItemCollection snapshot = ftpConnector.get(ftpClient, importFile.getName());
                                    if (snapshot != null) {
                                        verified++;
                                        // verify if this snapshot exists?
                                        if (!existSnapshot(documentClient, snapshot)) {
                                            // restore data
                                            restoreSnapshot(documentClient, snapshot);
                                            count++;
                                        } else {
                                            // this snapshot already exists in the current workflow instance
                                            skipped++;
                                        }
                                    }
                                }
                                if (RestoreStatusHandler.STATUS_CANCELED.equals(restoreStatusHandler.getStatus())) {
                                    break;
                                }
                            }

                            logController.info(BackupApi.TOPIC_RESTORE,
                                    " ⇨ " + ftpFileYear.getName() + "/" + ftpWorkingPath + ": " + verified
                                            + " snapshots verified, " + count + " snapshots imported, " + skipped
                                            + " snapshots already existed");

                            ftpClient.changeToParentDirectory();
                            if (RestoreStatusHandler.STATUS_CANCELED.equals(restoreStatusHandler.getStatus())) {
                                break;
                            }
                        }
                    }

                }
                if (RestoreStatusHandler.STATUS_CANCELED.equals(restoreStatusHandler.getStatus())) {
                    break;
                }
                ftpClient.changeToParentDirectory();
            }

            logController.info(BackupApi.TOPIC_RESTORE, "Restore completed!");

            stopScheduler();

            restoreStatusHandler.setStatus(RestoreStatusHandler.STATUS_STOPPED);

        } catch (InvalidAccessException | EJBException | IOException | RestAPIException e) {
            // we also catch EJBExceptions here because we do not want to cancel the
            // ManagedScheduledExecutorService
            logController.warning(BackupApi.TOPIC_RESTORE, "restore failed: " + e.getMessage());
            try {
                stopScheduler();
            } catch (BackupException e1) {

            }
        } finally {
            try {
                // do logout....
                if (ftpClient != null) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException e) {
                throw new BackupException("FTP_ERROR", "FTP file transfer failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * This methods test if a snapshot exists in the current workflow instance. The
     * method returns false if the snapshot was not found.
     * <p>
     * To query the snapshot the method uses the load() method because snapshots are
     * not indexed and can be be queried by a search query.
     *
     * @param documentClient
     * @param snapshot
     * @return
     */
    private boolean existSnapshot(DocumentClient documentClient, ItemCollection snapshot) {
        documentClient.setItems("$uniqueid");
        ItemCollection result = null;
        try {
            result = documentClient.getDocument(snapshot.getUniqueID());
        } catch (NotFoundException e) {
            // document not found
            result = null;
        } catch (RestAPIException e) {
            // should not happen
            logger.warning("Failed do get document via rest api: " + e.getMessage());
        }
        // reset items
        documentClient.setItems(null);
        return (result != null);
    }

    /**
     * Helper Method to restore a snapshot by calling the Rest API from the workflow
     * instance.
     * <p>
     * The method marks the snapshot with the item $backuprestore to indicate that
     * this snapshot need no backup.
     *
     * @param documentClient
     * @param snapshot
     * @throws BackupException
     */
    private void restoreSnapshot(DocumentClient documentClient, ItemCollection snapshot) throws BackupException {

        String url = BackupApi.SNAPSHOT_RESOURCE;
        logger.finest("...... post data: " + url + "....");
        try {
            // mark snapshot to indicate that a new backup should be skipped.
            snapshot.setItemValue(BackupApi.ITEM_BACKUPRESTORE, new Date());
            documentClient.postXMLDocument(url, XMLDocumentAdapter.getDocument(snapshot));
        } catch (RestAPIException e) {
            String errorMessage = "...failed to restoreSnapshot: " + e.getMessage();
            throw new BackupException("RESTOR_ERROR", errorMessage, e);
        }

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
            logController.reset(BackupApi.TOPIC_RESTORE);
            logController.info(BackupApi.TOPIC_RESTORE,
                    "Starting restore scheduler - initalDelay=0ms  inverval=" + interval + "ms ....");
            // start archive schedulers....
            // Registering a non-persistent Timer Service.
            final TimerConfig timerConfig = new TimerConfig();
            timerConfig.setInfo(""); // empty info string indicates no JSESSIONID!
            timerConfig.setPersistent(false);
            Timer timer = timerService.createIntervalTimer(0, interval, timerConfig);
            restoreStatusHandler.setTimer(timer);
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
    public boolean stopScheduler() throws BackupException {
        Timer timer = restoreStatusHandler.getTimer();
        if (timer != null) {
            try {
                logController.info(BackupApi.TOPIC_RESTORE, "Stopping the restore scheduler...");
                timer.cancel();
            } catch (IllegalArgumentException | IllegalStateException | EJBException e) {
                throw new BackupException("TIMER_EXCEPTION", "Failed to stop scheduler ", e);
            }
            // update status message
            logController.info(BackupApi.TOPIC_RESTORE, "Timer stopped. ");
        }
        return true;
    }

}
