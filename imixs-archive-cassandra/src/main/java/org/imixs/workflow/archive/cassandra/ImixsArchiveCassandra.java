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

package org.imixs.workflow.archive.cassandra;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.mvc.engine.ViewEngine;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.archive.cassandra.controllers.SetupController;

/**
 * The Imixs-Archive-Cassandra application setup
 * 
 * @author rsoika
 * 
 */

@ApplicationPath("app")
public class ImixsArchiveCassandra extends Application {

	@Inject
	SetupController setupController;

	private static Logger logger = Logger.getLogger(WorkflowKernel.class.getName());

	public ImixsArchiveCassandra() {

		super();

	}

//	@Override
//	public Map<String, Object> getProperties() {
//		final Map<String, Object> map = new HashMap<>();
//	//	map.put(ViewEngine.VIEW_FOLDER, "/"); // overrides default /WEB-INF/
//		return map;
//	}

}
