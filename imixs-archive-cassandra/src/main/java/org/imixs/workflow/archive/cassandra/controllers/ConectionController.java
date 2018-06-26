package org.imixs.workflow.archive.cassandra.controllers;

import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.inject.Named;
import javax.mvc.annotation.Controller;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.imixs.workflow.archive.cassandra.services.ClusterService;

/**
 * Test the cassandra cluster connection
 * 
 * @author rsoika
 *
 */
@Controller
@Path("connection")
@Named
public class ConectionController {
	private static Logger logger = Logger.getLogger(ConectionController.class.getName());

	@EJB
	ClusterService clusterService;

	
	
	/**
	 * show connections
	 * 
	 * @return
	 */
	@Path("/")
	@GET
	public String showConfigs() {

			
		
		logger.info("show config...");

		return "archive_list.xhtml";
	}
	
	/**
	 * Create a new key-space
	 * 
	 * @return
	 */
	@Path("/action/create")
	@GET
	public String createKeySpace() {

			
		
		logger.info("create archive config...");

		return "archive_config.xhtml";
	}
}