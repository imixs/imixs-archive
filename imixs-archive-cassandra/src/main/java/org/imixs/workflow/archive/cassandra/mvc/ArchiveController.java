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
import org.imixs.workflow.archive.cassandra.data.ConfigurationDataController;
import org.imixs.workflow.archive.cassandra.data.ErrorController;
import org.imixs.workflow.archive.cassandra.services.ConfigurationService;
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
	ConfigurationService configurationService;

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
	 */
	@Path("/")
	@GET
	public String showConfigs() {

		logger.finest("......show config...");

		configurationDataController.setConfigurations(configurationService.getConfigurationList());

		return "archive_list.xhtml";
	}

	/**
	 * Edit key-space
	 * 
	 * @return
	 */
	@Path("/action/edit/{keyspace}")
	@GET
	public String editKeySpace(@PathParam(ImixsArchiveApp.ITEM_KEYSPACE) String keyspace) {

		errorController.reset();
		logger.finest("......edit archive config '" + keyspace + "'...");
		configurationDataController.setConfiguration(configurationService.loadConfiguration(keyspace));

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
		logger.info("...create archive config...");

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
	public String deleteKeySpace(@PathParam(ImixsArchiveApp.ITEM_KEYSPACE) String keyspace)
			throws ImixsArchiveException {

		logger.info("...delete archive config '" + keyspace + "'...");
		ItemCollection configuration = configurationService.loadConfiguration(keyspace);
		String message = configurationService.deleteConfiguration(configuration);

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
	public String saveArchiveKeySpace(@FormParam(ImixsArchiveApp.ITEM_KEYSPACE) String keyspace,
			@FormParam("url") String url, @FormParam("_scheduler_definition") String schedulerDefinition,
			@FormParam("authmethod") String authmethod, @FormParam("userid") String userid,
			@FormParam("password") String password) {

		errorController.reset();

		// validate keyspace pattern
		if (!keyspace.matches(KEYSPACE_REGEX)) {
			errorController.setMessage("Keyspace may not contain - or contain a numeric value!");
			return "redirect:archive";
		}

		try {
			// create ItemCollection with archive data
			ItemCollection archive = new ItemCollection();
			archive.replaceItemValue(ImixsArchiveApp.ITEM_KEYSPACE, keyspace);
			archive.replaceItemValue(ImixsArchiveApp.ITEM_URL, url);
			archive.replaceItemValue(ImixsArchiveApp.ITEM_USERID, userid);
			archive.replaceItemValue(ImixsArchiveApp.ITEM_PASSWORD, password);
			archive.replaceItemValue(ImixsArchiveApp.ITEM_AUTHMETHOD, authmethod);

			if (schedulerDefinition == null || schedulerDefinition.isEmpty()) {
				schedulerDefinition = "hour=*"; // defaut setting
			}
			archive.replaceItemValue(SchedulerService.ITEM_SCHEDULER_DEFINITION, schedulerDefinition);

			logger.info("update configuration for keyspace '" + keyspace + "' ....");

			// save the archive configuration
			configurationService.saveConfiguration(archive);

			// start scheduler
			schedulerService.start(keyspace);

		} catch (ImixsArchiveException e) {
			logger.severe(e.getMessage());
			errorController.setMessage(e.getMessage());
			return "archive_config.xhtml";
		}
		return "redirect:archive";
	}

}