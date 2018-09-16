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

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import org.imixs.workflow.archive.cassandra.services.ConfigurationService;

/**
 * The Imixs-Archive-Cassandra application setup
 * 
 * @author rsoika
 * 
 */

@ApplicationPath("imixsarchive")
public class ImixsArchiveApp extends Application {


	public final static String ITEM_KEYSPACE = "keyspace";
	public final static String ITEM_URL = "url";
	public final static String ITEM_USERID = "userid";
	public final static String ITEM_PASSWORD = "password";
	public final static String ITEM_AUTHMETHOD = "authmethod";
	public final static String ITEM_SYNCPOINT = "syncpoint";

	
	@EJB
	ConfigurationService configurationService;

	public ImixsArchiveApp() {
		super();
	}

	/**
	 * Initialize the web application
	 */
	@PostConstruct
	public void initialize() {
		if (configurationService != null) {
			configurationService.init();
		}
	}
}
