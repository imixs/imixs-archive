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

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.melman.BasicAuthenticator;
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
import jakarta.ws.rs.client.ClientRequestFilter;

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

    private Timer timer = null;

    // timeout interval in ms
    @Inject
    @ConfigProperty(name = BackupApi.WORKFLOW_SYNC_INTERVAL, defaultValue = "1000")
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
    @ConfigProperty(name = BackupApi.ENV_BACKUP_SCHEDULER_DEFINITION)
    Optional<String> schedulerDefinition;

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

    DocumentClient documentClient = null;
    EventLogClient eventLogClient = null;

    private String status = "stopped";

    @PostConstruct
    public void init() {
        // init timer....
        if (workflowServiceEndpoint.isPresent()) {
            logController.info("Starting Archive SyncScheduler - initalDelay=" + initialDelay + "ms  inverval="
                    + interval + "ms ....");
            // Registering a non-persistent Timer Service.
            try {
                start();
            } catch (BackupException e) {
                logController.warning("Failed to start scheduler: " + e.getMessage());
            }
        }

        // init rest clients....

        // Default behaviro - use a BasicAuthenticator
        BasicAuthenticator basicAuth = new BasicAuthenticator(workflowServiceUser.orElse(""),
                workflowServicePassword.orElse(""));
        ClientRequestFilter authenticator = basicAuth;

        // do we have a valid authentication?

        // yes - create the client objects
        documentClient = new DocumentClient(workflowServiceEndpoint.orElse(""));
        documentClient.registerClientRequestFilter(authenticator);
        eventLogClient = new EventLogClient(workflowServiceEndpoint.orElse(""));
        eventLogClient.registerClientRequestFilter(authenticator);

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
    public void processEventLog(jakarta.ejb.Timer timer) throws RestAPIException {
        String topic = null;
        String id = null;
        String ref = null;

        if (documentClient == null || eventLogClient == null) {
            // no client object
            logger.fine("...no eventLogClient available!");
            return;
        }
        status = "running";
        // max 100 entries per iteration
        eventLogClient.setPageSize(100);
        List<ItemCollection> events = eventLogClient.searchEventLog(BackupApi.EVENTLOG_TOPIC_BACKUP);

        for (ItemCollection eventLogEntry : events) {
            topic = eventLogEntry.getItemValueString("topic");
            id = eventLogEntry.getItemValueString("id");
            ref = eventLogEntry.getItemValueString("ref");
            try {
                // first try to lock the eventLog entry....
                eventLogClient.lockEventLogEntry(id);
                // eventLogService.lock(eventLogEntry);

                // pull the snapshotEvent ...

                logger.finest("......pull snapshot " + ref + "....");
                // eventCache.add(eventLogEntry);
                ItemCollection snapshot = pullSnapshot(eventLogEntry, documentClient, eventLogClient);

                ftpConnector.put(snapshot);

                // finally remove the event log entry...
                eventLogClient.deleteEventLogEntry(id);
                // eventLogService.removeEvent(eventLogEntry);
            } catch (InvalidAccessException | EJBException | BackupException e) {
                // we also catch EJBExceptions here because we do not want to cancel the
                // ManagedScheduledExecutorService
                logController.warning("SnapshotEvent " + id + " backup failed: " + e.getMessage());

            }
        }
        status = "scheduled";

    }

    public String getStatus() {
        return status;
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
     * This method lookups the event log entries and pushes new snapshots into the
     * archive service.
     * <p>
     * The method returns a AsyncResult to indicate the completion of the push. A
     * client can use this information for further control.
     *
     * @throws BackupException
     * @throws RestAPIException
     */
    public ItemCollection pullSnapshot(ItemCollection eventLogEntry, DocumentClient documentClient,
            EventLogClient eventLogClient) throws BackupException {

        if (eventLogEntry == null || documentClient == null || eventLogClient == null) {
            // no client object
            logger.fine("...no eventLogClient available!");
            return null;
        }

        String ref = eventLogEntry.getItemValueString("ref");
        String id = eventLogEntry.getItemValueString("id");
        logger.finest("...push " + ref + "...");
        // lookup the snapshot...
        ItemCollection snapshot;
        try {
            snapshot = documentClient.getDocument(ref);
            if (snapshot != null) {
                logger.finest("...write snapshot into backup store...");
                return snapshot;
            }
        } catch (RestAPIException e) {
            logController.warning("Snapshot " + ref + " pull failed: " + e.getMessage());
            // now we need to remove the batch event
            logController.warning("EventLogEntry " + id + " will be removed!");
            try {
                eventLogClient.deleteEventLogEntry(id);
            } catch (RestAPIException e1) {
                throw new BackupException("REMOTE_EXCEPTION", "Unable to delte eventLogEntry: " + id, e1);
            }
        }

        return null;
    }

    /**
     * This method initializes the scheduler.
     * <p>
     * The method also verifies the existence of the archive keyspace by loading the
     * archive session object.
     *
     * @throws BackupException
     */
    public boolean startScheduler() throws BackupException {
        try {
            logController.info("...starting the export scheduler...");
            // start archive schedulers....
            start();
            status = "scheduled";
            return true;

        } catch (Exception e) {
            logController.warning("...Failed to initalize imixsarchive keyspace: " + e.getMessage());
            status = "failed: " + e.getMessage();
            return false;

        }
    }

    /**
     * This method stops the scheduler.
     *
     *
     * @throws BackupException
     */
    public boolean stopScheduler() throws BackupException {
        stop();
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
            logController.warning("unable to updateTimerDetails: " + e.getMessage());
        }
        return null;
    }

    /**
     * Starts a new Timer for the scheduler defined by the Configuration.
     *
     * @param configuration - scheduler configuration
     * @return updated configuration
     * @throws BackupException
     */
    private void start() throws BackupException {

        // Registering a non-persistent Timer Service.
        final TimerConfig timerConfig = new TimerConfig();
        timerConfig.setInfo(""); // empty info string indicates no JSESSIONID!
        timerConfig.setPersistent(false);
        timer = timerService.createIntervalTimer(initialDelay, interval, timerConfig);

    }

    /**
     * Cancels the running timer instance.
     *
     * @throws BackupException
     */
    private void stop() throws BackupException {
        if (timer != null) {
            try {

                timer.cancel();
            } catch (Exception e) {
                logController.warning("Failed to stop timer - " + e.getMessage());
            }
            // update status message
            logController.info("Timer stopped. ");
        }
    }

}
