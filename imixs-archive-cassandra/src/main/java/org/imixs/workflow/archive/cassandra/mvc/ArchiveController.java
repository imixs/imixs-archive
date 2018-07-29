package org.imixs.workflow.archive.cassandra.controllers;

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
import org.imixs.workflow.archive.cassandra.ArchiveDataController;
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

		logger.info("create archive config...");

		return "archive_config.xhtml";
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
			@FormParam("pollingInterval") String pollingInterval) {

		// create ItemCollection with archive data
		ItemCollection archive = new ItemCollection();
		archive.replaceItemValue("keyspace", keyspace);
		archive.replaceItemValue("url", url);
		
		if (pollingInterval== null || pollingInterval.isEmpty() ) {
			pollingInterval="hour=*"; // defaut setting
		}
		archive.replaceItemValue("pollingInterval", pollingInterval);
		
		logger.info("update configuration for keyspace '" + keyspace + "' ....");
		try {
			// save the archive configuration
			clusterService.saveConfiguration(archive);
		} catch (ImixsArchiveException e) {
			logger.severe(e.getMessage());
			archiveDataController.setErrorMessage(e.getMessage());
			return "archive_config.xhtml";
		}
		return "redirect:archive";
	}

}