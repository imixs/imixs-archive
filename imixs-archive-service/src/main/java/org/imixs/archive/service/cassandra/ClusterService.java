package org.imixs.archive.service.cassandra;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Singleton;
import javax.inject.Inject;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.service.ArchiveException;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Cluster.Builder;

import com.datastax.driver.core.RemoteEndpointAwareJdkSSLOptions;
import com.datastax.driver.core.SSLOptions;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.InvalidQueryException;
import com.datastax.driver.core.policies.DefaultRetryPolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;

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
@Singleton
public class ClusterService {

    public static final String KEYSPACE_REGEX = "^[a-z_]*[^-]$";

    // mandatory environment settings
    public static final String ENV_ARCHIVE_CLUSTER_CONTACTPOINTS = "ARCHIVE_CLUSTER_CONTACTPOINTS";
    public static final String ENV_ARCHIVE_CLUSTER_KEYSPACE = "ARCHIVE_CLUSTER_KEYSPACE";

    // optional environment settings
    public static final String ENV_ARCHIVE_CLUSTER_AUTH_USER = "ARCHIVE_CLUSTER_AUTH_USER";
    public static final String ENV_ARCHIVE_CLUSTER_AUTH_PASSWORD = "ARCHIVE_CLUSTER_AUTH_PASSWORD";
    public static final String ENV_ARCHIVE_CLUSTER_SSL = "ARCHIVE_CLUSTER_SSL";
    public static final String ENV_ARCHIVE_CLUSTER_SSL_TRUSTSTOREPATH = "ARCHIVE_CLUSTER_SSL_TRUSTSTOREPATH";
    public static final String ENV_ARCHIVE_CLUSTER_SSL_TRUSTSTOREPASSWORD = "ARCHIVE_CLUSTER_SSL_TRUSTSTOREPASSWORD";
    public static final String ENV_ARCHIVE_CLUSTER_SSL_KEYSTOREPATH = "ARCHIVE_CLUSTER_SSL_KEYSTOREPATH";
    public static final String ENV_ARCHIVE_CLUSTER_SSL_KEYSTOREPASSWORD = "ARCHIVE_CLUSTER_SSL_KEYSTOREPASSWORD";

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
    public static final String TABLE_SCHEMA_DOCUMENTS = "CREATE TABLE IF NOT EXISTS documents (md5 text, sort_id int, data_id text, PRIMARY KEY (md5,sort_id))";
    public static final String TABLE_SCHEMA_SNAPSHOTS_BY_DOCUMENT = "CREATE TABLE IF NOT EXISTS snapshots_by_document (md5 text,snapshot text, PRIMARY KEY(md5, snapshot));";
    public static final String TABLE_SCHEMA_DOCUMENTS_DATA = "CREATE TABLE IF NOT EXISTS documents_data (data_id text, data blob, PRIMARY KEY (data_id))";

    private static Logger logger = Logger.getLogger(ClusterService.class.getName());

    @Inject
    @ConfigProperty(name = ENV_ARCHIVE_CLUSTER_REPLICATION_FACTOR, defaultValue = "1")
    String repFactor;

    @Inject
    @ConfigProperty(name = ENV_ARCHIVE_CLUSTER_REPLICATION_CLASS, defaultValue = "SimpleStrategy")
    String repClass;

    @Inject
    @ConfigProperty(name = ENV_ARCHIVE_CLUSTER_CONTACTPOINTS, defaultValue = "")
    String contactPoint;

    @Inject
    @ConfigProperty(name = ENV_ARCHIVE_CLUSTER_KEYSPACE, defaultValue = "")
    String keySpace;

    @Inject
    @ConfigProperty(name = ENV_ARCHIVE_CLUSTER_AUTH_USER, defaultValue = "")
    String userid;

    @Inject
    @ConfigProperty(name = ENV_ARCHIVE_CLUSTER_AUTH_PASSWORD, defaultValue = "")
    String password;

    @Inject
    @ConfigProperty(name = ENV_ARCHIVE_CLUSTER_SSL, defaultValue = "false")
    boolean bUseSSL;

    @Inject
    @ConfigProperty(name = ENV_ARCHIVE_CLUSTER_SSL_TRUSTSTOREPATH, defaultValue = "")
    String truststorePath;

    @Inject
    @ConfigProperty(name = ENV_ARCHIVE_CLUSTER_SSL_TRUSTSTOREPASSWORD, defaultValue = "")
    String truststorePwd;

    @Inject
    @ConfigProperty(name = ENV_ARCHIVE_CLUSTER_SSL_KEYSTOREPATH, defaultValue = "")
    String keystorePath;

    @Inject
    @ConfigProperty(name = ENV_ARCHIVE_CLUSTER_SSL_KEYSTOREPASSWORD, defaultValue = "")
    String keystorePwd;

    private Cluster cluster;
    private Session session;

    @PostConstruct
    private void init() {
        try {
            cluster = initCluster();
            session = initArchiveSession();
        } catch (ArchiveException e) {
            logger.severe("Failed to init achive session!");
            e.printStackTrace();
        }
    }

    @PreDestroy
    private void tearDown() {
        // close session and cluster object
        if (session != null) {
            session.close();
        }
        if (cluster != null) {
            cluster.close();
        }
    }

    public Session getSession() {
        if (session == null) {
            init();
        }
        return session;
    }

    /**
     * Returns a cassandra session for the archive KeySpace. The keyspace is defined
     * by the environmetn variable ARCHIVE_CLUSTER_KEYSPACE. If no keyspace with
     * this given keyspace name exists, the method creates the keyspace and table
     * schemas.
     * 
     * @throws ArchiveException
     */
    private Session initArchiveSession() throws ArchiveException {

        if (!isValidKeyspaceName(keySpace)) {
            throw new ArchiveException(ArchiveException.INVALID_KEYSPACE, "keyspace '" + keySpace + "' name invalid.");
        }

        // try to open keySpace
        logger.info("......conecting keyspace '" + keySpace + "'...");
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
    protected Cluster initCluster() throws ArchiveException {
        // Boolean used to check if at least one host could be resolved
        boolean found = false;

        if (contactPoint == null || contactPoint.isEmpty()) {
            throw new ArchiveException(ArchiveException.MISSING_CONTACTPOINT,
                    "missing cluster contact points - verify configuration!");
        }

        logger.info("...cluster conecting: " + contactPoint);
        Builder builder = Cluster.builder();
        String[] hosts = contactPoint.split(",");
        for (String host : hosts) {
            try {
                builder.addContactPoint(host);
                // One host could be resolved
                found = true;
            } catch (IllegalArgumentException e) {
                // This host could not be resolved so we log a message and keep going
                logger.warning("...the host '" + host + "' is unknown so it will be ignored");
            }
        }
        if (!found) {
            // No host could be resolved so we throw an exception
            throw new IllegalStateException("All provided hosts are unknown - check cluster status and configuration!");
        }

        builder.withLoadBalancingPolicy(new RoundRobinPolicy());
        builder.withRetryPolicy(DefaultRetryPolicy.INSTANCE);

        // set optional credentials...
        if (!userid.isEmpty()) {
            builder = builder.withCredentials(userid, password);
        }

        // use SSL ?
        if (bUseSSL) {
            try {
                // create ssl options based on environment settings...
                SSLOptions options = createSSLOptions();
                logger.severe("......creating cluster session with SSL...");
                builder.withSSL(options);
            } catch (KeyManagementException | UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException
                    | CertificateException | IOException e) {
                logger.severe("Failed to connect withSSL: " + e.getMessage());
                e.printStackTrace();
            }
        }

        cluster = builder.build();
        cluster.init();
        logger.info("...cluster conection status = OK");

        return cluster;

    }

    /**
     * Helper Method to generate sslOpens object for cassandra dataStax driver
     * withSSL.
     * <p>
     * At least the truststorePath is needed to establish a one way SSL connection.
     * <p>
     * The keystorePath is only needed in case 'require_client_auth = true' is set
     * in the cassandra.yaml file
     * 
     * @return sslOptons
     * @throws KeyStoreException
     * @throws FileNotFoundException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     * @throws CertificateException
     * @throws UnrecoverableKeyException
     */
    private SSLOptions createSSLOptions() throws KeyStoreException, FileNotFoundException, IOException,
            NoSuchAlgorithmException, KeyManagementException, CertificateException, UnrecoverableKeyException {

        TrustManagerFactory tmf = null;
        if (truststorePath != null && !truststorePath.isEmpty()) {
            KeyStore tks = KeyStore.getInstance("JKS");
            tks.load((InputStream) new FileInputStream(new File(truststorePath)), truststorePwd.toCharArray());
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(tks);
        } else {
            logger.info("SSLOptions without truststore...");
        }

        KeyManagerFactory kmf = null;
        if (null != keystorePath && !keystorePath.isEmpty()) {
            KeyStore kks = KeyStore.getInstance("JKS");
            kks.load((InputStream) new FileInputStream(new File(keystorePath)), keystorePwd.toCharArray());
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(kks, keystorePwd.toCharArray());
        } else {
            logger.info("SSLOptions without keystore...");
        }

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf != null ? kmf.getKeyManagers() : null, tmf != null ? tmf.getTrustManagers() : null,
                new SecureRandom());
        RemoteEndpointAwareJdkSSLOptions sslOptions = RemoteEndpointAwareJdkSSLOptions.builder()
                .withSSLContext(sslContext).build();
        return sslOptions;
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
        Session session = cluster.connect();

        String statement = "CREATE KEYSPACE IF NOT EXISTS " + keySpace + " WITH replication = {'class': '" + repClass
                + "', 'replication_factor': " + repFactor + "};";

        session.execute(statement);
        logger.info("......keyspace created...");
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

        logger.info(TABLE_SCHEMA_DOCUMENTS);
        session.execute(TABLE_SCHEMA_DOCUMENTS);

        logger.info(TABLE_SCHEMA_SNAPSHOTS_BY_DOCUMENT);
        session.execute(TABLE_SCHEMA_SNAPSHOTS_BY_DOCUMENT);

        logger.info(TABLE_SCHEMA_DOCUMENTS_DATA);
        session.execute(TABLE_SCHEMA_DOCUMENTS_DATA);

    }

}
