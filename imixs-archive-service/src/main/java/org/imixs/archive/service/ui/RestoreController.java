package org.imixs.archive.service.ui;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.ejb.Timer;
import javax.enterprise.context.RequestScoped;
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
@RequestScoped
public class RestoreController implements Serializable {

	public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(RestoreController.class.getName());

	Cluster cluster = null;
	Session session = null;
	long restoreDateFrom;
	long restoreDateTo;
	String restoreSizeUnit = null;
	ItemCollection metaData = null;

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
	void init() throws ArchiveException {
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

	public String getRestoreFrom() {

		SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
		return dt.format(restoreDateFrom);

	}

	public void setRestoreFrom(String restorePoint) throws ParseException {
		if (restorePoint != null && !restorePoint.isEmpty()) {
			// update sync date...
			SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
			try {
				restoreDateFrom = dt.parse(restorePoint).getTime();
			} catch (ParseException e) {
				logger.severe("Unable to parse syncdate: " + e.getMessage());
			}
		}
	}

	public String getRestoreTo() {
		if (restoreDateTo == 0) {
			// default current syncpoint
			restoreDateTo = getSyncPoint();

			// NOTE:
			// Because the current syncPoint has milisecont precission, but we format the
			// restoreTo date in seconds only, we need to ajust the restoreTo timestamp per
			// 1 second! Otherwise the last snaspshot is typically excluded from the restore
			// because of its milisecond precission.
			restoreDateTo=restoreDateTo+1000; // !!
		}
		SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
		return dt.format(restoreDateTo);
	}

	public void setRestoreTo(String restorePoint) {
		if (restorePoint != null && !restorePoint.isEmpty()) {
			// update sync date...
			SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
			try {
				restoreDateTo = dt.parse(restorePoint).getTime();
			} catch (ParseException e) {
				logger.severe("Unable to parse syncdate: " + e.getMessage());
			}
		}
	}

	/**
	 * returns the syncpoint of the current configuration
	 * 
	 * @return
	 */
	public Date getRestoreSyncPoint() {
		long lsyncPoint;
		lsyncPoint = metaData.getItemValueLong(RestoreService.ITEM_RESTORE_SYNCPOINT);
		Date syncPoint = new Date(lsyncPoint);
		return syncPoint;
	}

	public long getRestoreCount() {
		return metaData.getItemValueLong(RestoreService.ITEM_RESTORE_SYNCCOUNT);
	}

	public String getRestoreSize() {
		long l = metaData.getItemValueLong(RestoreService.ITEM_RESTORE_SYNCSIZE);
		String result = MessageService.userFriendlyBytes(l);
		String[] parts = result.split(" ");
		restoreSizeUnit = parts[1];
		return parts[0];
	}

	public String getRestoreSizeUnit() {
		return restoreSizeUnit;
	}

	/**
	 * returns the syncpoint of the current configuration
	 * 
	 * @return
	 */
	public long getSyncPoint() {
		return metaData.getItemValueLong(SyncService.ITEM_SYNCPOINT);
	}

	/**
	 * returns the syncpoint of the current configuration
	 * 
	 * @return
	 */
	public String getSyncPointISO() {
		SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
		Date date = new Date(getSyncPoint());
		return dt.format(date);
	}

	/**
	 * This method starts a restore process
	 * 
	 * 
	 */
	public void startRestore() {
		try {
			logger.info("......init restore process: " + this.getRestoreFrom() + " to " + this.getRestoreTo());
			restoreService.start(restoreDateFrom, restoreDateTo);
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
		return (timer != null);
	}

	public List<String> getMessages() {
		return messageService.getMessages();
	}
}