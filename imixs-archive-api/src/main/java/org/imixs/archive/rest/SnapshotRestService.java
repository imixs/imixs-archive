/*******************************************************************************
 * Imixs-Workflow Archive 
 * Copyright (C) 2001-2018 Imixs Software Solutions GmbH,  
 * http://www.imixs.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * 
 *
 * Project: 
 * 	http://www.imixs.org
 *
 * Contributors:  
 * 	Imixs Software Solutions GmbH - initial API and implementation
 * 	Ralph Soika - Software Developer
 *******************************************************************************/

package org.imixs.archive.rest;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.inject.Named;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.imixs.archive.core.SnapshotService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.jaxrs.WorkflowRestService;
import org.imixs.workflow.xml.XMLDataCollection;
import org.imixs.workflow.xml.XMLDataCollectionAdapter;

/**
 * The SnapshotRestService is a wrapper for the WorkflowRestService and provides
 * a method to get a file content based on the $uniqueid of the origin workitem.
 * 
 * @author rsoika
 */
@Named("snapshotService")
@RequestScoped
@Path("/snapshot")
@Produces({ MediaType.TEXT_HTML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, MediaType.TEXT_XML })
public class SnapshotRestService implements Serializable {

	private static final long serialVersionUID = 1L;

	@EJB
	DocumentService documentService;

	@EJB
	WorkflowRestService workflowRestService;

	private static Logger logger = Logger.getLogger(SnapshotRestService.class.getName());

	/**
	 * This method wraps the WorkflowRestService method 'getWorkItemFile' and
	 * verifies the target id which can be either a $snapshotid or the deprecated
	 * $blobWorkitemid.
	 * 
	 * Finally the method calls the origin method getWorkItemFile
	 * 
	 * @param uniqueid
	 * @param file     - file name
	 * @return byte stream with file data.
	 */
	@GET
	@Path("/{uniqueid : ([0-9a-f]{8}-.*|[0-9a-f]{11}-.*)}/file/{file}")
	public Response getWorkItemFile(@PathParam("uniqueid") String uniqueid, @PathParam("file") @Encoded String file,
			@Context UriInfo uriInfo) {

		ItemCollection workItem;
		String sTargetID = uniqueid;
		// load workitem
		workItem = documentService.load(uniqueid);
		// test if we have a $snapshotid
		if (workItem != null && workItem.hasItem("$snapshotid")) {
			sTargetID = workItem.getItemValueString("$snapshotid");
		} else {
			// support deprecated blobworkitem....
			if (workItem != null && workItem.hasItem("$blobworkitem")) {
				sTargetID = workItem.getItemValueString("$blobworkitem");
			}
		}
		return workflowRestService.getWorkItemFile(sTargetID, file, uriInfo);
	}

	/**
	 * This method retunrs the next workitem from a given syncpoint. A syncpoint is
	 * defined in milliseconds after January 1, 1970 00:00:00 GMT.
	 * 
	 * The syncpoint is compared to the modified date. If not data is found, the
	 * method returns null.
	 * 
	 * @param syncpoint
	 * @return
	 */
	@GET
	@Path("/syncpoint/{syncpoint}")
	public XMLDataCollection getDocumentBySyncPoint(@PathParam("syncpoint") long lSyncpoint) {

		Date syncpoint = new Date(lSyncpoint);

		// ISO date time format: '2016-08-25 01:23:46.0',
		DateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		String query = "SELECT document FROM Document AS document ";
		query += " WHERE document.modified > '" + isoFormat.format(syncpoint) + "'";
		query += " AND document.type LIKE '" + SnapshotService.TYPE_PRAFIX + "%' ";
		query += " ORDER BY document.modified ASC";
		logger.finest("......QUERY=" + query);

		List<ItemCollection> result = documentService.getDocumentsByQuery(query, 1);

		if (result == null || result.size() == 0) {
			return null;
		}
		ItemCollection document = result.get(0);
		return XMLDataCollectionAdapter.getDataCollection(document);

	}

	
	
	
	/**
	 * Ping service
	 * @param lSyncpoint
	 * @return
	 */
	@GET
	@Path("/ping")
	public String ping() {
		return "ping = " + System.currentTimeMillis();
	}
		
}
