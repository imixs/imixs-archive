package org.imixs.workflow.archive.cassandra.controllers;

import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.mvc.annotation.Controller;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.imixs.workflow.archive.cassandra.services.ClusterService;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

/**
 * The ArchiveController is used to create, show und update archive
 * configurations
 * 
 * @author rsoika
 *
 */
@Controller
@Path("archive")
public class ArchiveController {
	private static Logger logger = Logger.getLogger(ArchiveController.class.getName());

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

	/**
	 * Save the archive configuration. If the corresponding keyspace does not exist,
	 * the method creates and initalizes the keyspace.
	 * 
	 * @param keyspace
	 * @param url
	 * @param session 
	 * @param cluster 
	 * @return
	 */
	@POST
	@Path("/")
	public String saveTeam(@FormParam("keyspace") String keyspace, @FormParam("url") String url) {

		logger.info("creating new Keyspace " + keyspace + " ....");
		
		
		Cluster cluster = clusterService.getCluster();
		Session session = clusterService.createKeSpace(cluster, keyspace);
		
		logger.info("creating table schemas for keyspace '" + keyspace + "' ....");
		clusterService.createTableSchema(session);
		
		
		
		return "redirect:archive";
	}

}