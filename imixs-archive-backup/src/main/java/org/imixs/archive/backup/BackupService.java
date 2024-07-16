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

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryScope;
import org.imixs.archive.backup.util.FTPConnector;
import org.imixs.archive.backup.util.LogController;
import org.imixs.archive.backup.util.RestClientHelper;
import org.imixs.melman.DocumentClient;
import org.imixs.melman.EventLogClient;
import org.imixs.melman.RestAPIException;
import org.imixs.workflow.ItemCollection;
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
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

/**
 * The BackupService exports the workflow data from a Imixs-Workflow instance
 * into a FTP storage. The service class runs a TimerService based on the given
 * scheduler configuration.
 * <p>
 * The service automatically starts during deployment.
 * <p>
 * The scheduler configuration is based on the chron format. E.g:
 *
 * <code>
 *    hour=*;minute=30;
 * </code>
 *
 * @version 1.0
 * @author rsoika
 */
@Singleton
@Startup
public class BackupService {

    private static Logger logger = Logger.getLogger(BackupService.class.getName());

    @Inject
    LogController logController;

    public static final String METRIC_EVENTS_PROCESSED = "backup_events_processed";
    public static final String METRIC_EVENTS_ERRORS = "backup_events_errors";

    // timeout interval in ms
    @Inject
    @ConfigProperty(name = BackupApi.WORKFLOW_SYNC_INTERVAL, defaultValue = "10000")
    long interval;

    @Inject
    @ConfigProperty(name = BackupApi.WORKFLOW_SYNC_INITIALDELAY, defaultValue = "30000")
    long initialDelay;

    @Inject
    @ConfigProperty(name = BackupApi.WORKFLOW_SERVICE_ENDPOINT)
    Optional<String> workflowServiceEndpoint;

    @Inject
    @ConfigProperty(name = BackupApi.WORKFLOW_SERVICE_AUTHMETHOD)
    Optional<String> workflowServiceAuthMethod;

    @Inject
    @ConfigProperty(name = BackupApi.WORKFLOW_SERVICE_USER)
    Optional<String> workflowServiceUser;

    @Inject
    @ConfigProperty(name = BackupApi.WORKFLOW_SERVICE_PASSWORD)
    Optional<String> workflowServicePassword;

    @Inject
    @ConfigProperty(name = BackupApi.ENV_BACKUP_FTP_HOST)
    Optional<String> ftpServer;

    // deadlock timeout interval in ms
    @Inject
    @ConfigProperty(name = BackupApi.BACKUP_SYNC_DEADLOCK, defaultValue = "60000")
    long deadLockInterval;

    @Resource
    TimerService timerService;

    @Inject
    FTPConnector ftpConnector;

    @Inject
    RestClientHelper restClientHelper;

    @Inject
    BackupStatusHandler backupStatusHandler;

    @Inject
    @RegistryScope(scope = MetricRegistry.APPLICATION_SCOPE)
    MetricRegistry metricRegistry;

    DocumentClient documentClient = null;
    EventLogClient eventLogClient = null;

    @PostConstruct
    public void init() {
        // init timer....
        if (workflowServiceEndpoint.isPresent()) {
            logger.info("init BackupService endpoint: " + workflowServiceEndpoint.get().toString());
            // Registering a non-persistent Timer Service.
            try {
                startScheduler(true);
            } catch (BackupException e) {
                logController.warning(BackupApi.TOPIC_BACKUP, "Failed to init scheduler: " + e.getMessage());
            }
        } else {
            logController.warning(BackupApi.TOPIC_BACKUP,
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
     * @throws RestAPIException
     **/
    @SuppressWarnings("unused")
    @Timeout
    public void onTimeout(jakarta.ejb.Timer _timer) {
        String topic = null;
        String id = null;
        String ref = null;
        int total = 0;
        int success = 0;
        int errors = 0;
        long duration = System.currentTimeMillis();
        try {
            backupStatusHandler.setTimer(_timer);
            logger.info("Processing backup events...");
            // init rest clients....
            DocumentClient documentClient = restClientHelper.createDocumentClient();
            EventLogClient eventLogClient = restClientHelper.createEventLogClient(documentClient);
            if (documentClient == null || eventLogClient == null) {
                logController.warning(BackupApi.TOPIC_BACKUP,
                        "Unable to connect to workflow instance endpoint - please verify configuration!");
                try {
                    stopScheduler();
                } catch (BackupException e) {
                }
                return;
            }

            backupStatusHandler.setStatus(BackupStatusHandler.STATUS_RUNNING);

            logger.finest("......release dead locks....");
            // release dead locks...
            releaseDeadLocks(eventLogClient);

            // max 100 entries per iteration
            eventLogClient.setPageSize(100);
            List<ItemCollection> events = eventLogClient.searchEventLog(BackupApi.EVENTLOG_TOPIC_BACKUP);

            if (events != null && events.size() > 0) {
                logger.info(" -> " + events.size() + " backup events found...");
                for (ItemCollection eventLogEntry : events) {
                    total++;
                    topic = eventLogEntry.getItemValueString("topic");
                    id = eventLogEntry.getItemValueString("id");
                    ref = eventLogEntry.getItemValueString("ref");
                    try {
                        // first try to lock the eventLog entry....
                        eventLogClient.lockEventLogEntry(id);
                        // pull the snapshotEvent ...
                        logger.finest("......pull snapshot " + ref + "....");
                        // eventCache.add(eventLogEntry);
                        ItemCollection snapshot = pullSnapshot(eventLogEntry, documentClient, eventLogClient);
                        if (snapshot != null) {
                            ftpConnector.put(snapshot);
                            // finally remove the event log entry...
                            eventLogClient.deleteEventLogEntry(id);
                            success++;
                        }
                        countMetric(METRIC_EVENTS_PROCESSED);

                    } catch (InvalidAccessException | EJBException | BackupException | RestAPIException e) {
                        // we also catch EJBExceptions here because we do not want to cancel the
                        // ManagedScheduledExecutorService
                        logController.warning(BackupApi.TOPIC_BACKUP, "SnapshotEvent " + id + ": " + e.getMessage());
                        errors++;
                        countMetric(METRIC_EVENTS_ERRORS);

                    }
                }
                // print log
                logController.info(BackupApi.TOPIC_BACKUP, success + " snapshots backed up in "
                        + (System.currentTimeMillis() - duration) + " ms - " + errors + " errors...");

            } else {
                logger.info(" -> no backup events found.");
            }

            backupStatusHandler.setStatus(BackupStatusHandler.STATUS_SCHEDULED);

        } catch (InvalidAccessException | EJBException | RestAPIException e) {
            // In case of a exception during processing the event log
            // the timer service will automatically restarted. This is important
            // to resolve restarts of the workflow engine.
            logController.warning(BackupApi.TOPIC_BACKUP, "processing EventLog failed: " + e.getMessage());
            try {
                restartScheduler();
            } catch (BackupException e1) {
                logController.warning(BackupApi.TOPIC_BACKUP, "Failed to restart backup scheduler: " + e.getMessage());
            }
        }
    }

    /**
     * Helper method to create a counter and inc the coutner
     *
     * @param name
     */
    private void countMetric(String name) {
        try {
            Metadata metadata = Metadata.builder().withName(name)
                    .withDescription("Imixs-Backup Service - processed backup events").build();
            Counter counter = metricRegistry.counter(metadata);
            counter.inc();
        } catch (Exception e) {
            logger.severe("Unable to create metrics for ' " + name + "'");
        }
    }

    /**
     * Asynchronous method to release dead locks
     *
     * @param eventLogClient
     * @param deadLockInterval
     * @param topic
     * @throws RestAPIException
     */
    @TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
    public void releaseDeadLocks(EventLogClient eventLogClient) throws RestAPIException {
        if (eventLogClient == null) {
            // no client object
            logger.fine("...no eventLogClient available!");
            return;
        }
        eventLogClient.releaseDeadLocks(deadLockInterval, BackupApi.EVENTLOG_TOPIC_BACKUP);
    }

    /**
     * This method loads a snapshot from the workflow instance.
     * <p>
     * The method returns null if the snapshot no longer exists. In this case the
     * method automatically deletes the outdated event log entry.
     *
     * @throws BackupException
     */
    public ItemCollection pullSnapshot(ItemCollection eventLogEntry, DocumentClient documentClient,
            EventLogClient eventLogClient) throws BackupException {
        String ref = null;
        String id = null;
        try {
            if (eventLogEntry == null || documentClient == null || eventLogClient == null) {
                // no client object
                logger.fine("...no eventLogClient available!");
                return null;
            }

            ref = eventLogEntry.getItemValueString("ref");
            id = eventLogEntry.getItemValueString("id");
            logger.fine("......pullSnapshot ref " + ref + "...");
            // lookup the snapshot...
            ItemCollection snapshot;

            snapshot = documentClient.getDocument(ref);
            if (snapshot != null) {
                logger.fine("...write snapshot into backup store...");
                return snapshot;
            }
        } catch (RestAPIException e) {
            logController.warning(BackupApi.TOPIC_BACKUP, "Snapshot " + ref + " pull failed: " + e.getMessage());
            // now we need to remove the batch event
            logController.warning(BackupApi.TOPIC_BACKUP, "EventLogEntry " + id + " will be removed!");
            try {
                eventLogClient.deleteEventLogEntry(id);
            } catch (RestAPIException e1) {
                throw new BackupException("REMOTE_EXCEPTION", "Unable to delete eventLogEntry: " + id, e1);
            }

        } catch (RuntimeException e) {
            // can occur in rare cases on the ejb container
            throw new BackupException("REMOTE_EXCEPTION", "Failed to pull Snapshot " + ref + " -> " + e.getMessage(),
                    e);
        }
        return null;
    }

    /**
     * Stops and restarts the timer. The log will be prevented.
     *
     * @throws BackupException
     */
    public void restartScheduler() throws BackupException {
        stopScheduler();
        startScheduler(false);
    }

    /**
     * This method initializes the scheduler.
     * <p>
     * The method also verifies the existence of the archive keyspace by loading the
     * archive session object.
     *
     * @param clearLog - if true, the current log will be reset
     * @throws BackupException
     */
    public void startScheduler(boolean clearLog) throws BackupException {
        try {
            restClientHelper.reset();
            if (clearLog) {
                // clear log in case of a normal start
                logController.reset(BackupApi.TOPIC_BACKUP);
            }
            logController.info(BackupApi.TOPIC_BACKUP,
                    "Starting backup scheduler - initalDelay=" + initialDelay + "ms  inverval=" + interval + "ms ....");
            // start archive schedulers....
            // Registering a non-persistent Timer Service.
            final TimerConfig timerConfig = new TimerConfig();
            timerConfig.setInfo(""); // empty info string indicates no JSESSIONID!
            timerConfig.setPersistent(false);
            Timer timer = timerService.createIntervalTimer(initialDelay, interval, timerConfig);
            backupStatusHandler.setTimer(timer);
        } catch (IllegalArgumentException | IllegalStateException | EJBException e) {
            throw new BackupException("TIMER_EXCEPTION", "Failed to init scheduler ", e);
        }
        backupStatusHandler.setStatus(BackupStatusHandler.STATUS_SCHEDULED);
    }

    /**
     * This method stops the scheduler.
     *
     *
     * @throws BackupException
     */
    public boolean stopScheduler() throws BackupException {
        Timer timer = backupStatusHandler.getTimer();
        if (timer != null) {
            try {
                logController.info(BackupApi.TOPIC_BACKUP, "Stopping the backup scheduler...");
                timer.cancel();
            } catch (IllegalArgumentException | IllegalStateException | EJBException e) {
                throw new BackupException("TIMER_EXCEPTION", "Failed to stop scheduler ", e);
            }
            // update status message
            logController.info(BackupApi.TOPIC_BACKUP, "Timer stopped. ");
        }
        backupStatusHandler.setStatus(BackupStatusHandler.STATUS_STOPPED);
        return true;
    }

}
