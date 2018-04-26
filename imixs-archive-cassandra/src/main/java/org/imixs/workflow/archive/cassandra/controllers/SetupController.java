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

import java.io.Serializable;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;

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
@Named
@ApplicationScoped
public class SetupController implements Serializable {

	private static final long serialVersionUID = 1L;
	public static String PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT = "archive.cluster.contactpoints";
	public static String PROPERTY_ARCHIVE_CLUSTER_KEYSPACE = "archive.cluster.keyspace";

	private static Logger logger = Logger.getLogger(SetupController.class.getName());

	
	Properties configurationProperties=null;
	private String contactPoints;
	private String keySpace;

	public SetupController() {
		super();

	}

	/**
	 * This method loads the config entity. If the entity did not yet exist, the
	 * method creates one.
	 * 
	 *
	 */
	@PostConstruct
	public void init() {
		logger.info("Initial setup: reading environment....");
		
		
		 configurationProperties = new Properties();
		try {
			// load confiugration file 'imixs.properties'
			configurationProperties
					.load(Thread.currentThread().getContextClassLoader().getResource("imixs.properties").openStream());
		} catch (Exception e) {
			logger.warning("LDAPLookupService unable to find imixs.properties in current classpath");
			e.printStackTrace();
		}

		// skip if no configuration
		if (configurationProperties == null) {
			logger.severe("Missing imixs.properties!");
			return;
		}
		
		
		// load environment setup..
		contactPoints = configurationProperties.getProperty(PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT);
		keySpace = configurationProperties.getProperty(PROPERTY_ARCHIVE_CLUSTER_KEYSPACE);
		
		
			
		logger.info(PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT +"="+contactPoints);
		logger.info(PROPERTY_ARCHIVE_CLUSTER_KEYSPACE +"="+keySpace);

	}

	public String getContactPoints() {
		return contactPoints;
	}

	public void setContactPoints(String contactPoints) {
		this.contactPoints = contactPoints;
	}

	public String getKeySpace() {
		return keySpace;
	}

	public void setKeySpace(String keySpace) {
		this.keySpace = keySpace;
	}

}
