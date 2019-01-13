package org.imixs.archive.service.cassandra;

import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.ejb.Stateless;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.scheduler.SyncService;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.InvalidQueryException;

/**
 * The ClusterService provides methods to persist the content of a Imixs
 * Document into a Cassandra keystore.
 * <p>
 * The service saves the content in XML format. The size of an XML
 * representation of a Imixs document is only slightly different in size from
 * the serialized map object. This is the reason why we do not store the
 * document map in a serialized object format.
 * <p>
 * The ClusterService creates a Core-KeySpace automatically which is used for
 * the internal management.
 * 
 * @author rsoika
 * 
 */
@Stateless
public class ClusterService {

	public static final String KEYSPACE_REGEX = "^[a-z_]*[^-]$";

	// mandatory environment settings
	public static final String ENV_ARCHIVE_CLUSTER_CONTACTPOINTS = "ARCHIVE_CLUSTER_CONTACTPOINTS";
	public static final String ENV_ARCHIVE_CLUSTER_KEYSPACE = "ARCHIVE_CLUSTER_KEYSPACE";

	// optional environment settings
	public static final String ENV_ARCHIVE_SCHEDULER_DEFINITION = "ARCHIVE_SCHEDULER_DEFINITION";
	public static final String ENV_ARCHIVE_CLUSTER_REPLICATION_FACTOR = "ARCHIVE_CLUSTER_REPLICATION_FACTOR";
	public static final String ENV_ARCHIVE_CLUSTER_REPLICATION_CLASS = "ARCHIVE_CLUSTER_REPLICATION_CLASS";

	// workflow rest service endpoint
	public static final String ENV_WORKFLOW_SERVICE_ENDPOINT = "WORKFLOW_SERVICE_ENDPOINT";
	public static final String ENV_WORKFLOW_SERVICE_USER = "WORKFLOW_SERVICE_USER";
	public static final String ENV_WORKFLOW_SERVICE_PASSWORD = "WORKFLOW_SERVICE_PASSWORD";
	public static final String ENV_WORKFLOW_SERVICE_AUTHMETHOD = "WORKFLOW_SERVICE_AUTHMETHOD";

	// archive table schemas
	public static final String TABLE_SCHEMA_SNAPSHOTS = "CREATE TABLE IF NOT EXISTS snapshots (snapshot text, data blob, PRIMARY KEY (snapshot))";
	public static final String TABLE_SCHEMA_SNAPSHOTS_BY_UNIQUEID = "CREATE TABLE IF NOT EXISTS snapshots_by_uniqueid (uniqueid text,snapshot text, PRIMARY KEY(uniqueid, snapshot));";
	public static final String TABLE_SCHEMA_SNAPSHOTS_BY_MODIFIED = "CREATE TABLE IF NOT EXISTS snapshots_by_modified (modified date,snapshot text,PRIMARY KEY(modified, snapshot));";

	public static final String REGEX_SNAPSHOTID = "([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}-[0-9]{13,15})";

	public static final String REGEX_KEYSPACE_NAME = "^[A-Z]{2}(?:[ ]?[0-9]){18,20}$";

	private static Logger logger = Logger.getLogger(ClusterService.class.getName());

	@EJB
	SyncService schedulerService;

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
	 * Returns a cassandra session for the archive KeySpace. The keyspace is defined
	 * by the environmetn variable ARCHIVE_CLUSTER_KEYSPACE. If no keyspace with
	 * this given keyspace name exists, the method creates the keyspace and table
	 * schemas.
	 * 
	 * @throws ArchiveException
	 */
	public Session getArchiveSession() throws ArchiveException {

		String keySpace = ClusterService.getEnv(ENV_ARCHIVE_CLUSTER_KEYSPACE, null);
		if (!isValidKeyspaceName(keySpace)) {
			throw new ArchiveException(ArchiveException.INVALID_KEYSPACE, "keyspace '" + keySpace + "' name invalid.");
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
			session = createKeySpace(keySpace);
		}
		if (session != null) {
			logger.finest("......keyspace conection status = OK");
		}
		return session;
	}

	/**
	 * This method creates a Cassandra Cluster object. The cluster is defined by
	 * ContactPoints provided in the environmetn variable
	 * 'ARCHIVE_CLUSTER_CONTACTPOINTS' or in the imixs.property
	 * 'archive.cluster.contactpoints'
	 * 
	 * @return Cassandra Cluster instacne
	 * @throws ArchiveException
	 */
	public Cluster getCluster() throws ArchiveException {
		String contactPoint = getEnv(ENV_ARCHIVE_CLUSTER_CONTACTPOINTS, null);

		if (contactPoint == null || contactPoint.isEmpty()) {
			throw new ArchiveException(ArchiveException.MISSING_CONTACTPOINT,
					"missing cluster contact points - verify configuration!");
		}

		logger.finest("......cluster conecting...");
		Cluster cluster = Cluster.builder().addContactPoint(contactPoint).build();
		cluster.init();

		logger.finest("......cluster conection status = OK");
		return cluster;

	}

	/**
	 * Returns a environment variable. An environment variable can be provided as a
	 * System property. If not available in the system variables than the method
	 * verifies the imixs.properties field.
	 * 
	 * @param env
	 *            - environment variable name
	 * @param defaultValue
	 *            - optional default value
	 * @return value
	 */
	public static String getEnv(String env, String defaultValue) {
		String result = System.getenv(env);
		if (result == null || result.isEmpty()) {
			result = defaultValue;
		}
		return result;
	}

	/**
	 * This method creates a cassandra keySpace.
	 * <p>
	 * Depending on the KeyspaceType, the method creates the core table schema to
	 * store configurations, or the extended Archive table schcema which is used to
	 * store imixs documents.
	 * 
	 * @param cluster
	 * @throws ArchiveException
	 */
	protected Session createKeySpace(String keySpace) throws ArchiveException {
		logger.info("......creating new keyspace '" + keySpace + "'...");

		Cluster cluster = getCluster();
		Session session = cluster.connect();

		String repFactor = getEnv(ENV_ARCHIVE_CLUSTER_REPLICATION_FACTOR, "1");
		String repClass = getEnv(ENV_ARCHIVE_CLUSTER_REPLICATION_CLASS, "SimpleStrategy");

		String statement = "CREATE KEYSPACE IF NOT EXISTS " + keySpace + " WITH replication = {'class': '" + repClass
				+ "', 'replication_factor': " + repFactor + "};";
		logger.info("......keyspace created...");
		session.execute(statement);
		// try to connect again to keyspace...
		session = cluster.connect(keySpace);
		if (session != null) {
			logger.info("......keyspace conection status = OK");

			// now create table schemas
			createArchiveTableSchema(session);
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
	 * Test if the keyspace name is valid.
	 * 
	 * @param keySpace
	 * @return
	 */
	public boolean isValidKeyspaceName(String keySpace) {
		if (keySpace == null || keySpace.isEmpty()) {
			return false;
		}

		return keySpace.matches(KEYSPACE_REGEX);

	}

}
