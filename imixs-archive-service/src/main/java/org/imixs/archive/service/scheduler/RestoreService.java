/*******************************************************************************
 *  Imixs Workflow Technology
 *  Copyright (C) 2001, 2008 Imixs Software Solutions GmbH,  
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
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika
 *******************************************************************************/
package org.imixs.archive.service.scheduler;

import java.util.logging.Logger;

import javax.ejb.Stateless;

/**
 * The RecoveryService recovers the workflow data stored in the cassandra
 * cluster into the workflow system. The service class runns a TimerService
 * based on the given scheduler configuration.
 * <p>
 * The scheduler configuration is based on the chron format. E.g:
 * 
 * <code>
 *    hour=*;minute=30;
 * </code>
 * 
 * @version 1.0
 * @author rsoika
 */

@Stateless
public class RecoveryService {

	
	private static Logger logger = Logger.getLogger(RecoveryService.class.getName());

}
