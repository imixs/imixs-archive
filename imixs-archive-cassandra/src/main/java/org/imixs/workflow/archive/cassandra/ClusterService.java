package org.imixs.workflow.archive.cassandra;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;

import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.engine.PropertyService;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

/**
 * Simple Test class to check a local Apache Cassandra Cluster
 * 
 * @author rsoika
 * 
 */
@Stateless
public class ClusterService {

	public static String PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT = "archive.cluster.contactpoint";
	public static String PROPERTY_ARCHIVE_CLUSTER_KEYSPACE = "archive.cluster.keyspace";

	@Resource
	SessionContext ejbCtx;

	@EJB
	DocumentService documentService;

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

		Cluster cluster = Cluster.builder().addContactPoint(contactPoint).build();
		cluster.init();
		Session session = cluster.connect(keySpace);
		return session;
	}
}
