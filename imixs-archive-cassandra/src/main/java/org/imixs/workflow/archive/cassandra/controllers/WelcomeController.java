package org.imixs.workflow.archive.cassandra.controllers;

import javax.mvc.annotation.Controller;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * Test the cassandra cluster connection
 * 
 * @author rsoika
 *
 */
@Controller
@Path("/")
public class WelcomeController {
	

	@GET
	public String home() {
		return "index.xhtml";
	}

	
}