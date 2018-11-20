package org.imixs.archive.service.mvc;

import java.io.Serializable;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DocumentService;
import org.imixs.archive.service.scheduler.SchedulerService;

import com.datastax.driver.core.Session;

/**
 * CID Bean provide cluster configuration .
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

	int archiveCount;
	long syncCount, errorCount, errorCountSync, errorCountObject;
	//Date nextTimeout;

	@EJB
	ClusterService clusterService;

	@EJB
	DocumentService documentService;

	@EJB
	SchedulerService schedulerService;

	@Inject
	ErrorController errorController;

	public ClusterDataController() {
		super();
	}

	/**
	 * This method verifies and initializes the core keyspace
	 * 
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

	}

	/**
	 * returns the syncpoint of the current configuration
	 * 
	 * @return
	 */
	public Date getSyncPoint() {

		long lsyncPoint = documentService.getSyncpoint();

		Date syncPoint = new Date(lsyncPoint);
		return syncPoint;
	}

	public String getContactPoints() {
		return ClusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_CONTACTPOINTS, null);
	}

	public String getKeySpace() {
		return ClusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_KEYSPACE, null);
	}

	public String getScheduler() {
		return ClusterService.getEnv(ClusterService.ENV_ARCHIVE_SCHEDULER_DEFINITION,
				SchedulerService.DEFAULT_SCHEDULER_DEFINITION);
	}

	public String getReplicationFactor() {
		return ClusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_REPLICATION_FACTOR, "1");

	}

	public String getReplicationClass() {
		return ClusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_REPLICATION_CLASS, "SimpleStrategy");
	}

	
	
	public Date getNextTimeout() {
		return schedulerService.getNextTimeout();
	}

	/**
	 * returns true if a connection to the specified keySpace was successful
	 * 
	 * @return
	 */
	public boolean isConnected() {

		Session _session;
		try {
			_session = clusterService.getArchiveSession();
		} catch (ArchiveException e) {
			errorController.setMessage(e.getMessage());
			return false;
		}
		return _session != null;
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