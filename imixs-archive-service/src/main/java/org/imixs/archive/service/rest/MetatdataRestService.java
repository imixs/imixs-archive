/*******************************************************************************
 *  Imixs Workflow 
 *  Copyright (C) 2001, 2011 Imixs Software Solutions GmbH,  
 *  http://www.imixs.com
 *  
 *  This program is free software; you can redistribute it and/or 
 *  modify it under the terms of the GNU General Public License 
 *  as published by the Free Software Foundation; either version 2 
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful, 
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 *  General Public License for more details.
 *  
 *  You can receive a copy of the GNU General Public
 *  License at http://www.gnu.org/licenses/gpl.html
 *  
 *  Project: 
 *  	http://www.imixs.org
 *  	http://java.net/projects/imixs-workflow
 *  
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika - Software Developer
 *******************************************************************************/

package org.imixs.archive.service.rest;

import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.SnapshotService;
import org.imixs.archive.service.scheduler.SyncService;
import org.imixs.workflow.ItemCollection;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

/**
 * The MetatdataRestService is used to read and update the current syncpoint
 * stored in the metdata object.
 * 
 * @author rsoika
 * 
 */
@Path("/metadata")
@Produces({ MediaType.APPLICATION_JSON, MediaType.TEXT_HTML, MediaType.TEXT_XML })
@Stateless
public class MetatdataRestService {

	@EJB
	ClusterService clusterService;
	
	@EJB
	SnapshotService documentService;

	@javax.ws.rs.core.Context
	private static HttpServletRequest servletRequest;

	private static Logger logger = Logger.getLogger(MetatdataRestService.class.getName());

	/**
	 * Ping test
	 * 
	 * @return time
	 * @throws Exception
	 */
	@GET
	@Path("/")
	public String getSyncpoint() {
		Session session = null;
		Cluster cluster = null;
		try {
			logger.info("...read metadata...");
			cluster = clusterService.getCluster();
			session = clusterService.getArchiveSession(cluster);
			
			ItemCollection metadata=documentService.loadMetadata(session);
			
			
			String result="syncpoint=" + metadata.getItemValueString(SyncService.ITEM_SYNCPOINT);
			result=result+"\ncount="+ metadata.getItemValueString(SyncService.ITEM_SYNCCOUNT);

			return result;

		} catch (Exception e) {
			logger.warning("...Failed to initalize imixsarchive keyspace: " + e.getMessage());
			return "NO DATA";

		} finally {
			// close session and cluster object
			if (session != null) {
				session.close();
			}
			if (cluster != null) {
				cluster.close();
			}
		}

	}

}
