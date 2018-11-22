package org.imixs.workflow.archive.cassandra;

import org.imixs.archive.service.cassandra.ClusterService;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test the regex matcher for a imixs snapshot id
 * 
 * 
 * @author rsoika
 * 
 */
public class TestRegex {

	/**
	 * This various snaphsot id patterns
	 * 
	 */
	@Test
	public void testSnapshotPattern() {

		Assert.assertTrue(ClusterService.isSnapshotID("2de78aec-6f14-4345-8acf-dd37ae84875d-1530315900599"));
		Assert.assertTrue(ClusterService.isSnapshotID("2de78aec-6f14-4345-8acf-dd37ae84875d-01530315900599"));
		// without snapshot digits
		Assert.assertFalse(ClusterService.isSnapshotID("2de78aec-6f14-4345-8acf-dd37ae84875d"));

	}
	
	
	@Test
	public void testKeyspaceNamePattern() {
		// validate keyspace pattern
		
		String keyspace="abc";	
		Assert.assertTrue(keyspace.matches(ClusterService.REGEX_KEYSPACE_NAME));
		
		keyspace="345";
		Assert.assertFalse(keyspace.matches(ClusterService.REGEX_KEYSPACE_NAME));
		
		keyspace="asdf_asdf";
		Assert.assertTrue(keyspace.matches(ClusterService.REGEX_KEYSPACE_NAME));
		
		keyspace="asdf-asdf";
		Assert.assertFalse(keyspace.matches(ClusterService.REGEX_KEYSPACE_NAME));
	}
	
	
	
	@Test
	public void testSemicolonOrComma() {
		// validate keyspace pattern
		
		String sDefinition="hour=5;minute=1,day=3";	
		String calendarConfiguation[] = sDefinition.split("(\\r?\\n)|(;)|(,)");
		Assert.assertEquals(3, calendarConfiguation.length);
		
	}
	
}
