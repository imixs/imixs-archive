package org.imixs.workflow.archive.cassandra.services;

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
public class SyncService  {

	/**
	 * This method read sync data. The method returns the first workitem from the
	 * given syncpoint. If no data is available the method returns null.
	 * 
	 * 
	 * @return an XMLDocument instance representing the data to sync or null if no
	 *         data form the given syncpoint is available.
	 * 
	 */
	public XMLDocument readSyncData(ItemCollection configuration) {
		// load next document
		long syncPoint = configuration.getItemValueLong(ImixsArchiveApp.ITEM_SYNCPOINT);

		WorkflowClient workflowClient = initWorkflowClient(configuration);
		XMLDataCollection result = workflowClient.getCustomResourceXML("snapshot/sycnpoint/" + syncPoint);

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
		WorkflowClient workflowClient = new WorkflowClient(configuration.getItemValueString(ImixsArchiveApp.ITEM_URL));
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