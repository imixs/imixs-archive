package org.imixs.archive.service.rest;

import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.workflow.services.rest.BasicAuthenticator;
import org.imixs.workflow.services.rest.FormAuthenticator;
import org.imixs.workflow.services.rest.RestAPIException;
import org.imixs.workflow.services.rest.RestClient;
import org.imixs.workflow.xml.XMLDataCollection;

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
@Path("/sync")
@Stateless
public class SyncService {
	
	private static String SNAPSHOT_RESOURCE="snapshot/syncpoint/";
	
	private static Logger logger = Logger.getLogger(SyncService.class.getName());

		
	/**
	 * Ping test
	 * 
	 * @return time
	 * @throws Exception
	 */
	@GET
	@Path("/")
	public String ping() {
		logger.finest("......Ping....");
		java.time.LocalDate localDate = java.time.LocalDate.now();
		return "Ping: " + localDate;
	}
	
	
	
	/**
	 * This method read sync data. The method returns the first workitem from the
	 * given syncpoint. If no data is available the method returns null.
	 * 
	 * 
	 * @return an XMLDataCollection instance representing the data to sync or null if no
	 *         data form the given syncpoint is available.
	 * @throws ArchiveException 
	 * 
	 */
	public XMLDataCollection readSyncData(long syncPoint ) throws ArchiveException {
		XMLDataCollection result = null;
		// load next document
	
		RestClient workflowClient =initWorkflowClient();
		String url = SNAPSHOT_RESOURCE  + syncPoint;
		logger.info("...... read data: " + url + "....");

		try {			
			result = workflowClient.getXMLDataCollection(url);
		} catch (RestAPIException e) {
			String errorMessage="Failed to readSyncData : " + e.getMessage();
			logger.warning("..."+errorMessage);
			throw new ArchiveException(ArchiveException.SYNC_ERROR, errorMessage,e);
		}

		if (result != null && result.getDocument().length > 0) {
			return result;
		}
		return null;
	}
	
	 

	/**
	 * Helper method to initalize a Melman Workflow Client based on the current
	 * archive configuration.
	 */
	private RestClient initWorkflowClient() {
		String url = ClusterService.getEnv(ClusterService.WORKFLOW_SERVICE_ENDPOINT, null);
		String autMethod = ClusterService.getEnv(ClusterService.WORKFLOW_SERVICE_AUTHMETHOD, null);
		String user = ClusterService.getEnv(ClusterService.WORKFLOW_SERVICE_USER, null);
		String password = ClusterService.getEnv(ClusterService.WORKFLOW_SERVICE_PASSWORD, null);
				
		logger.info("...... WORKFLOW_SERVICE_ENDPOINT = " + url);
		
		RestClient workflowClient = new RestClient(url);


		// Test authentication method
		if ("Form".equalsIgnoreCase(autMethod)) {
			// default basic authenticator
			FormAuthenticator formAuth = new FormAuthenticator(url,user,password);
			// register the authenticator
			workflowClient.registerRequestFilter(formAuth);

		} else {
			// default basic authenticator
			BasicAuthenticator basicAuth = new BasicAuthenticator(user,password);
			// register the authenticator
			workflowClient.registerRequestFilter(basicAuth);
		}
		return workflowClient;
	}
}