package org.imixs.workflow.archive.cassandra;

import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test class for testing the regex options for restore
 * 
 * @author rsoika
 * 
 */
public class TestRegexOptions {
	String regex = null;
	String value = null;;

	/**
	 * Test create workitem by factory
	 */
	@Test
	public void testType() {
		regex = "(process$)";
		Pattern regexPattern = Pattern.compile(regex);

		value = "snapshot-process";
		Assert.assertTrue(regexPattern.matcher(value).find());

		value = "snapshot-processx";
		Assert.assertFalse(regexPattern.matcher(value).find());

		value = "process";
		Assert.assertTrue(regexPattern.matcher(value).find());

	}

	/**
	 * Test create workitem by factory
	 */
	@Test
	public void testTypeMulti() {
		regex = "(process$)|(space$)";
		Pattern regexPattern = Pattern.compile(regex);

		value = "snapshot-process";
		Assert.assertTrue(regexPattern.matcher(value).find());

		value = "snapshot-processx";
		Assert.assertFalse(regexPattern.matcher(value).find());

		value = "process";
		Assert.assertTrue(regexPattern.matcher(value).find());
		
		
		value = "snapshot-space";
		Assert.assertTrue(regexPattern.matcher(value).find());


	}

}
