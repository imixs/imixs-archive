package org.imixs.workflow.archive.cassandra;

import java.util.Date;
import java.util.Random;

import org.imixs.workflow.ItemCollection;

/**
 * Helper Class to create test workitems with dummy file attachments.
 * 
 * @author rsoika
 *
 */
public class WorkitemFactory {

	
	
	public static ItemCollection createWorkitem() {
		
		ItemCollection result=new ItemCollection();
		result.replaceItemValue("$created",new Date());
		result.replaceItemValue("$modified",new Date());
		
		result.replaceItemValue("_some_text","Hello World");
		result.replaceItemValue("_some_amount",47);
		
		return result;
	}
	
	
	/**
	 * adds a dummy file to a workitem 
	 * @param name
	 * @param sizeKB
	 * @return
	 */
	public static ItemCollection addFile(ItemCollection workitem,String fileName, int sizeKB) {
		
		byte[] temp = new byte[sizeKB*1024];
		
		// fill with random
		Random r = new Random();
		r.nextBytes(temp);
		workitem.addFile(temp, fileName, "application/unknown");
		return workitem;
	}
		
}
