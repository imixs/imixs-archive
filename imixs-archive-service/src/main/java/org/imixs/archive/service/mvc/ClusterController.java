package org.imixs.archive.service.mvc;

import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.inject.Named;
import javax.mvc.annotation.Controller;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.imixs.archive.service.cassandra.ClusterService;

/**
 * The cluster controller provides action methods to setup the cluster configuration
 *  
 * 
 * @author rsoika
 *
 */
@Controller
@Path("cluster")
@Named
public class ClusterController {
	private static Logger logger = Logger.getLogger(ClusterController.class.getName());

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

		logger.finest("......show cluster config...");

		return "cluster.xhtml";
	}

}