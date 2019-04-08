package org.imixs.archive.service.ui;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.inject.Named;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.MessageService;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.archive.service.scheduler.SyncService;
import org.imixs.workflow.ItemCollection;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

/**
 * CID Bean provide cluster configuration.
 * 
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class ClusterDataController implements Serializable {

	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(ClusterDataController.class.getName());

	Cluster cluster = null;
	Session session = null;
	String syncSizeUnit=null;
	ItemCollection metaData=null;

	@EJB
	ClusterService clusterService;

	@EJB
	DataService dataService;

	@EJB
	SyncService syncService;

	@EJB
	MessageService messageService;

	public ClusterDataController() {
		super();
	}
	
	/**
	 * This method initializes a cluster and session obejct.
	 * 
	 * @throws ArchiveException
	 * @see {@link ClusterDataController#close()}
	 */
	@PostConstruct
	void init() throws ArchiveException {
		logger.info("...initial session....");
		cluster = clusterService.getCluster();
		session = clusterService.getArchiveSession(cluster);
		
		// load metadata
		metaData = dataService.loadMetadata(session);
	}

	/**
	 * This method closes the session and cluster object.
	 * 
	 * @see {@link ClusterDataController#init()}
	 */
	@PreDestroy
	void close() {
		logger.info("...closing session....");
		// close session and cluster object
		if (session != null) {
			session.close();
		}
		if (cluster != null) {
			cluster.close();
		}
	}

	/**
	 * Returns true if a connection to the specified keySpace was successful
	 * 
	 * @return true if session was successfull established. 
	 */
	public boolean isConnected() {
		return (session != null);
	}

	/**
	 * returns the syncpoint of the current configuration
	 * 
	 * @return
	 */
	public Date getSyncPoint() {
		long lsyncPoint = metaData.getItemValueLong(SyncService.ITEM_SYNCPOINT);
		Date syncPoint = new Date(lsyncPoint);
		return syncPoint;
	}

	public long getSyncCount() {
		return metaData.getItemValueLong(SyncService.ITEM_SYNCCOUNT);
	}

	public String getSyncSize() {
		long l = metaData.getItemValueLong(SyncService.ITEM_SYNCSIZE);
		String result= MessageService.userFriendlyBytes(l);
		
		String[] parts = result.split(" ");
		syncSizeUnit=parts[1];
		return parts[0];
	}
	
	public String getSyncSizeUnit() {
		return syncSizeUnit;
	}


	
	public String getContactPoints() {
		return ClusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_CONTACTPOINTS, null);
	}

	public String getKeySpace() {
		return ClusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_KEYSPACE, null);
	}

	public String getScheduler() {
		return ClusterService.getEnv(ClusterService.ENV_ARCHIVE_SCHEDULER_DEFINITION,
				SyncService.DEFAULT_SCHEDULER_DEFINITION);
	}

	public String getReplicationFactor() {
		return ClusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_REPLICATION_FACTOR, "1");

	}

	public String getReplicationClass() {
		return ClusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_REPLICATION_CLASS, "SimpleStrategy");
	}

	public String getServiceEndpoint() {
		return ClusterService.getEnv(ClusterService.ENV_WORKFLOW_SERVICE_ENDPOINT, null);
	}

	public Date getNextTimeout() {
		return syncService.getNextTimeout();
	}

	public List<String> getMessages() {
		return messageService.getMessages();
	}
}