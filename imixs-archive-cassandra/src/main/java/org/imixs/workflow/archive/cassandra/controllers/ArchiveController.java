package org.imixs.workflow.archive.cassandra.controllers;

import java.util.Date;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.mvc.annotation.Controller;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.archive.cassandra.ArchiveDataController;
import org.imixs.workflow.archive.cassandra.services.ClusterService;
import org.imixs.workflow.archive.cassandra.services.ImixsArchiveException;

import com.datastax.driver.core.Session;

/**
 * The ArchiveController is used to create, show und update archive
 * configurations
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

		logger.info("show config...");

		return "archive_list.xhtml";
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
			@FormParam("pollingInterval") int pollingInterval) {

		// open core keyspace...
		Session coreSession = clusterService.getSession();

		// create ItemCollection with archive data
		ItemCollection archive = new ItemCollection();
		archive.replaceItemValue("$modified", new Date());
		archive.replaceItemValue("keyspace", keyspace);
		archive.replaceItemValue("url", url);
		archive.replaceItemValue("pollingInterval", pollingInterval);

		logger.info("creating table schemas for keyspace '" + keyspace + "' ....");
		Session archiveSession=clusterService.getSession(keyspace);
		
		
		if (archiveSession==null) {
			archiveDataController.setErrorMessage("Unabel to create keyspace");
			return "archive_config.xhtml";
		}
		
		// save the archvie configuraton
		try {
			clusterService.save(archive, coreSession);
		} catch (ImixsArchiveException e) {
			logger.severe(e.getMessage());
			archiveDataController.setErrorMessage(e.getMessage());
			return "archive_config.xhtml";
		}

		// now save the configuration into the core keySpace

		return "redirect:archive";
	}

}