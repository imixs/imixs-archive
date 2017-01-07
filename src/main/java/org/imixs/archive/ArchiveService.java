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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

/**
 * The Archive Service is a JAX-RS service interface to the imixs-archive
 * 
 * @author rsoika
 * 
 */

@javax.ws.rs.Path("/archive")
@Produces({ MediaType.TEXT_HTML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, MediaType.TEXT_XML })
@Stateless
public class ArchiveService  {

	@javax.ws.rs.core.Context
	private static HttpServletRequest servletRequest;
	private static Logger logger = Logger.getLogger(ArchiveService.class.getName());

	
	@GET
	@Produces("text/html")
	@javax.ws.rs.Path("/help")
	public StreamingOutput getHelpHTML() {

		return new StreamingOutput() {
			public void write(OutputStream out) throws IOException, WebApplicationException {

				out.write("<html><head>".getBytes());
				out.write("<style>".getBytes());
				out.write("table {padding:0px;width: 100%;margin-left: -2px;margin-right: -2px;}".getBytes());
				out.write(
						"body,td,select,input,li {font-family: Verdana, Helvetica, Arial, sans-serif;font-size: 13px;}"
								.getBytes());
				out.write("table th {color: white;background-color: #bbb;text-align: left;font-weight: bold;}"
						.getBytes());

				out.write("table th,table td {font-size: 12px;}".getBytes());

				out.write("table tr.a {background-color: #ddd;}".getBytes());

				out.write("table tr.b {background-color: #eee;}".getBytes());

				out.write("</style>".getBytes());
				out.write("</head><body>".getBytes());

				// body
				out.write("<h1>Imixs-Archive Rest API 1.0</h1>".getBytes());
				out.write(
						"<p>See the <a href=\"https://github.com/imixs/imixs-archive\" target=\"_blank\">Imixs-Archive REST Service API</a> for more information about this Service.</p>"
								.getBytes());

				// end
				out.write("</body></html>".getBytes());
			}
		};

	}

	/**
	 * Returns a file stored in hadoop. The method expect the uniqueid of the
	 * file and the checksum. The later will be verified before the file is
	 * returned. If the checksum did not match, an error is returned.
	 * 
	 * @param uniqueid
	 *            - unique identifier returned form putFile()
	 * @param checksum
	 *            - checksum returned from putFile()
	 * @return
	 * @throws IOException
	 */
	@GET
	@javax.ws.rs.Path("/{uniqueid}/{checksum}/{file}")
	public void getFile(InputStream is, @PathParam("uniqueid") String uniqueid, @PathParam("uniqueid") String checksum,
			@PathParam("file") @Encoded String file, @Context UriInfo uriInfo,
			@QueryParam("contenttype") String contentType) throws IOException {

	}

	/**
	 * Returns a file stored in hadoop. The method expect the uniqueid of the
	 * file and the checksum. The later will be verified before the file is
	 * returned. If the checksum did not match, an error is returned.
	 * 
	 * @param uniqueid
	 *            - unique identifier returned form putFile()
	 * @param checksum
	 *            - checksum returned from putFile()
	 * @return
	 * @throws IOException
	 * @throws NoSuchAlgorithmException 
	 */
	@PUT
	@javax.ws.rs.Path("/{uniqueid}/{file}")
	public void putFile(InputStream is, @PathParam("uniqueid") String uniqueid,
			@PathParam("file") @Encoded String fileName, @Context UriInfo uriInfo,
			@QueryParam("contenttype") String contentType) throws IOException, NoSuchAlgorithmException {

		logger.info("put bytes....");
		byte[] data = readFromStream(is);

		if (data != null) {
			logger.info(data.length + " bytes receifed, writing bytes....");
			logger.info("uniqueid=" + uniqueid + " file=" + fileName);

			
		}
		
		String checksum=ChecksumGenerator.generateMD5(data);

		logger.info("Write Bytes successfull, checksum="+checksum);

	}

	private byte[] readFromStream(InputStream stream) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		byte[] buffer = new byte[1000];
		int wasRead = 0;
		do {
			wasRead = stream.read(buffer);
			if (wasRead > 0) {
				baos.write(buffer, 0, wasRead);
			}
		} while (wasRead > -1);
		return baos.toByteArray();
	}

	

}
