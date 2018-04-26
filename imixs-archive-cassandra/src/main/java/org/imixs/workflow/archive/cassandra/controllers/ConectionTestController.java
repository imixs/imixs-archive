package org.imixs.workflow.archive.cassandra.controllers;

import java.util.logging.Logger;

import javax.ejb.EJB;
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
@Path("test")
public class ConectionTestController {
	private static Logger logger = Logger.getLogger(ConectionTestController.class.getName());

	@EJB
	ClusterService clusterService;

	@GET
	public String sayHello() {

		logger.info("connecting...");
		clusterService.connect();

		logger.info("connection ok");

		return "/index.jsf";
	}
}