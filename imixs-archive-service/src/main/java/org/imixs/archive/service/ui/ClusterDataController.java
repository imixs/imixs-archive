package org.imixs.archive.service.ui;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.MessageService;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.archive.service.scheduler.SyncService;
import org.imixs.workflow.ItemCollection;

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

//	Cluster cluster = null;
//	Session session = null;
	String syncSizeUnit = null;
	ItemCollection metaData = null;

	@Inject
	ClusterService clusterService;

	@Inject
	DataService dataService;

	@Inject
	SyncService syncService;

	@Inject
	MessageService messageService;
	
	@Inject
	@ConfigProperty(name =ClusterService.ENV_ARCHIVE_CLUSTER_CONTACTPOINTS, defaultValue = "")
	String contactPoint;

	@Inject
	@ConfigProperty(name =ClusterService.ENV_ARCHIVE_CLUSTER_KEYSPACE, defaultValue = "")
	String keySpace;
	

	@Inject
	@ConfigProperty(name = ClusterService.ENV_ARCHIVE_SCHEDULER_DEFINITION, defaultValue = "")
	String schedulerDefinition;


	@Inject
	@ConfigProperty(name =ClusterService.ENV_ARCHIVE_CLUSTER_REPLICATION_FACTOR, defaultValue = "1")
	String repFactor;

	@Inject
	@ConfigProperty(name =ClusterService.ENV_ARCHIVE_CLUSTER_REPLICATION_CLASS, defaultValue = "SimpleStrategy")
	String repClass;
	
	@Inject
	@ConfigProperty(name = ClusterService.ENV_WORKFLOW_SERVICE_ENDPOINT, defaultValue = "")
	String workflowServiceEndpoint;

	

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
//		cluster = clusterService.getCluster();
//		session = clusterService.getArchiveSession(cluster);

		// load metadata
		metaData = dataService.loadMetadata();
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
//		if (session != null) {
//			session.close();
//		}
//		if (cluster != null) {
//			cluster.close();
//		}
	}

	/**
	 * Returns true if a connection to the specified keySpace was successful
	 * 
	 * @return true if session was successfull established.
	 */
	public boolean isConnected() {
		return (clusterService.getSession() != null);
	}

	/**
	 * This method starts a restore process
	 * 
	 * 
	 */
	public void startSync() {
		try {
			syncService.startScheduler();
		} catch (ArchiveException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * This method starts a restore process
	 * 
	 * 
	 */
	public void stopSync() {
		try {
			syncService.stopScheduler();
		} catch (ArchiveException e) {
			e.printStackTrace();
		}
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
		String result = MessageService.userFriendlyBytes(l);

		String[] parts = result.split(" ");
		syncSizeUnit = parts[1];
		return parts[0];
	}

	public String getSyncSizeUnit() {
		return syncSizeUnit;
	}

	
	public String getContactPoints() {
		return contactPoint;
	}

	public String getKeySpace() {
		return keySpace;
	}

	public String getScheduler() {
		return schedulerDefinition;
	}

	public String getReplicationFactor() {
		return repFactor;

	}

	public String getReplicationClass() {
		return repClass;
	}

	public String getServiceEndpoint() {
		return workflowServiceEndpoint;
	}

	public Date getNextTimeout() {
		return syncService.getNextTimeout();
	}

	/**
	 * Returns the message list in reverse order.
	 * 
	 * @return
	 */
	public List<String> getMessages() {
		List<String> messageLog = messageService.getMessages();
		// revrese order (use cloned list)
		List<String> result = new ArrayList<String>();
		for (String message : messageLog) {
			result.add(message);
		}
		Collections.reverse(result);
		return result;
	}
}