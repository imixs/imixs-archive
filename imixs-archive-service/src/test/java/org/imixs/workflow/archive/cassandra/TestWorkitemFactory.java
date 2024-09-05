package org.imixs.workflow.archive.cassandra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.junit.jupiter.api.Test;

/**
 * Test class for the WorkitemFactory
 * 
 * @author rsoika
 * 
 */
public class TestWorkitemFactory {

	/**
	 * Test create workitem by factory
	 */
	@Test
	public void testSimple() {
		ItemCollection workitem = WorkitemFactory.createWorkitem();
		assertNotNull(workitem);
		// validate data
		assertEquals(47, workitem.getItemValueInteger("_some_amount"));
		assertEquals("Hello World", workitem.getItemValueString("_some_text"));
	}

	/**
	 * Test create workitem by factory with file
	 */
	@Test
	public void testSimpleFile() {
		ItemCollection workitem = WorkitemFactory.createWorkitem();

		WorkitemFactory.addFile(workitem, "test", 1);
		assertNotNull(workitem);

		FileData fileData = workitem.getFileData("test");

		assertNotNull(fileData);
		assertEquals(1024, fileData.getContent().length);
	}

}
