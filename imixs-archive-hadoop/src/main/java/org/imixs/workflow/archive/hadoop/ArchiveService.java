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

package org.imixs.workflow.archive.hadoop;

import java.io.StringReader;
import java.io.StringWriter;
import java.rmi.RemoteException;
import java.util.logging.Logger;

import javax.ejb.EJBException;
import javax.ejb.LocalBean;
import javax.ejb.SessionSynchronization;
import javax.ejb.Stateful;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.xml.XMLItemCollection;
import org.imixs.workflow.xml.XMLItemCollectionAdapter;


/**
 * The Service can be used to store data into hadoop.
 * The bean has SessionSynchronization to rollback failed transactions. The bean is used by the ArchivePlugin.
 * 
 * The method write(path, data) can be used to store data into the hadoop cluster. 
 * The bean synchronizes the transaction state and rollback any changes to hadoop made in on transaction.
 * 
 * 
 * 
 * @author rsoika
 */

@Stateful
@LocalBean
public class ArchiveService implements SessionSynchronization {
	
	static final String ARCHIVE_ERROR = "ARCHIVE_ERROR";
	private static Logger logger = Logger.getLogger(ArchiveService.class.getName());

	/**
	 * This method write data to the hadoop archive.
	 * 
	 * @param file
	 * @param content
	 * @return
	 */
	public String doArchive(String path,ItemCollection document) throws PluginException {
		
		HDFSClient hdfsClient = null;
		try {
		
			hdfsClient = new HDFSClient();

			StringWriter writer = new StringWriter();
			
			
			// convert the ItemCollection into a XMLItemcollection...
			XMLItemCollection xmlItemCollection= XMLItemCollectionAdapter.putItemCollection(document);

			// marshal the Object into an XML Stream....
		
			JAXBContext context = JAXBContext.newInstance(XMLItemCollection.class);
			Marshaller m=context.createMarshaller();
			m.marshal(xmlItemCollection,writer);
			
			byte[] content=writer.toString().getBytes();

			String status = hdfsClient.putData(path, content);

			logger.info("Status=" + status);

			// extract the status code from the hdfs put call
			JsonReader reader = Json.createReader(new StringReader(status));
			JsonObject payloadObject = reader.readObject();
			int httpResult = Integer.parseInt(payloadObject.getString("code", "500"));
			if (httpResult < 200 || httpResult >= 300) {
				throw new PluginException(ArchivePlugin.class.getName(), ARCHIVE_ERROR,
						"Archive failed - HTTP Result:" + status);
			} else {
				logger.info("Archive successful -HTTP Result: " + status);
			}

			
		
		} catch (Exception e) {
			if (hdfsClient != null) {
				logger.severe("Unable to connect to '" + hdfsClient.getUrl());
			}
			e.printStackTrace();
			throw new PluginException(ArchivePlugin.class.getName(), "ERROR", e.getMessage());
		}

		
		
		return null;
	}
	@Override
	public void afterBegin() throws EJBException, RemoteException {
		System.out.println("after begin....");
	}
	@Override
	public void afterCompletion(boolean arg0) throws EJBException, RemoteException {
		System.out.println("after completion... status="+arg0);
	}
	@Override
	public void beforeCompletion() throws EJBException, RemoteException {
		System.out.println("before comple...");
	}

}
