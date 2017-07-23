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

import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.bind.JAXBException;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowContext;
import org.imixs.workflow.engine.plugins.AbstractPlugin;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.xml.DocumentCollection;
import org.imixs.workflow.xml.XMLItemCollectionAdapter;

/**
 * This plugin archives the current workitem into a hadoop cluster.
 * 
 * 
 * @author rsoika
 * @version 2.0
 * 
 */
public class ArchivePlugin extends AbstractPlugin {
	static final String INVALID_TARGET_TASK = "INVALID_TARGET_TASK";
	static final String ARCHIVE_ERROR = "ARCHIVE_ERROR";

	private static Logger logger = Logger.getLogger(ArchivePlugin.class.getName());
	HadoopService hadoopService = null;
	
	@Override
	public void init(WorkflowContext actx) throws PluginException {

		super.init(actx);

		// lookup profile service EJB
		String jndiName = "ejb/HadoopService";
		InitialContext ictx;
		try {
			ictx = new InitialContext();
			Context ctx = (Context) ictx.lookup("java:comp/env");
			hadoopService = (HadoopService) ctx.lookup(jndiName);
		} catch (NamingException e) {

			logger.warning("hat nicht geklappt");
		}

	}

	
	
	@Override
	public ItemCollection run(ItemCollection adocumentContext, ItemCollection documentActivity) throws PluginException {

		if (hadoopService!=null) {
			try {
				hadoopService.createConfiguration("asdfds");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		HDFSClient hdfsClient = null;
		// try to get next ProcessEntity
		ItemCollection itemColNextProcess = null;
		try {
			// now get the next ProcessEntity from ctx
			itemColNextProcess = this.getNextTask(adocumentContext, documentActivity);

			// TODO evaluate archive status of nextTask

			/* 1. dummy implementation - just do archiving.... */

			hdfsClient = new HDFSClient();

			boolean redirect = false;
			// create the target path form the creation date...

			Date created = adocumentContext.getItemValueDate("$created");
			if (created == null) {
				// workitem does not yet exist (only in virtual style)
				// in this case we can not create an achive entry!
				logger.warning("Workitem can not be archived before created - cancel archive process!");
				return adocumentContext;
			}
			LocalDateTime ldt = LocalDateTime.ofInstant(created.toInstant(), ZoneId.systemDefault());

			String path = "";
			path += ldt.getYear() + "/" + ldt.getMonthValue() + "/";

			path += adocumentContext.getUniqueID() + ".xml";

			List<ItemCollection> col = new ArrayList<ItemCollection>();
			// we simply put only the current workitem and no childs into
			// the item col
			col.add(adocumentContext);
			String status = hdfsClient.putData(path, XMLItemCollectionAdapter.putCollection(col));

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

			
			testPerformance(hdfsClient,"6ba6f6af-d129-4346-8f7a-5d48f05ef4cf");
			testPerformance(hdfsClient,"871c89ef-25dd-4170-9ce7-8d682437cf59");
			testPerformance(hdfsClient,"4ba7e95e-9d5f-4ad7-bf31-c46d4e3141cd");

		} catch (ModelException e) {
			throw new PluginException(ArchivePlugin.class.getName(), INVALID_TARGET_TASK,
					"WARNING: Target Task undefinded: " + e.getMessage());

		} catch (Exception e) {
			if (hdfsClient != null) {
				logger.severe("Unable to connect to '" + hdfsClient.getUrl());
			}
			e.printStackTrace();
			throw new PluginException(ArchivePlugin.class.getName(), "ERROR", e.getMessage());
		}

		return adocumentContext;
	}

	private void testPerformance(HDFSClient hdfsClient, String id) throws MalformedURLException, IOException, JAXBException {
		// some performance tests:
		long l = System.currentTimeMillis();
		DocumentCollection doc = null;
		doc = hdfsClient.readData("2017/7/" + id + ".xml");
		logger.info("hadoop read doc '" + id + "' in " + (System.currentTimeMillis() - l) + "ms");
		l = System.currentTimeMillis();
		ItemCollection itemcol = this.getWorkflowService().getWorkItem(id);
		logger.info("sql read doc '" + id + "' in " + (System.currentTimeMillis() - l) + "ms");

	}

}
