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

import java.util.List;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.ModelService;
import org.imixs.workflow.engine.plugins.AbstractPlugin;
import org.imixs.workflow.exceptions.PluginException;

/**
 * This plugin archives the current workitem into a hadoop cluster. The plugin
 * delegates the hadoop operation to the ArchiveService which provides a
 * transactional access service to hadoop.
 * 
 * A document can only be archive if the $creation attribute exists.
 * 
 * 
 * @author rsoika
 * @version 2.0
 * 
 */
public class JMSPlugin extends AbstractPlugin {
	static final String INVALID_TARGET_TASK = "INVALID_TARGET_TASK";

	private static Logger logger = Logger.getLogger(JMSPlugin.class.getName());

	@EJB
	ModelService modelService;
	
	@EJB
	JMSService jmsService;



	@Override
	public ItemCollection run(ItemCollection adocumentContext, ItemCollection documentActivity) throws PluginException {

		// test model service
		List<String> versions = modelService.getVersions();
		for (String aversion : versions) {
			logger.info("ModelVersion found: " + aversion);
		}
		
		jmsService.doArchive("xxxyyyyasdfadsf", adocumentContext);

		return adocumentContext;
	}

	/**
	 * Rolback transaction is handled by the archiveService EJB. So no action is
	 * necessary in the close method
	 */
	@Override
	public void close(boolean rollbackTransaction) throws PluginException {
		super.close(rollbackTransaction);
	}

}
