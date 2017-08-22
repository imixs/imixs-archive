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

import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.Queue;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.PluginException;

/**
 * The Service ...
 * 
 * 
 * @author rsoika
 */

@Stateless
@LocalBean
public class JMSService {
	private static Logger logger = Logger.getLogger(JMSService.class.getName());

	//@Resource(mappedName = "java:/jms/queue/ExpiryQueue")
	@Resource(mappedName = "java:jboss/exported/jms/queue/test")
	private Queue queueExample;

	@Inject
	JMSContext context;

	/**
	 * This method writes the data of a Imixs ItemCollection into ...
	 * 
	 * @param file
	 * @param content
	 * @return
	 */
	public void doArchive(String path, ItemCollection document) throws PluginException {
		logger.info("huhu ich schreibe was: "+path);

		try {

			context.createProducer().send(queueExample, path);

		} catch (Exception exc) {
			exc.printStackTrace();
		}

	}

}
