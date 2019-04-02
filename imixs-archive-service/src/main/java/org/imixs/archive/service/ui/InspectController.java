package org.imixs.archive.service.ui;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.enterprise.context.SessionScoped;
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
 * CID Bean to inspect a single process instance.
 * <p>
 * 
 * @author rsoika
 *
 */
@Named
@SessionScoped
public class InspectController implements Serializable {

	public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(InspectController.class.getName());

	Cluster cluster = null;
	Session session = null;
	String uniqueid = null;
	List<String> snapshotIDs = null;
	String currentSnapshotID=null;

	@EJB
	ClusterService clusterService;

	@EJB
	DataService dataService;

	@EJB
	SyncService syncService;

	@EJB
	MessageService messageService;

	public InspectController() {
		super();
	}

	/**
	 * This method initializes the default sync date
	 * 
	 */
	@PostConstruct
	void init() {

	}

	public String getUniqueid() {
		return uniqueid;
	}

	public void setUniqueid(String uniqueid) {
		this.uniqueid = uniqueid;
	}

	public List<String> getSnapshotIDs() {
		if (snapshotIDs == null) {
			snapshotIDs = new ArrayList<String>();
		}
		return snapshotIDs;
	}

	public void setSnapshotIDs(List<String> snapshotIDs) {
		this.snapshotIDs = snapshotIDs;
	}

	/**
	 * returns the current snapshot id form the workflow instance.
	 * @return
	 */
	public String getCurrentSnapshotID() {
		return currentSnapshotID;
	}

	public void setCurrentSnapshotID(String currentSnapshotID) {
		this.currentSnapshotID = currentSnapshotID;
	}

	/**
	 * This method returns the snapshot timestamp by a snapshot id.
	 * 
	 * 
	 * @param id
	 * @return
	 */
	public String getTime(String id) {
		// cut last segment
		String sTime = id.substring(id.lastIndexOf('-') + 1);

		long time = Long.parseLong(sTime);
		Date date = new Date(time);

		return date.toString();
	}

	/**
	 * This method loads all existing snapshot ids of a given unqiueid
	 * <p>
	 * The result list is sorted creation date descending (newest snapshot first)
	 * <p>
	 * The method also verifies the actual snapshot in the workflow instance and creats an indicator
	 * 
	 * @throws ArchiveException
	 */
	public void loadSnapshotIDs() {
		try {
			cluster = clusterService.getCluster();
			session = clusterService.getArchiveSession(cluster);
			logger.info("......load snsaphosts for " + uniqueid + "...");

			snapshotIDs = dataService.loadSnapshotsByUnqiueID(uniqueid, session);

			Collections.sort(snapshotIDs, Collections.reverseOrder());
			
			// test the current snapshot from the live system!
			setCurrentSnapshotID(syncService.readSnapshotIDByUniqueID(uniqueid));

		} catch (ArchiveException e) {
			logger.severe("failed to load snapshot ids: " + e.getMessage());

		} finally {
			// close session and cluster object
			if (session != null) {
				session.close();
			}
			if (cluster != null) {
				cluster.close();
			}
		}
		
		
		

	}

	/**
	 * This method loads all existing snapshot ids of a given unqiueid
	 * 
	 * @throws ArchiveException
	 */
	public void restoreSnapshot(String id) {
		try {
			cluster = clusterService.getCluster();
			session = clusterService.getArchiveSession(cluster);
			logger.info("......load snsaphosts for " + uniqueid + "...");

			ItemCollection snapshot = dataService.loadSnapshot(id, session);
			syncService.restoreSnapshot(snapshot);
			
			// refresh snapshot list....
			snapshotIDs = dataService.loadSnapshotsByUnqiueID(uniqueid, session);
			Collections.sort(snapshotIDs, Collections.reverseOrder());
			// test the current snapshot from the live system!
			setCurrentSnapshotID(syncService.readSnapshotIDByUniqueID(uniqueid));


		} catch (ArchiveException e) {
			logger.severe("failed to load snapshot ids: " + e.getMessage());

		} finally {
			// close session and cluster object
			if (session != null) {
				session.close();
			}
			if (cluster != null) {
				cluster.close();
			}
		}
		

	}

}