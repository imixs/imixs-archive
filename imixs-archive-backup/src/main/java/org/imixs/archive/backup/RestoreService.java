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

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.backup.util.BackupStore;
import org.imixs.archive.backup.util.BackupStoreFactory;
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
 * The RestoreService imports the workflow data from a backup storage (FTP
 * server or local file system) into a Imixs-Workflow instance.
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
    @ConfigProperty(name = BackupService.ENV_WORKFLOW_SYNC_INTERVAL, defaultValue = "60000")
    long interval;

    @Inject
    @ConfigProperty(name = BackupService.ENV_WORKFLOW_SYNC_INITIALDELAY, defaultValue = "30000")
    long initialDelay;

    @Inject
    @ConfigProperty(name = BackupService.ENV_WORKFLOW_SERVICE_ENDPOINT)
    Optional<String> workflowServiceEndpoint;

    @Resource
    TimerService timerService;

    @Inject
    BackupStoreFactory backupStoreFactory;

    @Inject
    RestClientHelper restClientHelper;

    @Inject
    RestoreStatusHandler restoreStatusHandler;

    @PostConstruct
    public void init() {
        if (!workflowServiceEndpoint.isPresent()) {
            logController.warning(BackupService.TOPIC_RESTORE,
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
        BackupStore backupStore = null;

        // overall statistics across all months
        int totalMonths = 0;
        int totalVerified = 0;
        int totalImported = 0;
        int totalSkipped = 0;

        try {
            restoreStatusHandler.setTimer(_timer);

            // init rest clients....
            DocumentClient documentClient = restClientHelper.createDocumentClient();

            if (documentClient == null) {
                logController.warning(BackupService.TOPIC_RESTORE,
                        "Unable to connect to workflow instance endpoint - please verify configuration!");
                try {
                    stopScheduler();
                } catch (BackupException e) {
                }
                return;
            }

            restoreStatusHandler.setStatus(RestoreStatusHandler.STATUS_RUNNING);

            logController.info(BackupService.TOPIC_RESTORE,
                    "Starting import from " + backupStoreFactory.getStorageDescription() + "...");

            // open the backup storage (FTP or local file system)
            backupStore = backupStoreFactory.openStore();

            // iterate over all years (yyyy) and months (MM) sorted by name
            for (String year : backupStore.listDirectories("")) {
                for (String month : backupStore.listDirectories(year)) {
                    String monthPath = year + "/" + month;

                    // read all files....
                    int count = 0;
                    int verified = 0;
                    int skipped = 0;
                    for (String fileName : backupStore.listFiles(monthPath)) {
                        ItemCollection snapshot = backupStore.get(monthPath + "/" + fileName);
                        if (snapshot != null) {
                            verified++;
                            // verify if this snapshot exists?
                            if (!existSnapshot(documentClient, snapshot)) {
                                // restore data
                                restoreSnapshot(documentClient, snapshot);
                                // force refresh of lucene index!
                                refreshLuceneIndex(documentClient);
                                count++;
                            } else {
                                // this snapshot already exists in the current workflow instance
                                skipped++;
                            }
                        }
                        if (RestoreStatusHandler.STATUS_CANCELED.equals(restoreStatusHandler.getStatus())) {
                            break;
                        }
                    }

                    // update overall statistics
                    totalMonths++;
                    totalVerified += verified;
                    totalImported += count;
                    totalSkipped += skipped;

                    logController.info(BackupService.TOPIC_RESTORE,
                            String.format(" ⇨ [%d] %s: %d verified, %d imported, %d skipped", totalMonths,
                                    monthPath, verified, count, skipped));

                    if (RestoreStatusHandler.STATUS_CANCELED.equals(restoreStatusHandler.getStatus())) {
                        break;
                    }
                }
                if (RestoreStatusHandler.STATUS_CANCELED.equals(restoreStatusHandler.getStatus())) {
                    break;
                }
            }

            logController.info(BackupService.TOPIC_RESTORE,
                    String.format("Restore completed! %d months processed, %d verified, %d imported, %d skipped",
                            totalMonths, totalVerified, totalImported, totalSkipped));

            stopScheduler();

            restoreStatusHandler.setStatus(RestoreStatusHandler.STATUS_STOPPED);

        } catch (InvalidAccessException | EJBException | BackupException | RestAPIException e) {
            // we also catch EJBExceptions here because we do not want to cancel the
            // ManagedScheduledExecutorService
            logController.warning(BackupService.TOPIC_RESTORE,
                    String.format("restore failed after %d months (%d verified, %d imported, %d skipped): %s",
                            totalMonths, totalVerified, totalImported, totalSkipped, e.getMessage()));
            try {
                stopScheduler();
            } catch (BackupException e1) {

            }
        } finally {
            // close the backup storage (e.g. ftp logout)
            if (backupStore != null) {
                backupStore.close();
            }
        }
    }

    /**
     * Helper method to force a refresh of the deprecated lucene index!
     */
    private void refreshLuceneIndex(DocumentClient documentClient) {
        try { // refresh lucene index!
            int oldPageSize = documentClient.getPageSize();
            int oldPageIndex = documentClient.getPageIndex();
            documentClient.setPageIndex(0);
            documentClient.setPageSize(1);
            documentClient.searchDocuments("(type:workitem)");
            // restore settigns
            documentClient.setPageIndex(oldPageIndex);
            documentClient.setPageSize(oldPageSize);
        } catch (RestAPIException | UnsupportedEncodingException e) {
            logger.warning("Failed to refreshLuceneIndex : " + e.getMessage());
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
        } catch (NotFoundException | RestAPIException e) {
            // document not found
            result = null;
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

        String url = BackupService.SNAPSHOT_RESOURCE;
        logger.finest("...... post data: " + url + "....");
        try {
            // mark snapshot to indicate that a new backup should be skipped.
            snapshot.setItemValue(BackupService.ITEM_BACKUPRESTORE, new Date());
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
        logController.reset(BackupService.TOPIC_RESTORE);
        // verify the storage configuration before the restore is started
        backupStoreFactory.validateConfig();
        try {
            restClientHelper.reset();
            logController.info(BackupService.TOPIC_RESTORE,
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
                logController.info(BackupService.TOPIC_RESTORE, "Stopping the restore scheduler...");
                timer.cancel();
            } catch (IllegalArgumentException | IllegalStateException | EJBException e) {
                throw new BackupException("TIMER_EXCEPTION", "Failed to stop scheduler ", e);
            }
            // update status message
            logController.info(BackupService.TOPIC_RESTORE, "Timer stopped. ");
        }
        return true;
    }

}