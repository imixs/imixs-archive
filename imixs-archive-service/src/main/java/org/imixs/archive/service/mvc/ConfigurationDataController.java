package org.imixs.archive.service.mvc;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.inject.Named;

import org.imixs.archive.service.ImixsArchiveApp;
import org.imixs.archive.service.scheduler.SchedulerService;
import org.imixs.workflow.ItemCollection;

/**
 * Request Scoped CID Bean to hold the config-data. It provides a list of all available cnfiguations
 * and handles the curren configuraton object for updates. 
 * 
 * @see archive_config.xhtml, archive_list.xhtml
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class ConfigurationDataController implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public static final String KEYSPACE_NAME_PATTERN = "^[A-Z]{2}(?:[ ]?[0-9]){18,20}$";

	@EJB
	SchedulerService schedulerService;

	List<ItemCollection> configurations = null;
	ItemCollection configuration = null;
	//long timeRemaining;
	
	private static Logger logger = Logger.getLogger(ClusterDataController.class.getName());

	

	public ConfigurationDataController() {
		super();
	}

	public List<ItemCollection> getConfigurations() {
		return configurations;
	}

	/**
	 * The method updates the current list of configurations
	 * <p>
	 * The method updates the property 'timeRemaining' which is the number of
	 * milliseconds that will elapse before the next scheduled timer expiration for
	 * each configuration in the list.
	 * 
	 * @return
	 */
	public void setConfigurations(List<ItemCollection> configurations) {
		this.configurations = configurations;
	}

	/**
	 * The method returns the current list of configurations
	 */
	public ItemCollection getConfiguration() {
		if (configuration == null) {
			configuration = new ItemCollection();
		}
		return configuration;
	}

	/**
	 * returns the syncpoint of the current configuration
	 * 
	 * @return
	 */
	public Date getSyncPoint() {
		if (configuration == null) {
			return null;
		}

		long lsyncPoint = configuration.getItemValueLong(ImixsArchiveApp.ITEM_SYNCPOINT);

		Date syncPoint = new Date(lsyncPoint);
		return syncPoint;
	}

	/**
	 * This method sets the current configuraiton and verifies if a timer service is
	 * active for this configuration.
	 * 
	 * The method updates the property 'timeRemaining' which is the number of
	 * milliseconds that will elapse before the next scheduled timer expiration for
	 * this configuration.
	 * 
	 * @param configuration
	 */
	public void setConfiguration(ItemCollection configuration) {
		this.configuration = configuration;

		logger.finest("......update timer details...");
		// test if the timer is active...
		schedulerService.updateTimerDetails(configuration);
	}
	
	
	


}