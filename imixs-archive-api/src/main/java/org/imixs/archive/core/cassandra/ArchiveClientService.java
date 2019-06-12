package org.imixs.archive.core.cassandra;

import java.util.logging.Logger;

import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
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
import org.imixs.workflow.engine.EventLogEntry;
import org.imixs.workflow.engine.EventLogService;
import org.imixs.workflow.xml.XMLDocumentAdapter;

/**
 * The ArchiveHandler pushes a Snapshot int the cassandra archive. The service
 * uses an asyncronious mechansim based on the Imixs EventLog.
 * <p>
 * The service connects to the Imixs-Archive Service by a Rest Client to push
 * and read data.
 * 
 * @author rsoika
 *
 */
@Stateless
@LocalBean
public class ArchiveClientService {

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

	private static Logger logger = Logger.getLogger(ArchiveClientService.class.getName());

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
	 * This method loads the file content for a given md5 checksum directly from the
	 * cassandra archive using the resource
	 * <p>
	 * <code>/archive/md5/{md5}</code>
	 * <p>
	 * To activate this mechansim the environment variable ARCHIVE_SERVICE_ENDPOINT
	 * must be set to a valid endpoint.
	 * 
	 * @param fileData
	 *            - fileData object providing the MD5 checksum
	 */
	public byte[] loadFileFromArchive(FileData fileData) {

		if (fileData == null) {
			return null;
		}

		// first we lookup the FileData object

		if (fileData != null) {
			ItemCollection dmsData = new ItemCollection(fileData.getAttributes());
			String md5 = dmsData.getItemValueString(SnapshotService.ITEM_MD5_CHECKSUM);

			if (!md5.isEmpty()) {

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
}
