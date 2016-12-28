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
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;

import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.imixs.workflow.ItemCollection;

/**
 * The Archive Service is a JAX-RS service interface to the imixs-archive
 * 
 * @author rsoika
 * 
 */
@javax.ws.rs.Path("/archive")
@Produces({ MediaType.TEXT_HTML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, MediaType.TEXT_XML })
@Stateless
public class ArchiveService {

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
				out.write("<h1>Imixs-Archive Rest API</h1>".getBytes());
				out.write(
						"<p>See the <a href=\"https://github.com/imixs/imixs-archive\" target=\"_blank\">Imixs-Archive REST Service API</a> for more information about this Service.</p>"
								.getBytes());

				// end
				out.write("</body></html>".getBytes());
			}
		};

	}

	
	
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
	@javax.ws.rs.Path("/{uniqueid}/{file}")
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

















	@GET
	@javax.ws.rs.Path("/test")
	public void testWriteBytes() throws IOException {
		logger.info("Write Bytes ...");
		Configuration conf = new Configuration();

		conf.addResource(new org.apache.hadoop.fs.Path("/opt/hadoop/etc/hadoop/core-site.xml"));
		conf.addResource(new org.apache.hadoop.fs.Path("/opt/hadoop/etc/hadoop/hdfs-site.xml"));
		FSDataOutputStream out = null;
		try {

			// FileSystem fs = FileSystem.get(new URI("hdfs://localhost:54310"),
			// conf);
			// Path file = new Path("hdfs://localhost:54310/table.html");

			FileSystem fs = FileSystem.get(conf);
			org.apache.hadoop.fs.Path file = new org.apache.hadoop.fs.Path("byte-test-data2");

			if (fs.exists(file)) {
				System.err.println("Output already exists");
				fs.delete(file, true);
			}
			// Read from and write to new file
			out = fs.create(file);

			String s = "some test data....2";
			byte data[] = s.getBytes();

			out.write(data);
		} catch (Exception e) {
			System.out.println("Error while copying file " + e.getMessage());
		} finally {
			if (out != null) {
				out.close();
			}
		}

		logger.info("Write Bytes successfull");
	}




}
