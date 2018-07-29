package org.imixs.workflow.archive.cassandra.mvc;

import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.mvc.annotation.Controller;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.archive.cassandra.ImixsArchiveApp;
import org.imixs.workflow.archive.cassandra.data.ArchiveDataController;
import org.imixs.workflow.archive.cassandra.data.ErrorController;
import org.imixs.workflow.archive.cassandra.services.ClusterService;
import org.imixs.workflow.archive.cassandra.services.ImixsArchiveException;

/**
 * The ArchiveController is used to create, show and update archive
 * configurations.
 * 
 * @author rsoika
 *
 */
@Controller
@Path("archive")
public class ArchiveController {
	private static Logger logger = Logger.getLogger(ArchiveController.class.getName());

	@EJB
	ClusterService clusterService;

	@Inject
	ArchiveDataController archiveDataController;
	
	@Inject
	ErrorController errorController;

	/**
	 * show connections
	 * 
	 * @return
	 */
	@Path("/")
	@GET
	public String showConfigs() {

		logger.finest("......show config...");

		archiveDataController.setConfigurations(clusterService.getConfigurationList());

		return "archive_list.xhtml";
	}

	/**
	 * Edit key-space
	 * 
	 * @return
	 */
	@Path("/action/edit/{keyspace}")
	@GET
	public String editKeySpace(@PathParam("keyspace") String keyspace) {

		errorController.reset();
		logger.info("edit archive config '" + keyspace + "'...");
		archiveDataController.setConfiguration(clusterService.getConfigurationByName(keyspace));

		return "archive_config.xhtml";
	}

	/**
	 * Create a new key-space
	 * 
	 * @return
	 */
	@Path("/action/create")
	@GET
	public String createKeySpace() {
		errorController.reset();
		logger.info("create archive config...");

		return "archive_config.xhtml";
	}
	
	
	/**
	 * Deletes a key-space
	 * 
	 * @return
	 * @throws ImixsArchiveException 
	 */
	@Path("/action/delete/{keyspace}")
	@GET
	public String deleteKeySpace(@PathParam("keyspace") String keyspace) throws ImixsArchiveException {

		logger.info("delete archive config '" + keyspace + "'...");
		ItemCollection configuration = clusterService.getConfigurationByName(keyspace);
		String message=clusterService.deleteConfiguration(configuration);
		
		errorController.setMessage(message);

		return "redirect:archive";
	}


	/**
	 * Save the archive configuration. If the corresponding keyspace does not exist,
	 * the method creates and initalizes the keyspace.
	 * 
	 * @param keyspace
	 * @param url
	 * @param session
	 * @param cluster
	 * @return
	 */
	@POST
	@Path("/")
	public String saveArchiveKeySpace(@FormParam("keyspace") String keyspace, @FormParam("url") String url,
			@FormParam("pollingInterval") String pollingInterval, 
			@FormParam("authmethod") String authmethod,
			@FormParam("userid") String userid,
			@FormParam("password") String password) {

		errorController.reset();

		
		// validate keyspace pattern
		if (keyspace.matches("-?\\d+(\\.\\d+)?")) {
			errorController.setMessage("Keyspace can not be a numeric value!");
			return "redirect:archive";
		}
		
		
		// create ItemCollection with archive data
		ItemCollection archive = new ItemCollection();
		archive.replaceItemValue(ImixsArchiveApp.ITEM_KEYSPACE, keyspace);
		archive.replaceItemValue(ImixsArchiveApp.ITEM_URL, url);
		archive.replaceItemValue(ImixsArchiveApp.ITEM_USERID, userid);
		archive.replaceItemValue(ImixsArchiveApp.ITEM_PASSWORD, password);
		archive.replaceItemValue(ImixsArchiveApp.ITEM_AUTHMETHOD, authmethod);
		
		if (pollingInterval== null || pollingInterval.isEmpty() ) {
			pollingInterval="hour=*"; // defaut setting
		}
		archive.replaceItemValue(ImixsArchiveApp.ITEM_POLLINGINTERVAL, pollingInterval);
		
		logger.info("update configuration for keyspace '" + keyspace + "' ....");
		try {
			// save the archive configuration
			clusterService.saveConfiguration(archive);
		} catch (ImixsArchiveException e) {
			logger.severe(e.getMessage());
			errorController.setMessage(e.getMessage());
			return "archive_config.xhtml";
		}
		return "redirect:archive";
	}

}