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

	

}