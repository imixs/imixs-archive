package org.imixs.workflow.archive.cassandra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;

/**
 * Simple Test class to check a local Apache Cassandra Cluster via the native
 * api
 * 
 * 
 * @author rsoika
 * 
 */
public class TestClusterNative {

	public static String CONNACT_POINT = "localhost";
	public static String KEYSPACE = "imixs_dev";

	/**
	 * Test the local connection
	 */
	@Disabled
	@Test
	public void testCluster() {

		Cluster cluster = Cluster.builder().addContactPoint(CONNACT_POINT).build();

		assertNotNull(cluster);

		cluster.init();

		Session session = cluster.connect();

		assertNotNull(session);
	}

	/**
	 * Test createKeyspace if not exists...
	 */
	@Disabled
	@Test
	public void createKeyspace() {
		Cluster cluster = Cluster.builder().addContactPoint(CONNACT_POINT).build();
		Session session = cluster.connect();

		String statement = "CREATE KEYSPACE IF NOT EXISTS "
				+ "imixs_dev WITH replication = {'class':'SimpleStrategy','replication_factor':1};";

		session.execute(statement);

	}

	/**
	 * Test createKeyspace if not exists...
	 */
	@Disabled
	@Test
	public void createTable() {
		Session session = connect();
		// String statement = "CREATE TABLE IF NOT EXISTS imixs_test (id uuid PRIMARY
		// KEY, title text, subject text);";
		String statement = "CREATE TABLE IF NOT EXISTS document (id text PRIMARY KEY, type text, created timestamp, modified timestamp, data text);";

		session.execute(statement);

		ResultSet result = session.execute("SELECT * FROM imixs_dev.document;");

		List<String> columnNames = result.getColumnDefinitions().asList().stream().map(cl -> cl.getName())
				.collect(Collectors.toList());

		assertEquals(columnNames.size(), 5);
		assertTrue(columnNames.contains("id"));
		assertTrue(columnNames.contains("type"));
		assertTrue(columnNames.contains("data"));
	}

	/**
	 * Helper method to get a sseison for imixs_dev
	 */
	private Session connect() {
		Cluster cluster = Cluster.builder().addContactPoint(CONNACT_POINT).build();
		cluster.init();
		Session session = cluster.connect(KEYSPACE);
		assertNotNull(session);
		return session;
	}
}
