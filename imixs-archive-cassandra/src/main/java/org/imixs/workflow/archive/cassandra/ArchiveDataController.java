package org.imixs.workflow.archive.cassandra;

import java.io.Serializable;
import java.util.List;

import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.inject.Named;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.archive.cassandra.services.SchedulerService;

/**
 * Request Scoped CID Bean to hold the config-data for a single archive.
 * 
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class ArchiveDataController implements Serializable {

	private static final long serialVersionUID = 1L;

	@EJB
	SchedulerService schedulerService;

	String errorMessage = null;

	List<ItemCollection> configurations = null;
	ItemCollection configuration = null;
	long timeRemaining;

	public ArchiveDataController() {
		super();
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
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
		
		// refresh timeouts...
		for (ItemCollection config: this.configurations) {
			// test if the timer is active...
			timeRemaining = schedulerService.getTimeRemaining(config.getItemValueString("keyspace"));
			config.replaceItemValue("timeRemaining", timeRemaining);
		}
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

		// test if the timer is active...
		timeRemaining = schedulerService.getTimeRemaining(configuration.getItemValueString("keyspace"));

		configuration.replaceItemValue("timeRemaining", timeRemaining);
	}

}