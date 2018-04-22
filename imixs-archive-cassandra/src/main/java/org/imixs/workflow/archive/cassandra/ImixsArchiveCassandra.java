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

import java.util.logging.Logger;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import org.imixs.workflow.WorkflowKernel;

/**
 * The Imixs-Archive-Cassandra application setup
 * 
 * @author rsoika
 * 
 */

@ApplicationPath("getting-started")
public class ImixsArchiveCassandra extends Application {
	public static String PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT = "archive.cluster.contactpoint";
	public static String PROPERTY_ARCHIVE_CLUSTER_KEYSPACE = "archive.cluster.keyspace";

	private static Logger logger = Logger.getLogger(WorkflowKernel.class.getName());

	
	public ImixsArchiveCassandra() {
		super();
		
		String contactPoint = System.getenv(PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT);
		String keySpace = System.getenv(PROPERTY_ARCHIVE_CLUSTER_KEYSPACE);

		
		logger.info(PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT +"="+contactPoint);
		logger.info(PROPERTY_ARCHIVE_CLUSTER_KEYSPACE +"="+keySpace);
		
	}

	
}
