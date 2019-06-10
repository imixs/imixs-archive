package org.imixs.archive.core;

import java.util.logging.Logger;

import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.melman.BasicAuthenticator;
import org.imixs.melman.DocumentClient;
import org.imixs.melman.FormAuthenticator;
import org.imixs.melman.RestAPIException;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.engine.EventLogEntry;
import org.imixs.workflow.engine.EventLogService;
import org.imixs.workflow.xml.XMLDocumentAdapter;

/**
 * The ArchiveHandler pushes a Snapshot by a asyncronious method.
 * 
 * @author rsoika
 *
 */
@Stateless
@LocalBean
public class ArchiveHandler {

	@Inject
	@ConfigProperty(name = SnapshotService.ENV_ARCHIVE_SERVICE_ENDPOINT, defaultValue = "")
	String archiveServiceEndpoint;

	@Inject
	@ConfigProperty(name = SnapshotService.ENV_ARCHIVE_SERVICE_USER, defaultValue = "")
	String archiveServiceUser;

	@Inject
	@ConfigProperty(name = SnapshotService.ENV_ARCHIVE_SERVICE_PASSWORD, defaultValue = "")
	String archiveServicePassword;

	@Inject
	@ConfigProperty(name = SnapshotService.ENV_ARCHIVE_SERVICE_AUTHMETHOD, defaultValue = "")
	String archiveServiceAuthMethod;

	@EJB
	EventLogService eventLogService;

	@EJB
	DocumentService documentService;

	private static Logger logger = Logger.getLogger(ArchiveHandler.class.getName());

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
	public void pushSnapshot(EventLogEntry eventLogEntry) {

		if (eventLogEntry == null) {
			return;
		}
		logger.finest("...push " + eventLogEntry.getUniqueID() + "...");
		long l = System.currentTimeMillis();
		// lookup the snapshot...
		ItemCollection snapshot = documentService.load(eventLogEntry.getUniqueID());
		if (snapshot != null) {
			// push the snapshot...
			DocumentClient documentClient = initWorkflowClient();
			String url = archiveServiceEndpoint + "/archive";
			try {
				documentClient.postXMLDocument(url, XMLDocumentAdapter.getDocument(snapshot));

				// remove the event log entry...
				eventLogService.removeEvent(eventLogEntry);

				// TODO - we need now to delete the snapshot!

				logger.info(
						"...pushed " + eventLogEntry.getUniqueID() + " in " + (System.currentTimeMillis() - l) + "ms");
			} catch (RestAPIException e) {
				logger.severe("...failed to push snapshot: " + snapshot.getUniqueID() + " : " + e.getMessage());
			}
		} else {
			// invalid eventlogentry
			eventLogService.removeEvent(eventLogEntry);
		}
	}

	/**
	 * Helper method to initalize a Melman Workflow Client based on the current
	 * archive configuration.
	 */
	private DocumentClient initWorkflowClient() {
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
}
