package org.imixs.workflow.archive.cassandra.services;

import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.xml.bind.JAXBException;

import org.imixs.workflow.ItemCollection;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.InvalidQueryException;

/**
 * Service to persist the content of a Imixs Document into a Cassandra keystore.
 * 
 * The service saves the content in XML format. The size of an XML
 * representation of a Imixs document is only slightly different in size from
 * the serialized map object. This is the reason why we do not store the
 * document map in a serialized object format.
 * 
 * @author rsoika
 * 
 */
@Stateless
public class ClusterService {

	public static final String PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT = "archive.cluster.contactpoints";
	public static final String PROPERTY_ARCHIVE_CLUSTER_KEYSPACE = "archive.cluster.keyspace";
	public static final String PROPERTY_ARCHIVE_CLUSTER_REPLICATION_FACTOR = "archive.cluster.replication_factor";
	public static final String PROPERTY_ARCHIVE_CLUSTER_REPLICATION_CLASS = "archive.cluster.replication_class";

	// table schemas

	public static final String TABLE_SCHEMA_DOCUMENT = "CREATE TABLE IF NOT EXISTS document (id text, data blob, PRIMARY KEY (id))";
	public static final String TABLE_SCHEMA_DOCUMENT_SNAPSHOTS = "CREATE TABLE IF NOT EXISTS document_snapshots (uniqueid text,snapshot text, PRIMARY KEY(uniqueid, snapshot));";
	public static final String TABLE_SCHEMA_DOCUMENT_MODIFIED = "CREATE TABLE IF NOT EXISTS document_modified (modified date,id text,PRIMARY KEY(modified, id));";

	private static Logger logger = Logger.getLogger(ClusterService.class.getName());

	@EJB
	PropertyService propertyService;

	/**
	 * Test the local connection
	 */
	public void testCluster() {

	}

	/**
	 * Helper method to get a session for the configured keyspace
	 */
	public Session connect() {

		String contactPoint = propertyService.getProperties().getProperty(PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT);
		String keySpace = propertyService.getProperties().getProperty(PROPERTY_ARCHIVE_CLUSTER_KEYSPACE);

		logger.info("......cluster conecting...");
		Cluster cluster = Cluster.builder().addContactPoint(contactPoint).build();
		cluster.init();

		logger.info("......cluster conection status = OK");

		// try to open keySpace
		logger.info("......conecting keyspace '" + keySpace + "'...");

		Session session = null;
		try {
			session = cluster.connect(keySpace);
		} catch (InvalidQueryException e) {
			logger.warning("......conecting keyspace '" + keySpace + "' failed: " + e.getMessage());
			// create keyspace...
			session = createKeSpace(cluster, keySpace);
		}
		if (session != null) {
			logger.info("......keyspace conection status = OK");
		}
		return session;
	}

	public Cluster getCluster() {
		String contactPoint = propertyService.getProperties().getProperty(PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT);
		String keySpace = propertyService.getProperties().getProperty(PROPERTY_ARCHIVE_CLUSTER_KEYSPACE);

		logger.info("......cluster conecting...");
		Cluster cluster = Cluster.builder().addContactPoint(contactPoint).build();
		cluster.init();

		logger.info("......cluster conection status = OK");
		return cluster;

	}

	public void save(ItemCollection itemCol, Session session) throws JAXBException {

		PreparedStatement ps1 = session
				.prepare("insert into document (id, type, created, modified, data) values (?, ?, ?, ?, ?)");

		BoundStatement bound = ps1.bind().setString("id", itemCol.getUniqueID()).setString("type", itemCol.getType())
				.setTimestamp("created", itemCol.getItemValueDate("$created"))
				.setTimestamp("modified", itemCol.getItemValueDate("$modified")).setString("data", getXML(itemCol));

		session.execute(bound);
	}

	/**
	 * This method creates a keypace
	 * 
	 * @param cluster
	 */
	public Session createKeSpace(Cluster cluster, String keySpace) {
		logger.info("......creating new keyspace '" + keySpace + "'...");

		Session session = cluster.connect();

		String repFactor = propertyService.getProperties().getProperty(PROPERTY_ARCHIVE_CLUSTER_REPLICATION_FACTOR,
				"1");
		String repClass = propertyService.getProperties().getProperty(PROPERTY_ARCHIVE_CLUSTER_REPLICATION_CLASS,
				"SimpleStrategy");

		String statement = "CREATE KEYSPACE IF NOT EXISTS " + keySpace + " WITH replication = {'class': '" + repClass
				+ "', 'replication_factor': " + repFactor + "};";
		logger.info("......keyspace created...");
		session.execute(statement);
		// try to connect again to keyspace...
		session = cluster.connect(keySpace);
		if (session != null) {
			logger.info("......keyspace conection status = OK");
		}
		return session;
	}

	/**
	 * This helper method creates the imixs-data table if not yet exists
	 */
	public void createTableSchema(Session session) {

		logger.info(TABLE_SCHEMA_DOCUMENT);
		session.execute(TABLE_SCHEMA_DOCUMENT);

		logger.info(TABLE_SCHEMA_DOCUMENT_SNAPSHOTS);
		session.execute(TABLE_SCHEMA_DOCUMENT_SNAPSHOTS);

		logger.info(TABLE_SCHEMA_DOCUMENT_MODIFIED);
		session.execute(TABLE_SCHEMA_DOCUMENT_MODIFIED);

	}

	/**
	 * Converts a ItemCollection into a xml string
	 * 
	 * @param itemcol
	 * @return
	 * @throws JAXBException
	 */
	private String getXML(ItemCollection itemCol) throws JAXBException {
		String result = null;
		// // convert the ItemCollection into a XMLItemcollection...
		// XMLItemCollection xmlItemCollection =
		// XMLItemCollectionAdapter.putItemCollection(itemCol);
		//
		// // marshal the Object into an XML Stream....
		// StringWriter writer = new StringWriter();
		// JAXBContext context = JAXBContext.newInstance(XMLItemCollection.class);
		// Marshaller m = context.createMarshaller();
		// m.marshal(xmlItemCollection, writer);
		//
		// result = writer.toString();
		return result;

	}
}
