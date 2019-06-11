package org.imixs.archive.service.ui;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
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
 * CID Bean for the resync service.
 * <p>
 * The new syncpoint is managed as a string in the format 2019-12-31T06:00
 * 
 * 
 * 
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class ResyncController implements Serializable {

	public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(ResyncController.class.getName());

//	Cluster cluster = null;
//	Session session = null;

	ItemCollection metaData = null;
	String newSyncPoint = null;

	@EJB
	ClusterService clusterService;

	@EJB
	DataService dataService;

	@EJB
	MessageService messageService;

	public ResyncController() {
		super();
	}

	/**
	 * This method initializes the default sync date
	 * 
	 * @throws ArchiveException
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
	 * Returns the newSyncPoint and computes the default value
	 * 
	 * @return
	 */
	public String getNewSyncPoint() {
		if (newSyncPoint==null) {
			// compute default
			SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
			newSyncPoint=dt.format(getSyncPoint());
			
		}
		return newSyncPoint;
	}

	public void setNewSyncPoint(String newSyncPoint) {
		this.newSyncPoint = newSyncPoint;
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

	/**
	 * This method updates the current synpoint
	 * 
	 * @throws ArchiveException
	 */
	public void updateSyncPoint() {
		try {
			// update sync date...
			SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
			Date syncDate = dt.parse(newSyncPoint);
			logger.info("......updateing syncpoint=" + this.newSyncPoint);
			metaData.setItemValue(SyncService.ITEM_SYNCPOINT, syncDate.getTime());
			dataService.saveMetadata(metaData);

		} catch (ArchiveException | ParseException e) {
			logger.severe("failed to set new syncpoint: " + e.getMessage());
		}

	}

}