package org.imixs.archive.service;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.Asynchronous;
import javax.ejb.EJBException;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.melman.DocumentClient;
import org.imixs.melman.EventLogClient;
import org.imixs.melman.RestAPIException;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.InvalidAccessException;

/**
 * The SyncService pulls a Snapshot into the cassandra archive. The service uses
 * an asynchronous mechanism based on the Imixs EventLog.
 * <p>
 * The service connects to an Imixs-Workflow instance by the Rest Client to read
 * new snapshot data.
 * <p>
 * The service is triggered by the SyncScheduler implementing a
 * ManagedScheduledExecutorService.
 * 
 * @version 2.0
 * @author ralph.soika@imixs.com
 */
@Stateless
@LocalBean
public class SyncService {

    @Inject
    @ConfigProperty(name = ImixsArchiveApp.WORKFLOW_SERVICE_ENDPOINT, defaultValue = "")
    String workflowServiceEndpoint;

    @Inject
    @ConfigProperty(name = ImixsArchiveApp.WORKFLOW_SERVICE_USER, defaultValue = "")
    String workflowServiceUser;

    @Inject
    @ConfigProperty(name = ImixsArchiveApp.WORKFLOW_SERVICE_PASSWORD, defaultValue = "")
    String workflowServicePassword;

    @Inject
    @ConfigProperty(name = ImixsArchiveApp.WORKFLOW_SERVICE_AUTHMETHOD, defaultValue = "")
    String workflowServiceAuthMethod;

    // deadlock timeout interval in ms
    @Inject
    @ConfigProperty(name = ImixsArchiveApp.WORKFLOW_SYNC_DEADLOCK, defaultValue = "60000")
    long deadLockInterval;

    @Inject
    DataService dataService;

    private static Logger logger = Logger.getLogger(SyncService.class.getName());

    /**
     * This method is called by the ManagedScheduledExecutorService. The method
     * lookups the event log entries and pushes new snapshots into the archive
     * service.
     * <p>
     * Each eventLogEntry is locked to guaranty exclusive processing.
     * 
     * @throws RestAPIException
     **/
    public void processEventLog(EventLogClient eventLogClient, DocumentClient documentClient) throws RestAPIException {
        String topic = null;
        String id = null;
        String ref = null;


        if ( documentClient == null || eventLogClient == null) {
            // no client object
            logger.fine("...no eventLogClient available!");
            return;
        }
        
        // max 100 entries per iteration
        eventLogClient.setPageSize(100);
        List<ItemCollection> events = eventLogClient.searchEventLog(ImixsArchiveApp.EVENTLOG_TOPIC_ADD,
                ImixsArchiveApp.EVENTLOG_TOPIC_REMOVE);

        for (ItemCollection eventLogEntry : events) {
            topic = eventLogEntry.getItemValueString("topic");
            id = eventLogEntry.getItemValueString("id");
            ref = eventLogEntry.getItemValueString("ref");
            try {
                // first try to lock the eventLog entry....
                eventLogClient.lockEventLogEntry(id);
                // eventLogService.lock(eventLogEntry);

                // pull the snapshotEvent only if not just qeued...
                if (topic.startsWith(ImixsArchiveApp.EVENTLOG_TOPIC_ADD)) {
                    logger.finest("......pull snapshot " + ref + "....");
                    // eventCache.add(eventLogEntry);
                    pullSnapshot(eventLogEntry, documentClient, eventLogClient);

                }

                if (topic.startsWith(ImixsArchiveApp.EVENTLOG_TOPIC_REMOVE)) {
                    logger.info("Remove Snapshot not yet implemented");
                }
                // finally remove the event log entry...
                eventLogClient.deleteEventLogEntry(id);
                // eventLogService.removeEvent(eventLogEntry);
            } catch (InvalidAccessException | EJBException | ArchiveException e) {
                // we also catch EJBExceptions here because we do not want to cancel the
                // ManagedScheduledExecutorService
                logger.severe("SnapshotEvent " + id + " pull failed: " + e.getMessage());
                // now we need to remove the batch event
                logger.warning("SnapshotEvent " + id + " will be removed!");
                eventLogClient.deleteEventLogEntry(id);
                // eventLogService.removeEvent(eventLogEntry.getId());
            }
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
    @Asynchronous
    @TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
    public void releaseDeadLocks(EventLogClient eventLogClient) throws RestAPIException {

        if (eventLogClient == null) {
            // no client object
            logger.fine("...no eventLogClient available!");
            return;
        }
        eventLogClient.releaseDeadLocks(deadLockInterval, ImixsArchiveApp.EVENTLOG_TOPIC_ADD,
                    ImixsArchiveApp.EVENTLOG_TOPIC_REMOVE);

      
    }

    /**
     * This method lookups the event log entries and pushes new snapshots into the
     * archive service.
     * <p>
     * The method returns a AsyncResult to indicate the completion of the push. A
     * client can use this information for further control.
     * 
     * @throws ArchiveException
     * @throws RestAPIException
     */
    public void pullSnapshot(ItemCollection eventLogEntry, DocumentClient documentClient, EventLogClient eventLogClient)
            throws ArchiveException {

        if (eventLogEntry == null || documentClient == null || eventLogClient == null) {
            // no client object
            logger.fine("...no eventLogClient available!");
            return;
        }

        boolean debug = logger.isLoggable(Level.FINE);
        String ref = eventLogEntry.getItemValueString("ref");
        String id = eventLogEntry.getItemValueString("id");
        logger.finest("...push " + ref + "...");
        long l = System.currentTimeMillis();
        // lookup the snapshot...
        ItemCollection snapshot;
        try {
            snapshot = documentClient.getDocument(ref);

            if (snapshot != null) {
                logger.finest("...write snapshot...");
                dataService.saveSnapshot(snapshot);

                // TODO - we should now delete the snapshot! This will decrease the storage
                // on the database. But is this bullet proved....?
                if (debug) {
                    logger.fine("...pulled " + ref + " in " + (System.currentTimeMillis() - l) + "ms");
                }
            }
        } catch (RestAPIException e) {
            logger.severe("Snapshot " + ref + " pull failed: " + e.getMessage());
            // now we need to remove the batch event
            logger.warning("EventLogEntry " + id + " will be removed!");
            try {
                eventLogClient.deleteEventLogEntry(id);
            } catch (RestAPIException e1) {
                throw new ArchiveException("REMOTE_EXCEPTION", "Unable to delte eventLogEntry: " + id, e1);
            }
        }
    }

}
