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

package org.imixs.archive.service.api;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.xml.XMLDataCollectionAdapter;
import org.imixs.workflow.xml.XMLDocumentAdapter;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

/**
 * The ArchiveRestService provides a Rest API to be used by external clients.
 * <p>
 * The API provides methods to read and write snapshot data into the cassandra
 * cluster.
 * 
 * @author rsoika
 * 
 */
@Path("/archive")
@Produces({ MediaType.TEXT_HTML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, MediaType.TEXT_XML })
@Stateless
public class ArchiveRestService {

    @Inject
    ClusterService clusterService;

    @Inject
    DataService dataService;

    @javax.ws.rs.core.Context
    private HttpServletRequest servletRequest;

    private static Logger logger = Logger.getLogger(ArchiveRestService.class.getName());

    /**
     * Loads a snapshot from the archive and returns a HTML representation.
     * 
     * @param id - snapshot id
     * @return XMLDataCollection
     */
    @GET
    @Path("/snapshot/{id : ([0-9a-f]{8}-.*|[0-9a-f]{11}-.*)}")
    public Response getSnapshot(@PathParam("id") String id, @QueryParam("format") String format) {
        Session session = null;
        Cluster cluster = null;
        try {
            logger.finest("...read snapshot...");
            // cluster = clusterService.getCluster();
            // session = clusterService.getArchiveSession(cluster);

            ItemCollection snapshot = dataService.loadSnapshot(id);

            return convertResult(snapshot, format);
            // return XMLDataCollectionAdapter.getDataCollection(snapshot);
            // return XMLDocumentAdapter.getDocument(snapshot);
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

    /**
     * Loads the metadata from the archive and returns a HTML representation.
     * 
     * @return XMLDataCollection
     */
    @GET
    @Path("/metadata")
    public Response getMetadata(@QueryParam("format") String format) {
        Session session = null;
        Cluster cluster = null;
        try {
            logger.finest("...read snapshot...");
            // cluster = clusterService.getCluster();
            // session = clusterService.getArchiveSession(cluster);
            ItemCollection metadata = dataService.loadMetadata();
            return convertResult(metadata, format);
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

    /**
     * Returns a file attachment based on its MD5 Checksum
     * <p>
     * The query parameter 'contentType' can be added to specify the returned
     * content type.
     * 
     * @param md5 - md5 checksum to identify the file content
     * @return
     */
    @GET
    @Path("/md5/{md5}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getSnapshotFileByMD5Checksum(@PathParam("md5") @Encoded String md5,
            @QueryParam("contentType") String contentType) {
        boolean debug = logger.isLoggable(Level.FINE);
        // load the snapshot
        byte[] fileContent = null;
        try {
            if (debug) {
                logger.finest("...read snapshot...");
            }
            // load snapshto without the file data
            fileContent = dataService.loadFileContent(md5);

        } catch (ArchiveException e) {
            logger.warning("...failed to load file: " + e.getMessage());
            e.printStackTrace();
        }
        // extract the file...
        try {

            if (fileContent != null && fileContent.length > 0) {
                // Set content type in order of the contentType stored
                // in the $file attribute
                Response.ResponseBuilder builder = Response.ok(fileContent, contentType);
                return builder.build();
            } else {
                logger.warning("Unable to open file by md5 checksum: '" + md5 + "' - no content!");
                // workitem not found
                return Response.status(Response.Status.NOT_FOUND).build();
            }

        } catch (Exception e) {
            logger.severe(
                    "Unable to open file by md5 checksum: '" + md5 + "' - error: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }

        logger.severe("Unable to open file by md5 checksum: '" + md5 + "'");
        return Response.status(Response.Status.NOT_FOUND).build();

    }

    /**
     * Returns a file attachment located in the property $file of the specified
     * snapshot
     * <p>
     * The file name will be encoded. With a URLDecode the filename is decoded in
     * different formats and searched in the file list. This is not a nice solution.
     * 
     * @param uniqueid
     * @return
     */
    @GET
    @Path("/snapshot/{id}/file/{file}")
    public Response getSnapshotFileByName(@PathParam("snapshotid") String id, @PathParam("file") @Encoded String file,
            @Context UriInfo uriInfo) {

        // load the snapshot
        Session session = null;
        Cluster cluster = null;
        ItemCollection snapshot = null;
        FileData fileData = null;
        try {
            logger.finest("...read snapshot...");
            // cluster = clusterService.getCluster();
            // session = clusterService.getArchiveSession(cluster);
            // load snapshto without the file data
            snapshot = dataService.loadSnapshot(id, false);

            String fileNameUTF8 = URLDecoder.decode(file, "UTF-8");
            String fileNameISO = URLDecoder.decode(file, "ISO-8859-1");
            // try to guess encodings.....
            fileData = snapshot.getFileData(fileNameUTF8);
            if (fileData == null)
                fileData = snapshot.getFileData(fileNameISO);
            if (fileData == null)
                fileData = snapshot.getFileData(file);

            if (fileData != null) {
                // now we load the content
                fileData = dataService.loadFileData(fileData);
            }

        } catch (ArchiveException | UnsupportedEncodingException e) {
            logger.warning("...Failed to load file: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // close session and cluster object
            if (session != null) {
                session.close();
            }
            if (cluster != null) {
                cluster.close();
            }
        }
        // extract the file...
        try {

            if (fileData != null) {
                // Set content type in order of the contentType stored
                // in the $file attribute
                Response.ResponseBuilder builder = Response.ok(fileData.getContent(), fileData.getContentType());
                return builder.build();
            } else {
                logger.warning("ArchiveRestService unable to open file: '" + file + "' in workitem '" + id
                        + "' - error: Filename not found!");
                // workitem not found
                return Response.status(Response.Status.NOT_FOUND).build();
            }

        } catch (Exception e) {
            logger.severe("ArchiveRestService unable to open file: '" + file + "' in workitem '" + id + "' - error: "
                    + e.getMessage());
            e.printStackTrace();
        }

        logger.severe("ArchiveRestService unable to open file: '" + file + "' in workitem '" + id + "'");
        return Response.status(Response.Status.NOT_FOUND).build();

    }

    /**
     * The method writes the data of a snapshot into the cassandra cluster
     * <p>
     * The method returns the snapshot data. In case of an failure the method return
     * a HTTP Response 500. In this case a client can inspect the items
     * '$error_code' and '$error_message' to evaulate the error.
     * <p>
     * In case the data was successfully written into the Cassandra cluster the
     * method returns a HTTP Response 200.
     * 
     * @param xmlworkitem - snapshot data to be stored into cassandra in XML format
     * @return - snapshot data with an error code in case of a failure
     */
//	@POST
//	@Produces(MediaType.APPLICATION_XML)
//	@Consumes({ MediaType.APPLICATION_XML, MediaType.TEXT_XML })
//	public Response postSnapshot(XMLDocument xmlDocument) {
//
//		Session session = null;
//		Cluster cluster = null;
//		ItemCollection snapshot = XMLDocumentAdapter.putDocument(xmlDocument);
//
//		try {
//			logger.finest("...write snapshot...");
//			dataService.saveSnapshot(snapshot);
//			snapshot.removeItem("$error_code");
//			snapshot.removeItem("$error_message");
//
//		} catch (ArchiveException e) {
//			logger.warning("...Failed to initalize imixsarchive keyspace: " + e.getMessage());
//			snapshot = this.addErrorMessage(e, snapshot);
//
//			return null;
//
//		} finally {
//			// close session and cluster object
//			if (session != null) {
//				session.close();
//			}
//			if (cluster != null) {
//				cluster.close();
//			}
//		}
//
//		// return workitem
//		try {
//			if (snapshot.hasItem("$error_code")) {
//				return Response.ok(XMLDataCollectionAdapter.getDataCollection(snapshot), MediaType.APPLICATION_XML)
//						.status(Response.Status.NOT_ACCEPTABLE).build();
//			}
//			else {
//			    // we do not return a data item
//				return Response.ok().build();
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//			return Response.status(Response.Status.NOT_ACCEPTABLE).build();
//		}
//
//	}

    /**
     * This method converts a single ItemCollection into a Jax-rs response object.
     * <p>
     * The method expects optional items and format string (json|xml)
     * <p>
     * In case the result set is null, than the method returns an empty collection.
     * 
     * @param result list of ItemCollection
     * @param items  - optional item list
     * @param format - optional format string (json|xml)
     * @return jax-rs Response object.
     */
    private Response convertResult(ItemCollection workitem, String format) {
        if (workitem == null) {
            workitem = new ItemCollection();
        }
        if ("json".equals(format)) {
            return Response
                    // Set the status and Put your entity here.
                    .ok(XMLDataCollectionAdapter.getDataCollection(workitem, null))
                    // Add the Content-Type header to tell Jersey which format it should marshall
                    // the entity into.
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).build();
        } else if ("xml".equals(format)) {
            return Response
                    // Set the status and Put your entity here.
                    .ok(XMLDataCollectionAdapter.getDataCollection(workitem, null))
                    // Add the Content-Type header to tell Jersey which format it should marshall
                    // the entity into.
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML).build();
        } else {
            // default header param
            return Response
                    // Set the status and Put your entity here.
                    .ok(XMLDocumentAdapter.getDocument(workitem)).build();
        }
    }

    /**
     * This helper method adds a error message to the given entity, based on the
     * data in a Exception. This kind of error message can be displayed in a page
     * evaluating the properties '$error_code' and '$error_message'. These
     * attributes will not be stored.
     * 
     * @param pe
     */
//	private ItemCollection addErrorMessage(Exception pe, ItemCollection aworkitem) {
//
//		if (pe instanceof RuntimeException && pe.getCause() != null) {
//			pe = (RuntimeException) pe.getCause();
//		}
//
//		if (pe instanceof InvalidAccessException) {
//			aworkitem.replaceItemValue("$error_code", ((InvalidAccessException) pe).getErrorCode());
//			aworkitem.replaceItemValue("$error_message", pe.getMessage());
//		} else {
//			aworkitem.replaceItemValue("$error_code", "INTERNAL ERROR");
//			aworkitem.replaceItemValue("$error_message", pe.getMessage());
//		}
//
//		return aworkitem;
//	}
}
