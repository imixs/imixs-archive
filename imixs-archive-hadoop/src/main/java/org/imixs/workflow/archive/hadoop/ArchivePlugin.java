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

import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowContext;
import org.imixs.workflow.engine.plugins.AbstractPlugin;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.xml.XMLItemCollection;
import org.imixs.workflow.xml.XMLItemCollectionAdapter;

/**
 * This plugin archives the current workitem into a hadoop cluster. The plugin
 * delegates the hadoop operation to the ArchiveService which provides a
 * transactional access service to hadoop.
 * 
 * 
 * @author rsoika
 * @version 2.0
 * 
 */
public class ArchivePlugin extends AbstractPlugin {
	static final String INVALID_TARGET_TASK = "INVALID_TARGET_TASK";

	private static Logger logger = Logger.getLogger(ArchivePlugin.class.getName());

	@EJB
	ArchiveService archiveService;

	@Override
	public ItemCollection run(ItemCollection adocumentContext, ItemCollection documentActivity) throws PluginException {

		// compute the target path

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

		StringWriter writer = new StringWriter();
		JAXBContext context;
		try {
			
			
			
			// convert the ItemCollection into a XMLItemcollection...
			XMLItemCollection xmlItemCollection= XMLItemCollectionAdapter.putItemCollection(adocumentContext);

			// marshal the Object into an XML Stream....
		
		 context = JAXBContext.newInstance(XMLItemCollection.class);
			Marshaller m=context.createMarshaller();
			m.marshal(xmlItemCollection,writer);
			System.out.println(writer.toString());
			
			
		

			byte[] content = writer.toString().getBytes();

		} catch (JAXBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		archiveService.doArchive(path, adocumentContext);

		return adocumentContext;
	}

	@Override
	public void close(boolean rollbackTransaction) throws PluginException {
		if (rollbackTransaction) {
			logger.warning("DO ROLLBACK! - Missing implementation");
		}
		super.close(rollbackTransaction);
	}

}
