package org.imixs.workflow.archive.cassandra;

import java.io.Serializable;
import java.util.List;

import javax.enterprise.context.RequestScoped;
import javax.inject.Named;

import org.imixs.workflow.ItemCollection;

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

	String errorMessage = null;

	List<ItemCollection> configurations = null;
	ItemCollection configuration = null;

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

	public void setConfigurations(List<ItemCollection> configurations) {
		this.configurations = configurations;
	}

	public ItemCollection getConfiguration() {
		if (configuration == null) {
			configuration = new ItemCollection();
		}
		return configuration;
	}

	public void setConfiguration(ItemCollection configuration) {
		this.configuration = configuration;
	}

}