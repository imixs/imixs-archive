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

import javax.annotation.Resource;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.annotation.security.RunAs;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;

import org.imixs.workflow.archive.hadoop.jca.HelloWorldConnectionFactory;
import org.imixs.workflow.engine.DocumentService;

//import org.imixs.workflow.archive.hadoop.jca.HelloWorldConnectionFactory;

/**
 * The Marty Config Service can be used to store and access configuration values
 * stored in a configuration entity (type='CONFIGURATION).
 * 
 * The ConfigService EJB provides access to named Config Entities stored in the
 * database. A configuration Entity is identified by its name (property
 * txtName). So different configuration Entities can be managed in one
 * application.
 * 
 * The ConfigService ejb is implemented as a sigelton and uses an internal cache
 * to cache config entities.
 * 
 * 
 * @author rsoika
 */

@DeclareRoles({ "org.imixs.ACCESSLEVEL.NOACCESS", "org.imixs.ACCESSLEVEL.READERACCESS",
	"org.imixs.ACCESSLEVEL.AUTHORACCESS", "org.imixs.ACCESSLEVEL.EDITORACCESS",
	"org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@RolesAllowed({ "org.imixs.ACCESSLEVEL.NOACCESS", "org.imixs.ACCESSLEVEL.READERACCESS",
	"org.imixs.ACCESSLEVEL.AUTHORACCESS", "org.imixs.ACCESSLEVEL.EDITORACCESS",
	"org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@Stateless
@LocalBean
@RunAs("org.imixs.ACCESSLEVEL.MANAGERACCESS")
public class HadoopService {

	
	// works!
	//@Resource(lookup = "java:/jdbc/office")
    //private javax.sql.DataSource dataSource;


	
	//@Resource(mappedName = "java:/jca/HadoopFactory")
	//@Resource(mappedName = "/jca/HadoopFactory")
	//@Resource(mappedName = "jca/HadoopFactory")
	//@Resource(lookup = "java:/jca/HadoopFactory")
	//private org.imixs.workflow.archive.hadoop.jca.DataSource dataSource;

	
	 
	@Resource(mappedName = "java:/eis/HelloWorld")
	 private HelloWorldConnectionFactory connectionFactory;
	 
	@EJB
	private DocumentService documentService;

	 
	@Resource
	private SessionContext context;


	/**
	 * creates a new configuration object for a specified name
	 * 
	 * @return
	 */
	public boolean createConfiguration(String content) throws Exception {
//		Connection connection = dataSource.getConnection();
//		connection.write(content);
//		connection.close();
		return true;
	}

	
}
