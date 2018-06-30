package org.imixs.workflow.archive.cassandra;

import org.imixs.workflow.archive.cassandra.services.ClusterService;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test the regex matcher for a imixs snapshot id
 * 
 * 
 * @author rsoika
 * 
 */
public class TestRegexSnapshotID {

	/**
	 * This various snaphsot id patterns
	 * 
	 */
	@Test
	public void test() {

		Assert.assertTrue(ClusterService.isSnapshotID("2de78aec-6f14-4345-8acf-dd37ae84875d-1530315900599"));
		Assert.assertTrue(ClusterService.isSnapshotID("2de78aec-6f14-4345-8acf-dd37ae84875d-01530315900599"));
		// without snapshot digits
		Assert.assertFalse(ClusterService.isSnapshotID("2de78aec-6f14-4345-8acf-dd37ae84875d"));

	}

}
