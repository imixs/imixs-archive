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
package org.imixs.archive.export.services;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.imixs.archive.export.ExportApi;
import org.imixs.archive.export.ExportException;
import org.imixs.archive.export.util.RestClientHelper;
import org.imixs.melman.DocumentClient;
import org.imixs.melman.EventLogClient;
import org.imixs.melman.RestAPIException;
import org.imixs.workflow.FileData;
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
 * The SchedulerService is responsible to run a EJB Timer service and pull
 * Export EventLog entries periodically. The service class runs a non-persistent
 * TimerService based on the given scheduler configuration.
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
public class SchedulerService {

    private static Logger logger = Logger.getLogger(SchedulerService.class.getName());

    // timeout interval in ms
    @Inject
    @ConfigProperty(name = ExportApi.WORKFLOW_SYNC_INTERVAL, defaultValue = "10000")
    long interval;

    @Inject
    @ConfigProperty(name = ExportApi.WORKFLOW_SYNC_INITIALDELAY, defaultValue = "30000")
    long initialDelay;

    @Inject
    @ConfigProperty(name = ExportApi.WORKFLOW_SERVICE_ENDPOINT)
    Optional<String> workflowServiceEndpoint;

    @Inject
    @ConfigProperty(name = ExportApi.WORKFLOW_SERVICE_AUTHMETHOD)
    Optional<String> workflowServiceAuthMethod;

    @Inject
    @ConfigProperty(name = ExportApi.WORKFLOW_SERVICE_USER)
    Optional<String> workflowServiceUser;

    @Inject
    @ConfigProperty(name = ExportApi.WORKFLOW_SERVICE_PASSWORD)
    Optional<String> workflowServicePassword;

    @Inject
    @ConfigProperty(name = ExportApi.EXPORT_FTP_HOST)
    Optional<String> ftpServer;

    @Inject
    @ConfigProperty(name = ExportApi.EXPORT_PATH)
    Optional<String> filePath;

    // deadlock timeout interval in ms
    @Inject
    @ConfigProperty(name = ExportApi.EVENTLOG_DEADLOCK, defaultValue = "60000")
    long deadLockInterval;

    @Resource
    TimerService timerService;

    @Inject
    FileService fileService;

    @Inject
    RestClientHelper restClientHelper;

    @Inject
    ExportStatusHandler exportStatusHandler;

    @Inject
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    MetricRegistry metricRegistry;

    @Inject
    LogService logService;

    @PostConstruct
    public void init() {

        logService.info("Setup...");
        // init timer....
        if (verifyConfiguration()) {
            try {
                // Registering a non-persistent Timer Service.
                startScheduler(true);
            } catch (ExportException e) {
                logService.warning("Failed to init scheduler: " + e.getMessage());
            }
        }
    }

    /**
     * Helper method to verify if the configuration is sufficient
     *
     * @return
     */
    private boolean verifyConfiguration() {
        if (!workflowServiceEndpoint.isPresent()) {
            logService.severe("Missing environment param 'WORKFLOW_SERVICE_ENDPOINT' - please verify configuration!");
            return false;

        }
        if (!ftpServer.isPresent() && !filePath.isPresent()) {
            logService.severe(
                    "Missing environment param 'EXPORT_FTP_HOST or EXPORT_PATH' - please verify configuration!");
            return false;

        }
        return true;
    }

    /**
     * This is the method which processes the timeout event depending on the running
     * timer settings. The method lookups the event log entries and pushes new
     * snapshots into the archive service.
     * <p>
     * Each eventLogEntry is locked to guaranty exclusive processing.
     *
     *
     * The metric generated :
     *
     * executions count: count of method executions processing Time:
     * application_org_imixs_archive_export_ExportService_executions_total event
     * count: error count:
     * application_org_imixs_archive_export_ExportService_errors_total
     *
     *
     * @throws RestAPIException
     **/
    @Counted(name = "executions", description = "Counting the invocations of export service", displayName = "executions")
    @SuppressWarnings("unused")
    @Timeout
    public void onTimeout(jakarta.ejb.Timer _timer) {
        String topic = null;
        String id = null;
        String ref = null;
        int total = 0;
        int success = 0;
        int errors = 0;
        exportStatusHandler.setTimer(_timer);
        try {
            // init rest clients....
            DocumentClient documentClient = restClientHelper.getDocumentClient();
            EventLogClient eventLogClient = restClientHelper.getEventLogClient(documentClient);
            if (documentClient == null || eventLogClient == null) {
                logService.warning("Unable to connect to workflow instance endpoint - please verify configuration!");
                try {
                    stopScheduler();
                } catch (ExportException e) {
                }
                metricRegistry.counter("errors").inc();
                return;
            }

            exportStatusHandler.setStatus(ExportStatusHandler.STATUS_RUNNING);

            logger.finest("......release dead locks....");
            // release dead locks...
            releaseDeadLocks(eventLogClient);

            // max 100 entries per iteration
            eventLogClient.setPageSize(100);
            List<ItemCollection> events = eventLogClient.searchEventLog(ExportApi.EVENTLOG_TOPIC);

            for (ItemCollection eventLogEntry : events) {
                total++;
                topic = eventLogEntry.getItemValueString("topic");
                id = eventLogEntry.getItemValueString("id");
                ref = eventLogEntry.getItemValueString("ref");
                String path = eventLogEntry.getItemValueString("path");

                try {
                    // first try to lock the eventLog entry....
                    eventLogClient.lockEventLogEntry(id);
                    // pull the snapshotEvent ...
                    ItemCollection snapshot = pullSnapshot(eventLogEntry, documentClient, eventLogClient);

                    // iterate over all Files and export it
                    List<FileData> fileDataList = snapshot.getFileData();
                    for (FileData fileData : fileDataList) {
                        fileService.writeFileData(fileData, path);
                        success++;
                    }

                    // finally remove the event log entry...
                    eventLogClient.deleteEventLogEntry(id);

                    metricRegistry.counter("application_org_imixs_archive_export_ExportService_events").inc();
                } catch (InvalidAccessException | EJBException | ExportException | RestAPIException e) {
                    // we also catch EJBExceptions here because we do not want to cancel the
                    // ManagedScheduledExecutorService
                    logService.warning("ExportEvent " + id + " failed: " + e.getMessage());
                    metricRegistry.counter("application_org_imixs_archive_export_ExportService_errors").inc();
                    errors++;
                }
            }

            // print log
            if (total > 0) {
                logService.info(success + " files exported, " + errors + " errors.");
            }

            exportStatusHandler.setStatus(ExportStatusHandler.STATUS_SCHEDULED);

        } catch (InvalidAccessException | EJBException | RestAPIException e) {
            logService.severe("processing EventLog failed: " + e.getMessage());
            metricRegistry.counter("application_org_imixs_archive_export_ExportService_errors").inc();
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
        eventLogClient.releaseDeadLocks(deadLockInterval, ExportApi.EVENTLOG_TOPIC);
    }

    /**
     * This method loads a snapshot from the workflow instance.
     * <p>
     * The method returns null if the snapshot no longer exists. In this case the
     * method automatically deletes the outdated event log entry.
     *
     * @throws ExportException
     */
    public ItemCollection pullSnapshot(ItemCollection eventLogEntry, DocumentClient documentClient,
            EventLogClient eventLogClient) throws ExportException {

        if (eventLogEntry == null || documentClient == null || eventLogClient == null) {
            // no client object
            logger.fine("...no eventLogClient available!");
            return null;
        }

        String ref = eventLogEntry.getItemValueString("ref");
        String id = eventLogEntry.getItemValueString("id");
        logger.finest("......pullSnapshot ref " + ref + "...");
        // lookup the snapshot...
        ItemCollection snapshot;
        try {
            snapshot = documentClient.getDocument(ref);
            if (snapshot != null) {
                logger.finest("...write snapshot into export store...");
                return snapshot;
            }
        } catch (RestAPIException e) {
            logService.warning("Snapshot " + ref + " pull failed: " + e.getMessage());
            // now we need to remove the batch event
            logService.warning("EventLogEntry " + id + " will be removed!");
            try {
                eventLogClient.deleteEventLogEntry(id);
            } catch (RestAPIException e1) {
                throw new ExportException("REMOTE_EXCEPTION", "Unable to delete eventLogEntry: " + id, e1);
            }
        }
        return null;
    }

    /**
     * Stops and restarts the timer. The log will be prevented.
     *
     * @throws ExportException
     */
    public void restartScheduler() throws ExportException {
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
     * @throws ExportException
     */
    public void startScheduler(boolean clearLog) throws ExportException {
        try {
            if (clearLog) {
                // clear log in case of a normal start
                // reset(ExportApi.EVENTLOG_TOPIC_EXPORT);
            }
            logService.info(
                    "Starting export scheduler - initalDelay=" + initialDelay + "ms  inverval=" + interval + "ms ....");
            // start archive schedulers....
            // Registering a non-persistent Timer Service.
            final TimerConfig timerConfig = new TimerConfig();
            timerConfig.setInfo(""); // empty info string indicates no JSESSIONID!
            timerConfig.setPersistent(false);
            Timer timer = timerService.createIntervalTimer(initialDelay, interval, timerConfig);
            exportStatusHandler.setTimer(timer);
        } catch (IllegalArgumentException | IllegalStateException | EJBException e) {
            throw new ExportException("TIMER_EXCEPTION", "Failed to init scheduler ", e);
        }
        exportStatusHandler.setStatus(ExportStatusHandler.STATUS_SCHEDULED);
    }

    /**
     * This method stops the scheduler.
     *
     *
     * @throws ExportException
     */
    public boolean stopScheduler() throws ExportException {
        Timer timer = exportStatusHandler.getTimer();
        if (timer != null) {
            try {
                logService.info("Stopping the export scheduler...");
                timer.cancel();
            } catch (IllegalArgumentException | IllegalStateException | EJBException e) {
                throw new ExportException("TIMER_EXCEPTION", "Failed to stop scheduler ", e);
            }
            // update status message
            logService.info("Timer stopped. ");
        }
        exportStatusHandler.setStatus(ExportStatusHandler.STATUS_STOPPED);
        return true;
    }

}
