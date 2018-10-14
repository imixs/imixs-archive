package org.imixs.workflow.archive.cassandra.mvc;

import java.util.Date;
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
import org.imixs.workflow.archive.cassandra.data.ConfigurationDataController;
import org.imixs.workflow.archive.cassandra.data.ErrorController;
import org.imixs.workflow.archive.cassandra.services.MetadataService;
import org.imixs.workflow.archive.cassandra.services.ImixsArchiveException;
import org.imixs.workflow.archive.cassandra.services.SchedulerService;

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

	public static final String KEYSPACE_REGEX = "^[a-z_]*[^-]$";


	@EJB
	SchedulerService schedulerService;

	@Inject
	ConfigurationDataController configurationDataController;

	@Inject
	ErrorController errorController;
	
	@EJB
	MetadataService metadataService;

	/**
	 * show connections
	 * 
	 * @return
	 * @throws ImixsArchiveException 
	 */
	@Path("/")
	@GET
	public String showConfigs() throws ImixsArchiveException {

		logger.finest("......show config...");

	
		return "archive_list.xhtml";
	}

	/**
	 * Edit key-space
	 * 
	 * @return
	 * @throws ImixsArchiveException 
	 */
	@Path("/action/edit/{keyspace}")
	@GET
	public String editMetadata(@PathParam(ImixsArchiveApp.ITEM_KEYSPACE) String keyspace) throws ImixsArchiveException {

		errorController.reset();
		logger.finest("......edit archive config '" + keyspace + "'...");
		configurationDataController.setConfiguration(metadataService.loadMetadata());

		return "archive_config.xhtml";
	}

}