package org.imixs.archive.service.mvc;

import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.mvc.annotation.Controller;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.scheduler.SchedulerService;

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
	

	/**
	 * show connections
	 * 
	 * @return
	 * @throws ArchiveException 
	 */
	@Path("/")
	@GET
	public String showConfigs() throws ArchiveException {

		logger.finest("......show config...");

	
		return "archive_list.xhtml";
	}



}