package org.imixs.archive.service.ui;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.enterprise.context.SessionScoped;
import javax.inject.Named;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.MessageService;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DocumentService;
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
public class ResyncController implements Serializable {

	public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
	
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(ResyncController.class.getName());

	Cluster cluster = null;
	Session session = null;
	// String syncPoint=null;
	Date syncDate = null;

	@EJB
	ClusterService clusterService;

	@EJB
	DocumentService documentService;

	@EJB
	MessageService messageService;

	public ResyncController() {
		super();
	}

	/**
	 * This method initializes the default sync date
	 * 
	 */
	@PostConstruct
	void init() {
		long lSync=loadSyncPoint();
		syncDate = new Date(lSync);
	}

	public String getSyncPoint() {
		SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
		return dt.format(syncDate);
	}

	public void setSyncPoint(String syncPoint) throws ParseException {
		// update sync date...
		SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
		syncDate = dt.parse(syncPoint);

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
			ItemCollection metaData = documentService.loadMetadata(session);
			
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
	 * This method updates the current synpoint
	 * 
	 * @throws ArchiveException
	 */
	public void updateSyncPoint() {
		try {
			cluster = clusterService.getCluster();
			session = clusterService.getArchiveSession(cluster);
			logger.info("......updateing syncpoint=" + this.getSyncPoint());
			ItemCollection metaData = documentService.loadMetadata(session);
			metaData.setItemValue(SyncService.ITEM_SYNCPOINT, syncDate.getTime());
			documentService.saveMetadata(metaData, session);

		} catch (ArchiveException e) {
			logger.severe("failed to set new syncpoint: " + e.getMessage());
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