package org.imixs.workflow.archive.cassandra;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.SessionScoped;
import javax.inject.Named;

import org.imixs.workflow.ItemCollection;

/**
 * Session Scoped CID Bean to hold current data.
 * 
 * @author rsoika
 *
 */
@Named
@SessionScoped
public class ConfigDataController implements Serializable {

	private static final long serialVersionUID = 1L;

	public static int DEFAULT_PAGE_SIZE = 30;



	ItemCollection configuration;
	List<ItemCollection> configurations;

	public ConfigDataController() {
		super();
	}

	public ItemCollection getConfiguration() {
		return configuration;
	}

	public void setConfiguration(ItemCollection configuration) {
		this.configuration = configuration;
	}

	public List<ItemCollection> getConfigurations() {
		if (configurations==null) {
			// create empty list
			configurations=new ArrayList<ItemCollection>();
		}
		return configurations;
	}

	public void setConfigurations(List<ItemCollection> configurations) {
		this.configurations = configurations;
	}


}