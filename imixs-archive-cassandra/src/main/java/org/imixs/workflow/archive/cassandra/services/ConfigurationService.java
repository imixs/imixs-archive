package org.imixs.workflow.archive.cassandra.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.ItemCollectionComparator;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.archive.cassandra.ImixsArchiveApp;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.InvalidConfigurationInQueryException;
import com.datastax.driver.core.exceptions.QueryValidationException;
import com.datastax.driver.core.exceptions.SyntaxError;

/**
 * The ConfigurationService saves configurates and starts teh scheduler.
 * 
 * @author rsoika
 * 
 */
@Stateless
public class ConfigurationService {

	private static Logger logger = Logger.getLogger(ConfigurationService.class.getName());

	@EJB
	ClusterService clusterService;

	@EJB
	SchedulerService schedulerService;

	/**
	 * This method initializes the Core-KeySpace and creates the table schema if not
	 * exits. The method returns true if the Core-KeySpace is accessible.
	 * <p>
	 * The method starts all enabled schedulers for existing configurations. 
	 * 
	 */
	public boolean init() {
		try {
			logger.info("...init imixsarchive keyspace ...");
			Session session = clusterService.getCoreSession();

			if (session != null) {
				// start archive schedulers....
				logger.info("...starting schedulers...");
				List<ItemCollection> archives = this.getConfigurationList();
				for (ItemCollection configItemCollection : archives) {
					if (configItemCollection.getItemValueBoolean(SchedulerService.ITEM_SCHEDULER_ENABLED)) {
						schedulerService.start(configItemCollection.getItemValueString(ImixsArchiveApp.ITEM_KEYSPACE));
					}
				}

				return true;
			} else {
				logger.warning("...Failed to initalize imixsarchive keyspace!");
				return false;
			}

		} catch (Exception e) {
			logger.warning("...Failed to initalize imixsarchive keyspace: " + e.getMessage());
			return false;
		}
	}

	/**
	 * This method saves a archive configuration into the Core-KeySpace. A
	 * configuration defines an archive keyspace.
	 * 
	 * @param configuration
	 * @throws ImixsArchiveException
	 */
	public void saveConfiguration(ItemCollection configuration) throws ImixsArchiveException {
		byte[] data = null;
		PreparedStatement statement = null;
		BoundStatement bound = null;

		String keyspace = configuration.getItemValueString(ImixsArchiveApp.ITEM_KEYSPACE);
	
		if (keyspace.isEmpty()) {
			throw new ImixsArchiveException(ImixsArchiveException.INVALID_DOCUMENT_OBJECT,"missing keyspace!");
		}
		configuration.replaceItemValue(WorkflowKernel.MODIFIED, new Date());

		// do we have a valid SyncPoint?
		long lSyncpoint = configuration.getItemValueLong(ImixsArchiveApp.ITEM_SYNCPOINT);
		if (lSyncpoint == 0) {
			logger.info("......initialized new syncpoint");
		}

		try {
			// create byte array from XMLDocument...
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

			JAXBContext context;
			context = JAXBContext.newInstance(XMLDocument.class);
			Marshaller m = context.createMarshaller();
			XMLDocument xmlDocument = XMLDocumentAdapter.getDocument(configuration);
			m.marshal(xmlDocument, outputStream);
			data = outputStream.toByteArray();
		} catch (JAXBException | AccessDeniedException e) {
			throw new ImixsArchiveException(ImixsArchiveException.INVALID_DOCUMENT_OBJECT, e.getMessage(), e);
		}

		// upset document....
		Session session = clusterService.getCoreSession();
		statement = session.prepare("insert into configurations (id, data) values (?, ?)");
		bound = statement.bind().setString("id", keyspace).setBytes("data", ByteBuffer.wrap(data));
		session.execute(bound);

		// create keyspace if not exists
		session = clusterService.getArchiveSession(keyspace);

	}

	/**
	 * This method deletes an archive configuration from the Core-KeySpace and the
	 * corresponding keyspace.
	 * 
	 * @param configuration
	 * @return error messages if occured during deletion.
	 */
	public String deleteConfiguration(ItemCollection configuration) {

		String errorMessage = "";
		String keyspace = configuration.getItemValueString(ImixsArchiveApp.ITEM_KEYSPACE);

		// first stop the timer
		schedulerService.stop(configuration.getItemValueString(ImixsArchiveApp.ITEM_KEYSPACE));

		// next delete the keyspace....
		try {
			Cluster cluster = clusterService.getCluster();
			Session session = cluster.connect();
			session.execute("DROP KEYSPACE " + keyspace + "");
			logger.info("......keyspace deleted...");
		} catch (InvalidConfigurationInQueryException | SyntaxError e) {
			String message = "Deletion of keyspace '" + keyspace + "' failed: " + e.getMessage();
			logger.warning("......" + message);
			errorMessage += message + "\n";
		}

		// finally delete the configuration entry ....
		try {
			Session session = clusterService.getCoreSession();
			session.execute("DELETE FROM configurations WHERE id IN ('" + keyspace + "')");
			logger.info("......archive configuration deleted...");
		} catch (QueryValidationException e) {
			String message = "deletion of configuration for keyspace '" + keyspace + "' failed: " + e.getMessage();
			logger.warning("......" + message);
			errorMessage += message + "\n";
		}

		return errorMessage;
	}

	/**
	 * Returns a list of all existing archive configurations which are stored in the
	 * Core-KeySpace. The method updates the timer details for each configuration
	 * delevered by the ScheudlerService.
	 * <p>
	 * The flag "_scheduler_enabled" indicates if a timer for a configuraiton is
	 * running.
	 * 
	 * 
	 * @return configuration list
	 */
	public List<ItemCollection> getConfigurationList() {
		List<ItemCollection> result = new ArrayList<ItemCollection>();

		logger.finest("......load configuraiton list...");
		// get session from Core-KeySpace....
		try {
			Session session = clusterService.getCoreSession();

			ResultSet resultSet = session.execute("SELECT * FROM configurations;");
			for (Row row : resultSet) {
				// String keyspace = row.getString("id");
				byte[] source = row.getBytes("data").array();

				ByteArrayInputStream bis = new ByteArrayInputStream(source);

				JAXBContext context;
				context = JAXBContext.newInstance(XMLDocument.class);
				Unmarshaller m = context.createUnmarshaller();
				Object jaxbObject = m.unmarshal(bis);
				if (jaxbObject == null) {
					throw new RuntimeException(
							"readCollection error - wrong xml file format - unable to read content!");
				}
				XMLDocument xmlDocument = (XMLDocument) jaxbObject;

				result.add(XMLDocumentAdapter.putDocument(xmlDocument));

			}

		} catch (JAXBException e) {
			logger.severe("failed to read result set: " + e.getMessage());
			e.printStackTrace();
			return null;
		}

		// Update the timer details for each confiugraton
		for (ItemCollection config : result) {
			schedulerService.updateTimerDetails(config);
		}

		// sort result
		Collections.sort(result, new ItemCollectionComparator(ImixsArchiveApp.ITEM_KEYSPACE, true));
		return result;
	}

	/**
	 * Loads a configuration from the core-keyspace by a keyspaceId.
	 * 
	 * @return
	 */
	public ItemCollection loadConfiguration(String keyspaceID) {
		// get session from archive....
		try {
			Session session = clusterService.getCoreSession();

			ResultSet resultSet = session.execute("SELECT * FROM configurations WHERE id='" + keyspaceID + "';");
			Row row = resultSet.one();
			if (row != null) {
				// String keyspace = row.getString("id");
				byte[] source = row.getBytes("data").array();

				ByteArrayInputStream bis = new ByteArrayInputStream(source);

				JAXBContext context;
				context = JAXBContext.newInstance(XMLDocument.class);
				Unmarshaller m = context.createUnmarshaller();
				Object jaxbObject = m.unmarshal(bis);
				if (jaxbObject == null) {
					throw new RuntimeException(
							"readCollection error - wrong xml file format - unable to read content!");
				}
				XMLDocument xmlDocument = (XMLDocument) jaxbObject;

				return XMLDocumentAdapter.putDocument(xmlDocument);

			}

		} catch (JAXBException e) {
			logger.severe("failed to read result set: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
		return null;
	}

}
