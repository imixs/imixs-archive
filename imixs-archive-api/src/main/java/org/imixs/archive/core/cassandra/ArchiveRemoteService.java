package org.imixs.archive.core.cassandra;

import java.util.List;
import java.util.logging.Logger;

import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.core.SnapshotService;
import org.imixs.melman.BasicAuthenticator;
import org.imixs.melman.DocumentClient;
import org.imixs.melman.FormAuthenticator;
import org.imixs.melman.RestAPIException;
import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.engine.EventLogService;
import org.imixs.workflow.engine.jpa.EventLog;
import org.imixs.workflow.exceptions.InvalidAccessException;
import org.imixs.workflow.xml.XMLDocumentAdapter;

/**
 * The ArchiveRemoteService pushes a Snapshot into a remote cassandra archive.
 * The service uses an asynchronious mechanism based on the Imixs EventLog. The
 * service also provides a method to load a file from the remote archvie.
 * <p>
 * The service connects to the Imixs-Archive Service by a Rest Client to push
 * and read data.
 * <p>
 * The service is triggered by the ArchivePushService on a scheduled basis.
 * 
 * @version 1.1
 * @author ralph.soika@imixs.com
 */
@Stateless
@LocalBean
public class ArchiveRemoteService {

    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_ENDPOINT, defaultValue = "")
    String archiveServiceEndpoint;

    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_USER, defaultValue = "")
    String archiveServiceUser;

    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_PASSWORD, defaultValue = "")
    String archiveServicePassword;

    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_AUTHMETHOD, defaultValue = "")
    String archiveServiceAuthMethod;

    @EJB
    EventLogService eventLogService;

    @EJB
    DocumentService documentService;

    private static Logger logger = Logger.getLogger(ArchiveRemoteService.class.getName());

    /**
     * Thie method lookups the event log entries and pushes new snapshots into the
     * archvie service.
     * <p>
     * The method returns a AsyncResult to indicate the completion of the push. A
     * client can use this information for further control.
     * 
     * @throws ArchiveException
     */
    @Asynchronous
    @TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
    public void pushSnapshot(EventLog eventLogEntry) {

        if (eventLogEntry == null) {
            return;
        }
        logger.finest("...push " + eventLogEntry.getRef() + "...");
        long l = System.currentTimeMillis();
        // lookup the snapshot...
        ItemCollection snapshot = documentService.load(eventLogEntry.getRef());
        if (snapshot != null) {
            // push the snapshot...
            DocumentClient documentClient = initWorkflowClient();
            String url = archiveServiceEndpoint + "/archive";
            try {
                documentClient.postXMLDocument(url, XMLDocumentAdapter.getDocument(snapshot));

                // remove the event log entry...
                eventLogService.removeEvent(eventLogEntry);

                // TODO - we should now delete the snapshot! This will decrease the storage
                // on the database. But is this bullet proved....?

                logger.fine("...pushed " + eventLogEntry.getRef() + " in " + (System.currentTimeMillis() - l) + "ms");
            } catch (RestAPIException e) {
                logger.severe("...failed to push snapshot: " + snapshot.getUniqueID() + " : " + e.getMessage());
            }
        } else {
            // invalid eventlogentry
            eventLogService.removeEvent(eventLogEntry);
        }
    }

    /**
     * This method loads the file content for a given md5 checksum directly from the
     * cassandra archive using the resource
     * <p>
     * <code>/archive/md5/{md5}</code>
     * <p>
     * To activate this mechansim the environment variable ARCHIVE_SERVICE_ENDPOINT
     * must be set to a valid endpoint.
     * 
     * @param fileData - fileData object providing the MD5 checksum
     * @throws RestAPIException
     */
    public byte[] loadFileFromArchive(FileData fileData) throws RestAPIException {

        if (fileData == null) {
            return null;
        }

        // first we lookup the FileData object

        if (fileData != null) {
            ItemCollection dmsData = new ItemCollection(fileData.getAttributes());
            String md5 = dmsData.getItemValueString(SnapshotService.ITEM_MD5_CHECKSUM);

            if (!md5.isEmpty()) {

                try {
                    DocumentClient documentClient = initWorkflowClient();

                    Client rsClient = documentClient.newClient();

                    String url = archiveServiceEndpoint + "/archive/md5/" + md5;

                    Response reponse = rsClient.target(url).request(MediaType.APPLICATION_OCTET_STREAM).get();

                    // InputStream is = reponse.readEntity(InputStream.class);
                    byte[] fileContent = reponse.readEntity(byte[].class);

                    if (fileContent != null && fileContent.length > 0) {
                        logger.info("md5 daten gefunden");
                        return fileContent;
                    }

                } catch (ProcessingException e) {
                    String message = null;
                    if (e.getCause() != null) {
                        message = e.getCause().getMessage();
                    } else {
                        message = e.getMessage();
                    }
                    throw new RestAPIException(DocumentClient.class.getSimpleName(),
                            RestAPIException.RESPONSE_PROCESSING_EXCEPTION,
                            "error load file by MD5 checksum -> " + message, e);
                }
            } else {
                return null;
            }

        }

        return null;

    }

    /**
     * Helper method to initalize a Melman Workflow Client based on the current
     * archive configuration.
     */
    public DocumentClient initWorkflowClient() {
        logger.finest("...... WORKFLOW_SERVICE_ENDPOINT = " + archiveServiceEndpoint);
        DocumentClient documentClient = new DocumentClient(archiveServiceEndpoint);
        // Test authentication method
        if ("Form".equalsIgnoreCase(archiveServiceAuthMethod)) {
            // default basic authenticator
            FormAuthenticator formAuth = new FormAuthenticator(archiveServiceEndpoint, archiveServiceUser,
                    archiveServicePassword);
            // register the authenticator
            documentClient.registerClientRequestFilter(formAuth);
        } else {
            // default basic authenticator
            BasicAuthenticator basicAuth = new BasicAuthenticator(archiveServiceUser, archiveServicePassword);
            // register the authenticator
            documentClient.registerClientRequestFilter(basicAuth);
        }
        return documentClient;
    }

    /**
     * This method is called by the ManagedScheduledExecutorService. The method
     * lookups the event log entries and pushes new snapshots into the archive
     * service.
     * <p>
     * Each eventLogEntry is locked to guaranty exclusive processing.
     **/
    public void processEventLog() {
        // test for new event log entries...
        List<EventLog> events = eventLogService.findEventsByTopic(100, SnapshotService.EVENTLOG_TOPIC_ADD,
                SnapshotService.EVENTLOG_TOPIC_REMOVE);

        for (EventLog eventLogEntry : events) {

            // first try to lock the eventLog entry....
            eventLogService.lock(eventLogEntry);
            try {
                // push the snapshotEvent only if not just qeued...
                if (SnapshotService.EVENTLOG_TOPIC_ADD.equals(eventLogEntry.getTopic())) {
                    logger.finest("......push snapshot " + eventLogEntry.getRef() + "....");
                    // eventCache.add(eventLogEntry);
                    pushSnapshot(eventLogEntry);
                }

                if (SnapshotService.EVENTLOG_TOPIC_REMOVE.equals(eventLogEntry.getTopic())) {
                    logger.info("Remove Snapshot not yet implemented");
                }
                // finally remove the event log entry...
                eventLogService.removeEvent(eventLogEntry.getId());
            } catch (InvalidAccessException | EJBException e) {
                // we also catch EJBExceptions here because we do not want to cancel the
                // ManagedScheduledExecutorService
                logger.severe("SnapshotEvent " + eventLogEntry.getId() + " push failed: " + e.getMessage());
                // now we need to remove the batch event
                logger.warning("SnapshotEvent " + eventLogEntry.getId() + " will be removed!");
                eventLogService.removeEvent(eventLogEntry.getId());
            }
        }
    }
}
