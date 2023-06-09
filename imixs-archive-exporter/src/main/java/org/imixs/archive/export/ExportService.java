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
package org.imixs.archive.export;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.imixs.archive.util.FTPConnector;
import org.imixs.archive.util.LogController;
import org.imixs.archive.util.RestClientHelper;
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
 * The ExportService exports the workflow data from a Imixs-Workflow instance
 * into a FTP storage. The service class runs a non-persistent TimerService
 * based on the given scheduler configuration.
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
public class ExportService {

    private static Logger logger = Logger.getLogger(ExportService.class.getName());

    @Inject
    LogController logController;

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
    @ConfigProperty(name = ExportApi.ENV_EXPORT_FTP_HOST)
    Optional<String> ftpServer;

    // deadlock timeout interval in ms
    @Inject
    @ConfigProperty(name = ExportApi.EXPORT_SYNC_DEADLOCK, defaultValue = "60000")
    long deadLockInterval;

    @Resource
    TimerService timerService;

    @Inject
    FTPConnector ftpConnector;

    @Inject
    RestClientHelper restClientHelper;

    @Inject
    ExportStatusHandler exportStatusHandler;

    @Inject
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    MetricRegistry metricRegistry;

    @PostConstruct
    public void init() {
        // init timer....
        if (workflowServiceEndpoint.isPresent()) {
            // Registering a non-persistent Timer Service.
            try {
                startScheduler(true);
            } catch (ExportException e) {
                logController.warning(ExportApi.TOPIC_EXPORT, "Failed to init scheduler: " + e.getMessage());
            }
        } else {
            logController.warning(ExportApi.TOPIC_EXPORT,
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

    @Counted(name = "exportServiceInvocations", description = "Counting the invocations of export service", displayName = "exportServiceInvocations")
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
                logController.warning(ExportApi.TOPIC_EXPORT,
                        "Unable to connect to workflow instance endpoint - please verify configuration!");
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
            List<ItemCollection> events = eventLogClient.searchEventLog(ExportApi.EVENTLOG_TOPIC_EXPORT);

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

                    ftpConnector.put(snapshot);

                    // finally remove the event log entry...
                    eventLogClient.deleteEventLogEntry(id);
                    success++;
                } catch (InvalidAccessException | EJBException | ExportException | RestAPIException e) {
                    // we also catch EJBExceptions here because we do not want to cancel the
                    // ManagedScheduledExecutorService
                    logController.warning(ExportApi.TOPIC_EXPORT,
                            "SnapshotEvent " + id + " export failed: " + e.getMessage());
                    errors++;
                }
            }

            // print log
            if (total > 0) {
                logController.info(ExportApi.TOPIC_EXPORT, success + " snapshots export up, " + errors + " errors...");
            }

            exportStatusHandler.setStatus(ExportStatusHandler.STATUS_SCHEDULED);

        } catch (InvalidAccessException | EJBException | RestAPIException e) {
            logController.severe(ExportApi.TOPIC_EXPORT, "processing EventLog failed: " + e.getMessage());
            metricRegistry.counter("org_imixs_archive_export_errors").inc();
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
        eventLogClient.releaseDeadLocks(deadLockInterval, ExportApi.EVENTLOG_TOPIC_EXPORT);
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
            logController.warning(ExportApi.TOPIC_EXPORT, "Snapshot " + ref + " pull failed: " + e.getMessage());
            // now we need to remove the batch event
            logController.warning(ExportApi.TOPIC_EXPORT, "EventLogEntry " + id + " will be removed!");
            try {
                eventLogClient.deleteEventLogEntry(id);
            } catch (RestAPIException e1) {
                throw new ExportException("REMOTE_EXCEPTION", "Unable to delte eventLogEntry: " + id, e1);
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
                logController.reset(ExportApi.TOPIC_EXPORT);
            }
            logController.info(ExportApi.TOPIC_EXPORT,
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
                logController.info(ExportApi.TOPIC_EXPORT, "Stopping the export scheduler...");
                timer.cancel();
            } catch (IllegalArgumentException | IllegalStateException | EJBException e) {
                throw new ExportException("TIMER_EXCEPTION", "Failed to stop scheduler ", e);
            }
            // update status message
            logController.info(ExportApi.TOPIC_EXPORT, "Timer stopped. ");
        }
        exportStatusHandler.setStatus(ExportStatusHandler.STATUS_STOPPED);
        return true;
    }

}
