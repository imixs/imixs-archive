package org.imixs.workflow.archive.cassandra.services;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.LocalDate;
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
	public static final String PROPERTY_ARCHIVE_CLUSTER_REPLICATION_FACTOR = "archive.cluster.replication_factor";
	public static final String PROPERTY_ARCHIVE_CLUSTER_REPLICATION_CLASS = "archive.cluster.replication_class";
	public static final String PROPERTY_ARCHIVE_CORE_KEYSPACE = "archive.core.keyspace";
	public static final String DEFAULT_CORE_KEYSPACE = "imixsarchive";

	// table schemas

	public static final String TABLE_SCHEMA_DOCUMENT = "CREATE TABLE IF NOT EXISTS document (id text, data blob, PRIMARY KEY (id))";
	public static final String TABLE_SCHEMA_DOCUMENT_SNAPSHOTS = "CREATE TABLE IF NOT EXISTS document_snapshots (uniqueid text,snapshot text, PRIMARY KEY(uniqueid, snapshot));";
	public static final String TABLE_SCHEMA_DOCUMENT_MODIFIED = "CREATE TABLE IF NOT EXISTS document_modified (modified date,id text,PRIMARY KEY(modified, id));";

	private static final String REGEX_SNAPSHOTID = "([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}-[0-9]{13,15})";
	private static Logger logger = Logger.getLogger(ClusterService.class.getName());

	@EJB
	PropertyService propertyService;

	/**
	 * Test the local connection
	 */
	public void testCluster() {

	}

	/**
	 * This method initializes the core keyspace and creates the table schema if not
	 * exits. The method returns true if the keyspace is accessable
	 */
	public boolean init() {
		try {
			logger.info("......init core keyspace ...");
			Session session = getSession(null);
			// create core tabel schema
			createTableSchema(session);
		} catch (Exception e) {
			logger.warning("......init cluster keyspace failed: " + e.getMessage());
			return false;
		}
		return true;
	}

	/**
	 * Returns a cassandra session for a KeySpace. If no keySpace is defined, the
	 * core keyspace will be returned. If no keyspace with this given keyspace name
	 * exists, the method creates the keyspace and table schemas.
	 */
	public Session getSession(String keySpace) {

		if (keySpace == null || keySpace.isEmpty()) {
			keySpace = propertyService.getProperties().getProperty(PROPERTY_ARCHIVE_CORE_KEYSPACE,
					DEFAULT_CORE_KEYSPACE);
		}

		logger.info("......get session...");
		Cluster cluster = getCluster();

		// try to open keySpace
		logger.info("......conecting keyspace '" + keySpace + "'...");

		Session session = null;
		try {
			session = cluster.connect(keySpace);
		} catch (InvalidQueryException e) {
			logger.warning("......conecting keyspace '" + keySpace + "' failed: " + e.getMessage());
			// create keyspace...
			session = createKeSpace(keySpace);
		}
		if (session != null) {
			logger.info("......keyspace conection status = OK");
		}
		return session;
	}

	/**
	 * Returns a cassandra session for a ImixsArchive core KeySpace.
	 */
	public Session getSession() {
		return getSession(null);
	}

	public Cluster getCluster() {
		String contactPoint = propertyService.getProperties().getProperty(PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT);
		logger.info("......cluster conecting...");
		Cluster cluster = Cluster.builder().addContactPoint(contactPoint).build();
		cluster.init();

		logger.info("......cluster conection status = OK");
		return cluster;

	}

	/**
	 * This method saves a ItemCollection into the keyspace defined by the given
	 * session
	 * 
	 * @param itemCol
	 * @param session
	 * @throws ImixsArchiveException 
	 */
	public void save(ItemCollection itemCol, Session session) throws ImixsArchiveException {
		byte[] data = null;
		PreparedStatement statement = null;
		BoundStatement bound = null;

		String snapshotID = itemCol.getUniqueID();
		if (!isSnapshotID(snapshotID)) {
			throw new IllegalArgumentException("invalid item '$snapshotid'");
		}

		if (!itemCol.hasItem("$modified")) {
			throw new IllegalArgumentException("missing item '$modified'");
		}

		// extract $snapshotid 2de78aec-6f14-4345-8acf-dd37ae84875d-1530315900599
		String[] snapshotSegments = snapshotID.split("-");
		String snapshotDigits = snapshotSegments[snapshotSegments.length - 1];
		String originUnqiueID = snapshotID.substring(0, snapshotID.lastIndexOf("-"));

		// create byte array from XMLDocument...
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			JAXBContext context;
			context = JAXBContext.newInstance(XMLDocument.class);
			Marshaller m = context.createMarshaller();
			XMLDocument xmlDocument = XMLDocumentAdapter.getDocument(itemCol);
			m.marshal(xmlDocument, outputStream);
			data = outputStream.toByteArray();
		} catch (JAXBException e) {
			throw new ImixsArchiveException(ImixsArchiveException.INVALID_DOCUMENT_OBJECT, e.getMessage(),e);
		}

		// upset document....
		statement = session.prepare("insert into document (id, data) values (?, ?)");
		bound = statement.bind().setString("id", itemCol.getUniqueID()).setBytes("adta", ByteBuffer.wrap(data));
		session.execute(bound);

		// upset document_snapshots....
		statement = session.prepare("insert into document_snapshots (uniqueid, snapshot) values (?, ?)");
		bound = statement.bind().setString("uniqueid", originUnqiueID).setString("snapshot", snapshotDigits);
		session.execute(bound);

		// upset document_modified....
		LocalDate ld = LocalDate.fromMillisSinceEpoch(itemCol.getItemValueDate("$modified").getTime());
		statement = session.prepare("insert into document_snapshots (date, id) values (?, ?)");
		bound = statement.bind().setDate("date", ld).setString("uniqueid", originUnqiueID).setString("id",
				itemCol.getUniqueID());
		session.execute(bound);
	}

	/**
	 * This method returns true if the given id is a valid Snapshot id (UUI +
	 * timestamp
	 * 
	 * @param uid
	 * @return
	 */
	public static boolean isSnapshotID(String uid) {
		return uid.matches(REGEX_SNAPSHOTID);
	}

	/**
	 * This method creates a keySpace
	 * 
	 * @param cluster
	 */
	private Session createKeSpace(String keySpace) {
		logger.info("......creating new keyspace '" + keySpace + "'...");

		Cluster cluster = getCluster();
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

			// now create table schemas
			createTableSchema(session);
		}
		return session;
	}

	/**
	 * This helper method creates the imixs-data table if not yet exists
	 */
	private void createTableSchema(Session session) {

		logger.info(TABLE_SCHEMA_DOCUMENT);
		session.execute(TABLE_SCHEMA_DOCUMENT);

		logger.info(TABLE_SCHEMA_DOCUMENT_SNAPSHOTS);
		session.execute(TABLE_SCHEMA_DOCUMENT_SNAPSHOTS);

		logger.info(TABLE_SCHEMA_DOCUMENT_MODIFIED);
		session.execute(TABLE_SCHEMA_DOCUMENT_MODIFIED);

	}

}
