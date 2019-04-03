package org.imixs.archive.service.ui;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Timer;
import javax.enterprise.context.SessionScoped;
import javax.inject.Named;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.MessageService;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.archive.service.scheduler.RestoreService;
import org.imixs.archive.service.scheduler.SyncService;
import org.imixs.workflow.ItemCollection;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

/**
 * CID Bean for the resync service.
 * <p>
 * The syncpoint is managed as a string in the format 2019-12-31T06:00
 * 
 * 
 * 
 * @author rsoika
 *
 */
@Named
@SessionScoped
public class RestoreController implements Serializable {

	public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(RestoreController.class.getName());

	Cluster cluster = null;
	Session session = null;
	// String syncPoint=null;
	Date syncDateFrom = null;
	Date syncDateTo = null;

	@EJB
	ClusterService clusterService;

	@EJB
	DataService dataService;

	@EJB
	RestoreService restoreService;

	@EJB
	MessageService messageService;

	public RestoreController() {
		super();
	}

	/**
	 * This method initializes the default sync date
	 * 
	 */
	@PostConstruct
	void init() {
		long lSync = loadSyncPoint();
		syncDateFrom = new Date(lSync);
		syncDateTo = null;
	}

	public String getSyncPointFrom() {
		SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
		return dt.format(syncDateFrom);
	}

	public void setSyncPointFrom(String syncPoint) throws ParseException {
		// update sync date...
		SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
		syncDateFrom = dt.parse(syncPoint);

	}

	public String getSyncPointTo() {
		if (syncDateTo != null) {
			SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
			return dt.format(syncDateTo);
		} else {
			return "";
		}
	}

	public void setSyncPointTo(String syncPoint) throws ParseException {
		// update sync date...
		SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
		syncDateTo = dt.parse(syncPoint);

	}

	/**
	 * This method loads the current synpoint from the methdata object
	 * 
	 * @throws ArchiveException
	 */
	public long loadSyncPoint() {
		try {
			cluster = clusterService.getCluster();
			session = clusterService.getArchiveSession(cluster);
			logger.info("......load syncpoint...");
			ItemCollection metaData = dataService.loadMetadata(session);

			return metaData.getItemValueLong(SyncService.ITEM_SYNCPOINT);

		} catch (ArchiveException e) {
			logger.severe("failed to load syncpoint: " + e.getMessage());
			return 0;
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
	 * This method starts a restore process
	 * 
	 * 
	 */
	public void startRestore() {
		try {

			logger.info("......init restore process: " + this.getSyncPointFrom() + " to " + this.getSyncPointTo());

			restoreService.start(syncDateFrom, syncDateTo);

		} catch (ArchiveException e) {
			logger.severe("failed to start restore process: " + e.getMessage());
		}

	}

	/**
	 * Returns true if a restore is running.
	 * 
	 * @return
	 */
	public boolean isRunning() {
		Timer timer = restoreService.findTimer();
		return (timer!=null);
	}

}