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

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import org.imixs.workflow.archive.cassandra.services.ClusterService;

import com.datastax.driver.core.Session;

/**
 * The Imixs-Archive-Cassandra application setup
 * 
 * @author rsoika
 * 
 */

@ApplicationPath("imixsarchive")
public class ImixsArchiveCassandra extends Application {

	@EJB
	ClusterService clusterService;

	public static final String CLUSTER_STATUS = "cluster.status";
	public static final String KEYSPACE_STATUS = "keyspace.status";

	private static Logger logger = Logger.getLogger(ImixsArchiveCassandra.class.getName());

	public ImixsArchiveCassandra() {
		super();
	}

	/**
	 * Initialize the web application
	 */
	@PostConstruct
	public void initialize() {
		logger.info("......postconstruct ejb!=null : " + (clusterService != null));

		if (clusterService != null) {
			logger.info("......testing cluster status...");
			Session session = clusterService.connect();
			if (session != null) {
				this.getProperties().put(CLUSTER_STATUS,"OK");
				this.getProperties().put(KEYSPACE_STATUS,"OK");
				logger.info("......cluster status = OK");
			}
		}
	}

}
