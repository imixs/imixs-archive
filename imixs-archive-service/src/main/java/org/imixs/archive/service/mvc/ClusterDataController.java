package org.imixs.archive.service.mvc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.SessionScoped;
import javax.inject.Named;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.scheduler.SchedulerService;
import org.imixs.workflow.ItemCollection;

/**
 * Session Scoped CID Bean to hold cluster configuration data.
 * 
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class ClusterDataController implements Serializable {

	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(ClusterDataController.class.getName());

	Properties configurationProperties = null;
	String contactPoints;
	String keySpace;
	String scheduler;

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
	 * @throws ArchiveException 
	 * 
	 *
	 */
	@PostConstruct
	public void init() throws ArchiveException {
		logger.info("...initial setup: reading environment....");

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
		contactPoints = clusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_CONTACTPOINTS, null);
		keySpace = clusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_KEYSPACE, null);
		scheduler=clusterService.getEnv(ClusterService.ENV_ARCHIVE_SCHEDULER_DEFINITION, SchedulerService.DEFAULT_SCHEDULER_DEFINITION);
		logger.info("......"+ClusterService.ENV_ARCHIVE_CLUSTER_CONTACTPOINTS + "=" + contactPoints);
		logger.info("......"+ClusterService.ENV_ARCHIVE_CLUSTER_KEYSPACE+ "=" + keySpace);

		

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



	public String getKeySpace() {
		return keySpace;
	}

	
	public String getScheduler() {
		return scheduler;
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