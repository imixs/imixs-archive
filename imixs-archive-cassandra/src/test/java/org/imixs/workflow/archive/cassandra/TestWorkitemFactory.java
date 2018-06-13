package org.imixs.workflow.archive.cassandra;

import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.junit.Assert;
import org.junit.Test;

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
		ItemCollection workitem=WorkitemFactory.createWorkitem();
		Assert.assertNotNull(workitem);
		// validate data
		Assert.assertEquals(47, workitem.getItemValueInteger("_some_amount"));
		Assert.assertEquals("Hello World", workitem.getItemValueString("_some_text"));
	}
	
	
	/**
	 * Test create workitem by factory with file
	 */
	@Test
	public void testSimpleFile() {
		ItemCollection workitem=WorkitemFactory.createWorkitem();
		
		WorkitemFactory.addFile(workitem, "test",1);
		Assert.assertNotNull(workitem);
		
		
		FileData fileData=workitem.getFileData("test");
		
		Assert.assertNotNull(fileData);
		Assert.assertEquals(1024, fileData.getContent().length);
	}
	
	
	
}
