package org.imixs.workflow.archive.cassandra;


import org.junit.Assert;
import org.junit.Test;

import com.datastax.driver.core.Cluster;

/**
 * Simple Test class to check a local Apache Cassandra Cluster
 * 
 * @author rsoika
 * 
 */
public class ClusterTest {

	public static String CONNACT_POINT = "localhost";


	/**
	 * Test the local connection
	 */
	@Test
	public void testCluster() {

		
		Cluster cluster=Cluster.builder().addContactPoint(CONNACT_POINT).build();
		
		Assert.assertNotNull(cluster);
		
		cluster.init();

	}

	
	
	
	
	
	
	
}
