package org.imixs.workflow.archive.cassandra;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.enterprise.context.SessionScoped;
import javax.inject.Named;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.archive.cassandra.services.ClusterService;

import com.datastax.driver.core.Session;

/**
 * Session Scoped CID Bean to hold config data.
 * 
 * @author rsoika
 *
 */
@Named
@SessionScoped
public class ConfigDataController implements Serializable {

	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(ConfigDataController.class.getName());

	public static int DEFAULT_PAGE_SIZE = 30;
	public static String PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT = "archive.cluster.contactpoints";
	public static String PROPERTY_ARCHIVE_CLUSTER_KEYSPACE = "archive.cluster.keyspace";

	Properties configurationProperties = null;
	String contactPoints;
	String keySpace;

	ItemCollection configuration;
	List<ItemCollection> configurations;

	boolean connected;

	@EJB
	ClusterService clusterService;

	public ConfigDataController() {
		super();
	}

	/**
	 * This method loads the config entity. If the entity did not yet exist, the
	 * method creates one.
	 * 
	 *
	 */
	@PostConstruct
	public void init() {
		logger.info("Initial setup: reading environment....");

		configurationProperties = new Properties();
		try {
			// load confiugration file 'imixs.properties'
			configurationProperties
					.load(Thread.currentThread().getContextClassLoader().getResource("imixs.properties").openStream());
		} catch (Exception e) {
			logger.warning("LDAPLookupService unable to find imixs.properties in current classpath");
			e.printStackTrace();
		}

		// skip if no configuration
		if (configurationProperties == null) {
			logger.severe("Missing imixs.properties!");
			return;
		}

		// load environment setup..
		contactPoints = configurationProperties.getProperty(PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT);
		keySpace = configurationProperties.getProperty(PROPERTY_ARCHIVE_CLUSTER_KEYSPACE);

		logger.info(PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT + "=" + contactPoints);
		logger.info(PROPERTY_ARCHIVE_CLUSTER_KEYSPACE + "=" + keySpace);

		// no we test the connection

		if (clusterService != null) {

			Session session = clusterService.connect();

			if (session != null) {
				connected= true;
			} else {
				logger.warning("Unable to connect to contact point!");
				connected= false;
			}

		} else {
			logger.warning("Unable to inject ClusterService!");
			connected= false;
		}
		
		
	}

	public ItemCollection getConfiguration() {
		return configuration;
	}

	public void setConfiguration(ItemCollection configuration) {
		this.configuration = configuration;
	}

	public List<ItemCollection> getConfigurations() {
		if (configurations == null) {
			// create empty list
			configurations = new ArrayList<ItemCollection>();
		}
		return configurations;
	}

	public void setConfigurations(List<ItemCollection> configurations) {
		this.configurations = configurations;
	}

	public String getContactPoints() {
		return contactPoints;
	}

	public void setContactPoints(String contactPoints) {
		this.contactPoints = contactPoints;
	}

	public String getKeySpace() {
		return keySpace;
	}

	public void setKeySpace(String keySpace) {
		this.keySpace = keySpace;
	}

	/**
	 * returns true if a connection to the specified keySpace was
	 * successful
	 * 
	 * @return
	 */
	public boolean isConnected() {
		return connected;
	}


}