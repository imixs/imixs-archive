package org.imixs.workflow.archive.cassandra.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Named;
import javax.mvc.annotation.Controller;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.imixs.workflow.ItemCollection;

/**
 * Controller to manage active imixs-workflow instances.
 * 
 * @author rsoika
 *
 */
@Controller
@Path("instances")
@Named
public class WorkflowInstanceController {
	private static Logger logger = Logger.getLogger(WorkflowInstanceController.class.getName());

	/**
	 * Returns a list of all existing instances
	 * 
	 * @return
	 */
	@GET
	public String showInstances() {

		logger.info("connecting...");
		// clusterService.connect();

		logger.info("connection ok2");

		return "instances.xhtml";
	}

	public List<ItemCollection> getInstances() {
		List<ItemCollection> result = new ArrayList<ItemCollection>();
		
		
		ItemCollection col=new ItemCollection();
		col.replaceItemValue("txtname","Imixs-Office-Workflow");
		result.add(col);
		return result;
		
	}
}