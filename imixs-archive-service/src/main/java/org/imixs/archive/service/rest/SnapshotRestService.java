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
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.SnapshotService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.xml.XMLDataCollection;
import org.imixs.workflow.xml.XMLDataCollectionAdapter;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

/**
 * The SnapshotRestService is used to inspect the data of a snapshot.
 * 
 * @author rsoika
 * 
 */
@Path("/snapshot")
@Produces({ MediaType.APPLICATION_JSON, MediaType.TEXT_HTML, MediaType.TEXT_XML })
@Stateless
public class SnapshotRestService {

	@EJB
	ClusterService clusterService;

	@EJB
	SnapshotService documentService;

	@javax.ws.rs.core.Context
	private static HttpServletRequest servletRequest;

	private static Logger logger = Logger.getLogger(SnapshotRestService.class.getName());

	/**
	 * Loads a snapshot from the archive.
	 * 
	 * @param id - snapshot id
	 * @return XMLDataCollection
	 */
	@GET
	@Path("/{snapshotid : ([0-9a-f]{8}-.*|[0-9a-f]{11}-.*)}")
	public XMLDataCollection getSnapshot(@PathParam("snapshotid") String id) {
		Session session = null;
		Cluster cluster = null;
		try {
			logger.info("...read snapshot...");
			cluster = clusterService.getCluster();
			session = clusterService.getArchiveSession(cluster);

			ItemCollection snapshot = documentService.loadSnapshot(id, session);

			return XMLDataCollectionAdapter.getDataCollection(snapshot);

		} catch (Exception e) {
			logger.warning("...Failed to initalize imixsarchive keyspace: " + e.getMessage());
			return null;

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
