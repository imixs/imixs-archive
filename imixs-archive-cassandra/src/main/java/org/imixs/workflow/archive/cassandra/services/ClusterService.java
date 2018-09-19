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
 * The ClusterService provides methods to persist the content of a Imixs
 * Document into a Cassandra keystore.
 * 
 * The service saves the content in XML format. The size of an XML
 * representation of a Imixs document is only slightly different in size from
 * the serialized map object. This is the reason why we do not store the
 * document map in a serialized object format.
 * 
 * The ClusterService creates a Core-KeySpace automatically which is used for
 * the internal management.
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

	// core table schema
	public static final String TABLE_SCHEMA_CONFIGURATION = "CREATE TABLE IF NOT EXISTS configurations (id text, data blob, PRIMARY KEY (id))";

	// archive table schemas
	public static final String TABLE_SCHEMA_SNAPSHOTS = "CREATE TABLE IF NOT EXISTS snapshots (snapshot text, data blob, PRIMARY KEY (snapshot))";
	public static final String TABLE_SCHEMA_SNAPSHOTS_BY_UNIQUEID = "CREATE TABLE IF NOT EXISTS snapshots_by_uniqueid (uniqueid text,snapshot text, PRIMARY KEY(uniqueid, snapshot));";
	public static final String TABLE_SCHEMA_SNAPSHOTS_BY_MODIFIED = "CREATE TABLE IF NOT EXISTS snapshots_by_modified (modified date,snapshot text,PRIMARY KEY(modified, snapshot));";

	private static final String REGEX_SNAPSHOTID = "([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}-[0-9]{13,15})";
	private static Logger logger = Logger.getLogger(ClusterService.class.getName());

	@EJB
	PropertyService propertyService;

	@EJB
	SchedulerService schedulerService;

	
	/**
	 * This method saves a ItemCollection into a specific KeySpace.
	 * 
	 * @param itemCol
	 * @param session
	 * @throws ImixsArchiveException
	 */
	public void saveDocument(ItemCollection itemCol, String keyspace) throws ImixsArchiveException {
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
			throw new ImixsArchiveException(ImixsArchiveException.INVALID_DOCUMENT_OBJECT, e.getMessage(), e);
		}

		// get session from archive....
		Session session = this.getArchiveSession(keyspace);

		// upset document....
		statement = session.prepare("insert into snapshots (id, data) values (?, ?)");
		bound = statement.bind().setString("id", itemCol.getUniqueID()).setBytes("data", ByteBuffer.wrap(data));
		session.execute(bound);

		// upset document_snapshots....
		statement = session.prepare("insert into snapshots_by_uniqueid (uniqueid, snapshot) values (?, ?)");
		bound = statement.bind().setString("uniqueid", originUnqiueID).setString("snapshot", snapshotDigits);
		session.execute(bound);

		// upset document_modified....
		LocalDate ld = LocalDate.fromMillisSinceEpoch(itemCol.getItemValueDate("$modified").getTime());
		statement = session.prepare("insert into snapshots_by_modified (date, id) values (?, ?)");
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
	 * Returns a cassandra session for a ImixsArchive Core-KeySpace. The
	 * Core-KeySpace is used for the internal management.
	 */
	public Session getCoreSession() {
		String coreKeySpace = null;
	
		Cluster cluster = getCluster();
	
		coreKeySpace = propertyService.getProperties().getProperty(PROPERTY_ARCHIVE_CORE_KEYSPACE,
				DEFAULT_CORE_KEYSPACE);
		Session session = null;
		try {
			session = cluster.connect(coreKeySpace);
		} catch (InvalidQueryException e) {
			logger.warning("......conecting keyspace '" + coreKeySpace + "' failed: " + e.getMessage());
			// create keyspace...
			session = createKeySpace(coreKeySpace, KeyspaceType.CORE);
		}
		if (session != null) {
			logger.finest("......keyspace conection status = OK");
		}
	
		return session;
	}

	/**
	 * Returns a cassandra session for a KeySpace. If no keySpace is defined, the
	 * core keyspace will be returned. If no keyspace with this given keyspace name
	 * exists, the method creates the keyspace and table schemas.
	 * 
	 * @throws ImixsArchiveException
	 */
	public Session getArchiveSession(String keySpace) throws ImixsArchiveException {
	
		if (keySpace == null || keySpace.isEmpty()) {
			throw new ImixsArchiveException(ImixsArchiveException.INVALID_KEYSPACE, "missing keyspace");
		}
	
		Cluster cluster = getCluster();
		// try to open keySpace
		logger.finest("......conecting keyspace '" + keySpace + "'...");
	
		Session session = null;
		try {
			session = cluster.connect(keySpace);
		} catch (InvalidQueryException e) {
			logger.warning("......conecting keyspace '" + keySpace + "' failed: " + e.getMessage());
			// create keyspace...
			session = createKeySpace(keySpace, KeyspaceType.ARCHIVE);
		}
		if (session != null) {
			logger.finest("......keyspace conection status = OK");
		}
		return session;
	}

	public Cluster getCluster() {
		String contactPoint = propertyService.getProperties().getProperty(PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT);
		logger.finest("......cluster conecting...");
		Cluster cluster = Cluster.builder().addContactPoint(contactPoint).build();
		cluster.init();

		logger.finest("......cluster conection status = OK");
		return cluster;

	}

	/**
	 * This method creates a cassandra keySpace.
	 * <p>
	 * Depending on the KeyspaceType, the method creates the core table schema to
	 * store configurations, or the extended Archive table schcema which is used to
	 * store imixs documents.
	 * 
	 * @param cluster
	 */
	protected Session createKeySpace(String keySpace, KeyspaceType type) {
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
			switch (type) {
			case ARCHIVE:
				createArchiveTableSchema(session);
				break;
			case CORE:
				createConfigurationTableSchema(session);
				break;
			}
		}

		return session;
	}

	/**
	 * This helper method creates the ImixsArchive document table schema if not yet
	 * exists
	 */
	protected void createArchiveTableSchema(Session session) {

		logger.info(TABLE_SCHEMA_SNAPSHOTS);
		session.execute(TABLE_SCHEMA_SNAPSHOTS);

		logger.info(TABLE_SCHEMA_SNAPSHOTS_BY_UNIQUEID);
		session.execute(TABLE_SCHEMA_SNAPSHOTS_BY_UNIQUEID);

		logger.info(TABLE_SCHEMA_SNAPSHOTS_BY_MODIFIED);
		session.execute(TABLE_SCHEMA_SNAPSHOTS_BY_MODIFIED);

	}

	/**
	 * This helper method creates the ImixsArchive configuration table schema if not
	 * yet exists
	 */
	protected void createConfigurationTableSchema(Session session) {
		logger.info(TABLE_SCHEMA_CONFIGURATION);
		session.execute(TABLE_SCHEMA_CONFIGURATION);
	}

}
