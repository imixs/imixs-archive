package org.imixs.workflow.archive.cassandra.data;

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

/**
 * Session Scoped CID Bean to hold cluster configuration data.
 * 
 * @author rsoika
 *
 */
@Named
@SessionScoped
public class ClusterDataController implements Serializable {

	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(ClusterDataController.class.getName());

	public static int DEFAULT_PAGE_SIZE = 30;
	public static String PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT = "archive.cluster.contactpoints";
	public static String PROPERTY_ARCHIVE_CLUSTER_KEYSPACE = "archive.cluster.keyspace";

	Properties configurationProperties = null;
	String contactPoints;
	String keySpace;

	int archiveCount;
	long syncCount, errorCount, errorCountSync, errorCountObject;

	ItemCollection configuration;
	List<ItemCollection> configurations;

	boolean connected;

	@EJB
	ClusterService clusterService;

	public ClusterDataController() {
		super();
	}

	/**
	 * This method verifies and initializes the core keyspace
	 * 
	 *
	 */
	@PostConstruct
	public void init() {
		logger.info("Initial setup: reading environment....");

		configurationProperties = new Properties();
		try {
			// load configuration file 'imixs.properties'
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

		refreshConfiguration();

	}

	/**
	 * Updates the configuration list
	 */
	public void refreshConfiguration() {
		List<ItemCollection> archiveList = clusterService.getConfigurationList();

		archiveCount = 0;

		syncCount = 0;

		for (ItemCollection archiveConf : archiveList) {
			archiveCount++;
			syncCount = syncCount + archiveConf.getItemValueLong("_sync_count");
			errorCount = errorCount + archiveConf.getItemValueLong("_error_count");

			errorCountObject = errorCountObject + archiveConf.getItemValueLong("_error_count_object");

			errorCountSync = errorCountSync + archiveConf.getItemValueLong("_error_count_Sync");

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
	 * returns true if a connection to the specified keySpace was successful
	 * 
	 * @return
	 */
	public boolean isConnected() {
		return connected;
	}

	public int getArchiveCount() {
		return archiveCount;
	}

	public void setArchiveCount(int archiveCount) {
		this.archiveCount = archiveCount;
	}

	
	
	
	public long getSyncCount() {
		return syncCount;
	}

	public void setSyncCount(long syncCount) {
		this.syncCount = syncCount;
	}

	public long getErrorCount() {
		return errorCount;
	}

	public void setErrorCount(long errorCount) {
		this.errorCount = errorCount;
	}

	public long getErrorCountSync() {
		return errorCountSync;
	}

	public void setErrorCountSync(long errorCountSync) {
		this.errorCountSync = errorCountSync;
	}

	public long getErrorCountObject() {
		return errorCountObject;
	}

	public void setErrorCountObject(long errorCountObject) {
		this.errorCountObject = errorCountObject;
	}

}