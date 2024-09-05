package org.imixs.workflow.archive.cassandra;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

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
		assertTrue(regexPattern.matcher(value).find());

		value = "snapshot-processx";
		assertFalse(regexPattern.matcher(value).find());

		value = "process";
		assertTrue(regexPattern.matcher(value).find());

	}

	/**
	 * Test create workitem by factory
	 */
	@Test
	public void testTypeMulti() {
		regex = "(process$)|(space$)";
		Pattern regexPattern = Pattern.compile(regex);

		value = "snapshot-process";
		assertTrue(regexPattern.matcher(value).find());

		value = "snapshot-processx";
		assertFalse(regexPattern.matcher(value).find());

		value = "process";
		assertTrue(regexPattern.matcher(value).find());

		value = "snapshot-space";
		assertTrue(regexPattern.matcher(value).find());

	}

}
