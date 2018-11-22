package org.imixs.archive.service.mvc;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DocumentService;
import org.imixs.archive.service.scheduler.MessageService;
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

	
	@EJB
	ClusterService clusterService;

	@EJB
	DocumentService documentService;

	@EJB
	SchedulerService schedulerService;

	@EJB
	MessageService messageService;

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
	}

	/**
	 * returns the syncpoint of the current configuration
	 * 
	 * @return
	 */
	public Date getSyncPoint() {
		long lsyncPoint;
		try {
			lsyncPoint = documentService.getSyncPoint();
		} catch (ArchiveException e) {
			logger.severe("unable to read syncpoint - " + e.getMessage());
			lsyncPoint = 0;
		}
		Date syncPoint = new Date(lsyncPoint);
		return syncPoint;
	}
	
	public long getSyncCount() {
		long l;
		try {
			l = documentService.getSyncCount();
		} catch (ArchiveException e) {
			logger.severe("unable to read syncpoint - " + e.getMessage());
			l = 0;
		}
	
		return l;
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

	public String getServiceEndpoint() {
		return ClusterService.getEnv(ClusterService.ENV_WORKFLOW_SERVICE_ENDPOINT, null);
	}

	public Date getNextTimeout() {
		return schedulerService.getNextTimeout();
	}

	public List<String> getMessages() {
		return messageService.getMessages();
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

}