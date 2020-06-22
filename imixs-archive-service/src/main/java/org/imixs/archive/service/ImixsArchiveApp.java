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

    public static final String EVENTLOG_TOPIC_ADD = "snapshot.add";
    public static final String EVENTLOG_TOPIC_REMOVE = "snapshot.remove";

    // rest service endpoint
    public static final String WORKFLOW_SERVICE_ENDPOINT = "workflow.service.endpoint";
    public static final String WORKFLOW_SERVICE_USER = "workflow.service.user";
    public static final String WORKFLOW_SERVICE_PASSWORD = "workflow.service.password";
    public static final String WORKFLOW_SERVICE_AUTHMETHOD = "workflow.service.authmethod";
    public static final String WORKFLOW_SYNC_INTERVAL = "workflow.sync.interval";
    public static final String WORKFLOW_SYNC_DEADLOCK = "workflow.sync.deadlock";

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
                messageService.logMessage(ResyncService.MESSAGE_TOPIC, "Failed to start scheduler - " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
