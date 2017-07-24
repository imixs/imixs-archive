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

import org.imixs.workflow.engine.DocumentService;


/**
 * The Service can be used to store data into hadoop
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

	
	 
	//@Resource(mappedName = "java:/eis/HelloWorld")
	//private HelloWorldConnectionFactory connectionFactory;
	 
	@EJB
	private DocumentService documentService;

	 
	@Resource
	private SessionContext context;


	/**
	 * just for testing...
	 * 
	 * @return
	 */
	public String createConfiguration(String content) throws Exception {
		String result = null;
		// HelloWorldConnection connection = null;
//         try {
////              connection = connectionFactory.getConnection();               
////              result = connection.helloWorld();
//             
//         } catch (ResourceException e) {
//             // TODO Auto-generated catch block
//             e.printStackTrace();
//         }


      return result;
      
      
	}

	
}
