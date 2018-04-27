package org.imixs.workflow.archive.cassandra.controllers;

import java.util.logging.Logger;

import javax.inject.Named;
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
@Named
@Path("/")
public class WelcomeController {
	private static Logger logger = Logger.getLogger(WelcomeController.class.getName());

	String id;
	
	
	
	
	
	public String getId() {
		logger.info("Jemand will meine ID wissen...");
		return ""+System.currentTimeMillis();
	}

	public void setId(String id) {
		this.id = id;
	}

	@GET
	public String home() {
		logger.info("home..");
		return "index.xhtml";
	}

	/*
	@Path("/1")
	@GET
	public String home1() {

		logger.info("home1..");

		return "index.jsp";
	}

	@Path("/2")
	@GET
	public String home2() {

		logger.info("home2..");

		return "index.xhtml";
	}

	@Path("/3")
	@GET
	public String home3() {

		logger.info("home3..");

		return "index.jsf";
	}
	*/
}