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

package org.imixs.archive;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.imixs.workflow.ItemCollection;

/**
 * The Archive Service is a JAX-RS service interface to the imixs-archive
 * 
 * @author rsoika
 * 
 */
@Path("/archive")
@Produces({ MediaType.TEXT_HTML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, MediaType.TEXT_XML })
@Stateless
public class ArchiveService {

	@javax.ws.rs.core.Context
	private static HttpServletRequest servletRequest;

	private static Logger logger = Logger.getLogger(ArchiveService.class.getName());

	/**
	 * Returns a file attachment located in the property $file of the specified
	 * workitem
	 * 
	 * The file name will be encoded. With a URLDecode the filename is decoded
	 * in different formats and searched in the file list. This is not a nice
	 * solution.
	 * 
	 * @param uniqueid
	 * @return
	 */
	@GET
	@Path("/{uniqueid}/{file}")
	public Response getFile(@PathParam("uniqueid") String uniqueid, @PathParam("file") @Encoded String file,
			@Context UriInfo uriInfo, @QueryParam("contenttype") String contentType) {

		ItemCollection workItem;
		// try {
		try {
			String fileNameUTF8 = URLDecoder.decode(file, "UTF-8");

			String fileNameISO = URLDecoder.decode(file, "ISO-8859-1");

			
				
			
			java.nio.file.Path path = Paths.get("/archive/"+uniqueid + "/"+file);

			if (path != null) {
				InputStream inStream = Files.newInputStream(path);

				// Set content type in order of the contentType stored
				// in the $file attribute
				Response.ResponseBuilder builder = Response.ok(inStream, contentType);

				return builder.build();

			} else {
				logger.warning("WorklfowRestService unable to open file: '" + file + "' in workitem '" + uniqueid
						+ "' - error: Workitem not found!");
				// workitem not found
				return Response.status(Response.Status.NOT_FOUND).build();
			}
		} catch (UnsupportedEncodingException e) {

			logger.severe("WorklfowRestService unable to open file: '" + file + "' in workitem '" + uniqueid
					+ "' - error: " + e.getMessage());
			e.printStackTrace();

		} catch (IOException e) {
			logger.severe("WorklfowRestService unable to open file: '" + file + "' in workitem '" + uniqueid
					+ "' - error: " + e.getMessage());
			e.printStackTrace();
		}

		logger.severe("WorklfowRestService unable to open file: '" + file + "' in workitem '" + uniqueid + "'");
		return Response.status(Response.Status.NOT_FOUND).build();

	}
}
