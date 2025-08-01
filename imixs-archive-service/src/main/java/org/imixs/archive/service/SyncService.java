package org.imixs.archive.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.melman.DocumentClient;
import org.imixs.melman.EventLogClient;
import org.imixs.melman.RestAPIException;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.InvalidAccessException;

import jakarta.ejb.EJBException;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

/**
 * The SyncService pulls a Snapshot into an Apache Cassandra archive. The
 * service uses an asynchronous mechanism based on the Imixs EventLog.
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

    // event log topics
    public static final String EVENTLOG_TOPIC_ADD = "snapshot.add";
    public static final String EVENTLOG_TOPIC_REMOVE = "snapshot.remove";
    public static final String EVENTLOG_TOPIC_BACKUP = "snapshot.backup";
    public static final String ITEM_BACKUPRESTORE = "$backuprestore";

    // rest service endpoint
    public static final String ENV_WORKFLOW_SERVICE_ENDPOINT = "workflow.service.endpoint";
    public static final String ENV_WORKFLOW_SERVICE_USER = "workflow.service.user";
    public static final String ENV_WORKFLOW_SERVICE_PASSWORD = "workflow.service.password";
    public static final String ENV_WORKFLOW_SERVICE_AUTHMETHOD = "workflow.service.authmethod";
    public static final String ENV_WORKFLOW_SYNC_INTERVAL = "workflow.sync.interval";
    public static final String ENV_WORKFLOW_SYNC_INITIALDELAY = "workflow.sync.initialdelay";
    public static final String ENV_WORKFLOW_SYNC_DEADLOCK = "workflow.sync.deadlock";
    public static final String ENV_BACKUP_SERVICE_ENDPOINT = "backup.service.endpoint";
    public static final String ENV_BACKUP_MIRRORS = "backup.mirrors";

    @Inject
    @ConfigProperty(name = ENV_WORKFLOW_SERVICE_ENDPOINT)
    Optional<String> workflowServiceEndpoint;

    @Inject
    @ConfigProperty(name = ENV_WORKFLOW_SERVICE_USER)
    Optional<String> workflowServiceUser;

    @Inject
    @ConfigProperty(name = ENV_WORKFLOW_SERVICE_PASSWORD)
    Optional<String> workflowServicePassword;

    @Inject
    @ConfigProperty(name = ENV_WORKFLOW_SERVICE_AUTHMETHOD)
    Optional<String> workflowServiceAuthMethod;

    // deadlock timeout interval in ms
    @Inject
    @ConfigProperty(name = ENV_WORKFLOW_SYNC_DEADLOCK, defaultValue = "60000")
    long deadLockInterval;

    @Inject
    @ConfigProperty(name = ENV_BACKUP_SERVICE_ENDPOINT)
    Optional<String> backupServiceEndpoint;

    @Inject
    @ConfigProperty(name = ENV_BACKUP_MIRRORS)
    Optional<String> backupMirrors;

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
        ItemCollection snapshot = null;
        long count = 0;
        long duration = System.currentTimeMillis();

        if (documentClient == null || eventLogClient == null) {
            // no client object
            logger.warning("...no eventLogClient available!");
            return;
        }

        // max 100 entries per iteration
        eventLogClient.setPageSize(100);
        List<ItemCollection> events = eventLogClient.searchEventLog(EVENTLOG_TOPIC_ADD,
                EVENTLOG_TOPIC_REMOVE);

        for (ItemCollection eventLogEntry : events) {
            topic = eventLogEntry.getItemValueString("topic");
            id = eventLogEntry.getItemValueString("id");
            ref = eventLogEntry.getItemValueString("ref");
            try {
                // first try to lock the eventLog entry....
                eventLogClient.lockEventLogEntry(id);

                // pull the snapshotEvent only if not just qeued...
                if (topic.startsWith(EVENTLOG_TOPIC_ADD)) {
                    logger.finest("......pull snapshot " + ref + "....");
                    snapshot = pullSnapshot(eventLogEntry, documentClient, eventLogClient);
                }

                if (topic.startsWith(EVENTLOG_TOPIC_REMOVE)) {
                    logger.info("Remove Snapshot not yet implemented");
                }
                // finally remove the event log entry...
                eventLogClient.deleteEventLogEntry(id);

                // finally write a backup event log entry if a BackupService is available...
                if (backupServiceEndpoint.isPresent() && !backupServiceEndpoint.get().isEmpty()) {
                    // we skip this event if the snapshot is from a restore....
                    if (snapshot != null && !snapshot.hasItem(ITEM_BACKUPRESTORE)) {
                        logger.finest("......create event log entry " + EVENTLOG_TOPIC_BACKUP);
                        eventLogClient.createEventLogEntry(EVENTLOG_TOPIC_BACKUP, ref, null);
                        // create additional events for each backup mirror if defined
                        String mirrors = backupMirrors.orElse("");
                        if (!mirrors.isBlank()) {
                            List<String> mirrorList = Arrays.stream(mirrors.split(","))
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .collect(Collectors.toList());
                            for (String mirrorID : mirrorList) {
                                eventLogClient.createEventLogEntry(EVENTLOG_TOPIC_BACKUP + "." + mirrorID, ref, null);
                            }
                        }
                    }
                }
                count++;
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
        if (count > 0) {
            logger.info("Processed " + count + " snapshot events in " + (System.currentTimeMillis() - duration) + "ms");
        } else {
            // only in debug mode
            logger.fine("Processed " + count + " snapshot events in " + (System.currentTimeMillis() - duration) + "ms");
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
            logger.warning("...no eventLogClient available!");
            return;
        }
        eventLogClient.releaseDeadLocks(deadLockInterval, EVENTLOG_TOPIC_ADD,
                EVENTLOG_TOPIC_REMOVE);
    }

    /**
     * This method lookups the event log entries and pushes new snapshots into the
     * archive service.
     * <p>
     * The method returns a AsyncResult to indicate the completion of the push. A
     * client can use this information for further control.
     * <p>
     * The method returns the snapshot ItemCollection
     * 
     * @throws ArchiveException
     * @throws RestAPIException
     */
    public ItemCollection pullSnapshot(ItemCollection eventLogEntry, DocumentClient documentClient,
            EventLogClient eventLogClient) throws ArchiveException {

        if (eventLogEntry == null || documentClient == null || eventLogClient == null) {
            // no client object
            logger.fine("...no eventLogClient available!");
            return null;
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
                return snapshot;
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
        return null;
    }

}
