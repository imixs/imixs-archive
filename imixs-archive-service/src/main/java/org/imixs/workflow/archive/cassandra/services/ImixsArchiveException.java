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

package org.imixs.workflow.archive.cassandra.services;

/**
 * WorkflowException is thrown by the ClusterService
 * 
 * 
 * @author rsoika
 * 
 */
public  class ImixsArchiveException extends Exception {

	private static final long serialVersionUID = 1L;

	protected String errorContext = "UNDEFINED";
	protected String errorCode = "UNDEFINED";
	
	
	public static final String INVALID_DOCUMENT_OBJECT = "INVALID_DOCUMENT_OBJECT";
	public static final String INVALID_KEYSPACE = "INVALID_KEYSPACE";
	public static final String SYNC_ERROR= "SYNC_ERROR";
	
	
	public ImixsArchiveException(String aErrorCode, String message) {
		super(message);
		errorCode = aErrorCode;

	}

	public ImixsArchiveException(String aErrorContext, String aErrorCode,
			String message) {
		super(message);
		errorContext = aErrorContext;
		errorCode = aErrorCode;

	}

	public ImixsArchiveException(String aErrorContext, String aErrorCode,
			String message, Exception e) {
		super(message, e);
		errorContext = aErrorContext;
		errorCode = aErrorCode;

	}
	

	public ImixsArchiveException(String aErrorCode, String message, Exception e) {
		super(message, e);

		errorCode = aErrorCode;

	}

	public String getErrorContext() {
		return errorContext;
	}

	public void setErrorContext(String errorContext) {
		this.errorContext = errorContext;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

}