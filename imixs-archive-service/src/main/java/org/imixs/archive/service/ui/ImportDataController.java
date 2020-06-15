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
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.archive.service.exports.ExportService;
import org.imixs.archive.service.imports.ImportService;
import org.imixs.archive.service.util.MessageService;
import org.imixs.workflow.ItemCollection;

/**
 * CID Bean provide the export configuration.
 * 
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class ImportDataController implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private static Logger logger = Logger.getLogger(ImportDataController.class.getName());

	String syncSizeUnit = null;
	ItemCollection metaData = null;

	@Inject
	ClusterService clusterService;

	@Inject
	DataService dataService;

	@Inject
	ImportService importService;

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



	

	public ImportDataController() {
		super();
	}

	/**
	 * This method initializes a cluster and session obejct.
	 * 
	 * @throws ArchiveException
	 * @see {@link ImportDataController#close()}
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
	public void start() {
		try {
			importService.start();
		} catch (ArchiveException e) {
			e.printStackTrace();
		}
	}
	
	

	/**
	 * returns the syncpoint of the current configuration
	 * 
	 * @return
	 */
	public Date getImportPoint() {
		long lsyncPoint = metaData.getItemValueLong(ImportService.ITEM_IMPORTPOINT);
		Date syncPoint = new Date(lsyncPoint);
		return syncPoint;
	}

	public long getImportCount() {
		return metaData.getItemValueLong(ImportService.ITEM_IMPORTCOUNT);
	}

	public String getImportSize() {
		long l = metaData.getItemValueLong(ImportService.ITEM_IMPORTSIZE);
		String result = messageService.userFriendlyBytes(l);

		String[] parts = result.split(" ");
		syncSizeUnit = parts[1];
		return parts[0];
	}

	public String getImportSizeUnit() {
		return syncSizeUnit;
	}

	
	

	/**
	 * Returns the message list in reverse order.
	 * 
	 * @return
	 */
	public List<String> getMessages() {
		List<String> messageLog = messageService.getMessages(ImportService.MESSAGE_TOPIC);
		// revrese order (use cloned list)
		List<String> result = new ArrayList<String>();
		for (String message : messageLog) {
			result.add(message);
		}
		Collections.reverse(result);
		return result;
	}
	
	
	public boolean isRunning() {
		return importService.isRunning();
	}

	/**
	 * This method reset the current synpoint to 0 and prepares a new export
	 * 
	 * 
	 * @throws ArchiveException
	 */
	public void cancel() {
		try {
			importService.cancel();
		} catch (ArchiveException e) {			
			e.printStackTrace();
		}

	}
}