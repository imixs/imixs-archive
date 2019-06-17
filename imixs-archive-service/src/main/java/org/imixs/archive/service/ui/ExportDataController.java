package org.imixs.archive.service.ui;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.MessageService;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.archive.service.export.ExportService;
import org.imixs.archive.service.resync.SyncService;
import org.imixs.workflow.ItemCollection;

/**
 * CID Bean provide the export configuration.
 * 
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class ExportDataController implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private static Logger logger = Logger.getLogger(ExportDataController.class.getName());

	String syncSizeUnit = null;
	ItemCollection metaData = null;

	@Inject
	ClusterService clusterService;

	@Inject
	DataService dataService;

	@Inject
	ExportService exportService;

	@Inject
	MessageService messageService;
	
	@Inject
	@ConfigProperty(name =ClusterService.ENV_ARCHIVE_CLUSTER_CONTACTPOINTS, defaultValue = "")
	String contactPoint;

	@Inject
	@ConfigProperty(name =ClusterService.ENV_ARCHIVE_CLUSTER_KEYSPACE, defaultValue = "")
	String keySpace;
	

	@Inject
	@ConfigProperty(name = ExportService.ENV_EXPORT_SCHEDULER_DEFINITION, defaultValue = "")
	String schedulerDefinition;



	

	public ExportDataController() {
		super();
	}

	/**
	 * This method initializes a cluster and session obejct.
	 * 
	 * @throws ArchiveException
	 * @see {@link ExportDataController#close()}
	 */
	@PostConstruct
	void init() {
		// load metadata
		try {
			metaData = dataService.loadMetadata();
		} catch (ArchiveException e) {
			logger.severe("Failed to load meta data!");
			e.printStackTrace();
		}
	}



	/**
	 * This method starts a restore process
	 * 
	 * 
	 */
	public void startSync() {
		try {
			exportService.startScheduler();
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
			exportService.stopScheduler();
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
		String result = messageService.userFriendlyBytes(l);

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

	public Date getNextTimeout() {
		return exportService.getNextTimeout();
	}

	/**
	 * Returns the message list in reverse order.
	 * 
	 * @return
	 */
	public List<String> getMessages() {
		List<String> messageLog = messageService.getMessages(ExportService.MESSAGE_TOPIC);
		// revrese order (use cloned list)
		List<String> result = new ArrayList<String>();
		for (String message : messageLog) {
			result.add(message);
		}
		Collections.reverse(result);
		return result;
	}
}