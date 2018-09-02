package org.imixs.workflow.archive.cassandra.services;

import java.util.logging.Logger;

import javax.ejb.Stateless;

import org.imixs.melman.BasicAuthenticator;
import org.imixs.melman.FormAuthenticator;
import org.imixs.melman.WorkflowClient;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.archive.cassandra.ImixsArchiveApp;
import org.imixs.workflow.xml.XMLDataCollection;
import org.imixs.workflow.xml.XMLDocument;

/**
 * The SyncService is used to establish a connection to Imixs-Worklfow remote
 * interface and read data to sync.
 * 
 * The constructor method expects a configuration item holding the necessary
 * information to connect to the remote system. This is: url, user, authMethod,
 * password
 * 
 * @author rsoika
 *
 */
@Stateless
public class SyncService {
	
	private static String SNAPSHOT_RESOURCE="snapshot/syncpoint/";
	
	private static Logger logger = Logger.getLogger(SyncService.class.getName());

	
	/**
	 * This method read sync data. The method returns the first workitem from the
	 * given syncpoint. If no data is available the method returns null.
	 * 
	 * 
	 * @return an XMLDocument instance representing the data to sync or null if no
	 *         data form the given syncpoint is available.
	 * @throws ImixsArchiveException 
	 * 
	 */
	public XMLDocument readSyncData(ItemCollection configuration) throws ImixsArchiveException {
		XMLDataCollection result = null;
		// load next document
		long syncPoint = configuration.getItemValueLong(ImixsArchiveApp.ITEM_SYNCPOINT);

		WorkflowClient workflowClient = initWorkflowClient(configuration);

		String url = SNAPSHOT_RESOURCE  + syncPoint;

		logger.info("...... read data: " + url + "....");

		try {
			result = workflowClient.getCustomResourceXML(url);
		} catch (Exception e) {
			String errorMessage="Failed to connnect '" + url + " : " + e.getMessage();
			logger.warning("..."+errorMessage);
			throw new ImixsArchiveException(ImixsArchiveException.SYNC_ERROR, errorMessage);
		}

		if (result != null && result.getDocument().length > 0) {
			return result.getDocument()[0];
		}
		return null;
	}

	/**
	 * Helper method to initalize a Melman Workflow Client based on the current
	 * archive configuration.
	 */
	private WorkflowClient initWorkflowClient(ItemCollection configuration) {
		String url = configuration.getItemValueString(ImixsArchiveApp.ITEM_URL);
		logger.info("...... init rest client - url = " + url);

		WorkflowClient workflowClient = new WorkflowClient(url);
		// Test authentication method
		if ("Form".equalsIgnoreCase(configuration.getItemValueString(ImixsArchiveApp.ITEM_AUTHMETHOD))) {
			// default basic authenticator
			FormAuthenticator formAuth = new FormAuthenticator(url,
					configuration.getItemValueString(ImixsArchiveApp.ITEM_USERID),
					configuration.getItemValueString(ImixsArchiveApp.ITEM_PASSWORD));
			// register the authenticator
			workflowClient.registerClientRequestFilter(formAuth);

		} else {
			// default basic authenticator
			BasicAuthenticator basicAuth = new BasicAuthenticator(
					configuration.getItemValueString(ImixsArchiveApp.ITEM_USERID),
					configuration.getItemValueString(ImixsArchiveApp.ITEM_PASSWORD));
			// register the authenticator
			workflowClient.registerClientRequestFilter(basicAuth);
		}
		return workflowClient;
	}
}