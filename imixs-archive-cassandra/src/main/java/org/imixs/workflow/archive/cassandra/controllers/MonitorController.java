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

package org.imixs.workflow.archive.cassandra.controllers;

import java.util.logging.Logger;

import javax.inject.Inject;
import javax.mvc.annotation.Controller;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.imixs.workflow.archive.cassandra.ClusterDataController;

/**
 * This Marty SetupController extends the Marty ConfigController and holds the
 * data from the configuration entity 'BASIC'. This is the general configuration
 * entity.
 * 
 * In addition the CDI bean verifies the setup of userDB and system models and
 * calls a init method if the system is not setup and the imixs.property param
 * 'setup.mode' is set to 'auto'.
 * 
 * The bean is triggered in the index.xhtml page
 * 
 * 
 * NOTE: A configuration entity provides a common way to manage application
 * specific complex config data. The configuration entity is database controlled
 * and more flexible as the file based imixs.properties provided by the Imixs
 * Workflow Engine.
 * 
 * 
 * @author rsoika
 * 
 */
@Controller
@Path("monitor")
public class MonitorController {

	@Inject
	ClusterDataController clusterDataController;
	
	private static Logger logger = Logger.getLogger(MonitorController.class.getName());

	/**
	 * show connections
	 * 
	 * @return
	 */
	@Path("/")
	@GET
	public String showSetupSettings() {
		logger.info("show monitor...");
		// refresh config....
		clusterDataController.refreshConfiguration();
		return "monitor.xhtml";
	}
	
	

}
