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
import org.imixs.archive.util.FTPConnector;
import org.imixs.archive.util.LogController;
import org.imixs.archive.util.RestClientHelper;
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

    private Timer timer = null;

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
    ImportStatusHandler importStatusHandler;

    private String status = "stopped";

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
        timer = _timer;
        FTPSClient ftpClient = null;

        // init rest clients....
        DocumentClient documentClient = restClientHelper.getDocumentClient();

        if (documentClient == null) {
            logController.warning(BackupApi.TOPIC_RESTORE,
                    "Unable to connect to workflow instance endpoint - please verify configuration!");
            try {
                stopScheduler();
            } catch (BackupException e) {
            }
            return;
        }

        status = "running";
        try {

            logController.info(BackupApi.TOPIC_RESTORE, "...starting import from " + ftpServer + "...");

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
                                    "......import: " + ftpFileYear.getName() + "/" + ftpWorkingPath + " ...");
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
                                        String query = "$uniqueid:" + snapshot.getUniqueID();
                                        long snapshotCount = documentClient.countDocuments(query);

                                        if (snapshotCount == 0) {
                                            // import!

                                            restoreSnapshot(documentClient, snapshot);
                                            count++;
                                        } else {
                                            // this snapshot already exists in the current workflow instance
                                            skipped++;
                                        }
                                    }
                                }
                                if (importStatusHandler.getStatus() == ImportStatusHandler.STAUS_CANCELED) {
                                    break;
                                }
                            }

                            logController.info(BackupApi.TOPIC_RESTORE,
                                    "......" + ftpFileYear.getName() + "/" + ftpWorkingPath + ": " + verified
                                            + " snapshots verified, " + count + " snapshots imported, " + skipped
                                            + " snapshots allready existed");

                            ftpClient.changeToParentDirectory();
                            if (importStatusHandler.getStatus() == ImportStatusHandler.STAUS_CANCELED) {
                                break;
                            }
                        }
                    }

                }
                if (importStatusHandler.getStatus() == ImportStatusHandler.STAUS_CANCELED) {
                    break;
                }
                ftpClient.changeToParentDirectory();
            }

            logController.info(BackupApi.TOPIC_RESTORE, "... import completed!");

            stopScheduler();

            status = "stopped";
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

    public String getStatus() {
        return status;
    }

    /**
     * Helper Method to restore a snapshot by calling the Rest API from the workflow
     * instance
     *
     * @param documentClient
     * @param snapshot
     * @throws BackupException
     */
    private void restoreSnapshot(DocumentClient documentClient, ItemCollection snapshot) throws BackupException {

        String url = BackupApi.SNAPSHOT_RESOURCE;
        logger.finest("...... post data: " + url + "....");
        try {
            // documentClient.postDocument(url, snapshot);
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

            logController.reset(BackupApi.TOPIC_RESTORE);
            logController.info(BackupApi.TOPIC_RESTORE,
                    "Starting restore scheduler - initalDelay=0ms  inverval=" + interval + "ms ....");
            // start archive schedulers....
            // Registering a non-persistent Timer Service.
            final TimerConfig timerConfig = new TimerConfig();
            timerConfig.setInfo(""); // empty info string indicates no JSESSIONID!
            timerConfig.setPersistent(false);
            timer = timerService.createIntervalTimer(0, interval, timerConfig);
        } catch (IllegalArgumentException | IllegalStateException | EJBException e) {
            throw new BackupException("TIMER_EXCEPTION", "Failed to init scheduler ", e);

        }
        status = "scheduled";

    }

    /**
     * This method stops the scheduler.
     *
     *
     * @throws BackupException
     */
    public boolean stopScheduler() throws BackupException {
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
        status = "stopped";
        return true;
    }

    /**
     * Updates the timer details of a running timer service. The method updates the
     * properties netxtTimeout and store them into the timer configuration.
     *
     *
     * @param configuration - the current scheduler configuration to be updated.
     */
    public Date getNextTimeout() {
        try {
            if (timer != null) {
                // load current timer details
                return timer.getNextTimeout();
            }
        } catch (Exception e) {
            logger.warning("unable to updateTimerDetails: " + e.getMessage());
        }
        return null;
    }

}
