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

package org.imixs.archive.service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import org.imixs.archive.service.exports.ExportService;
import org.imixs.archive.service.resync.ResyncService;
import org.imixs.archive.service.util.MessageService;

/**
 * The Imixs-Archive-Service application setup
 * 
 * @author rsoika
 * 
 */

@ApplicationPath("api")
public class ImixsArchiveApp extends Application {
	
	@Inject
	ResyncService syncService;

	@Inject
	MessageService messageService;

	@Inject
	ExportService exportService;
	
	public ImixsArchiveApp() {
		super();
	}

	/**
	 * Initialize the web application
	 */
	@PostConstruct
	public void initialize() {
		if (syncService != null) {
			try {
				syncService.start();
				exportService.startScheduler();
			} catch (ArchiveException e) {
				messageService.logMessage(ResyncService.MESSAGE_TOPIC,"Failed to start scheduler - " + e.getMessage());
				e.printStackTrace();
			}
		}
	}
}
