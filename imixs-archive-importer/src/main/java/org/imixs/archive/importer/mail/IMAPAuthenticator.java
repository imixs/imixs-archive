/*  
 *  Imixs-Workflow 
 *  
 *  Copyright (C) 2001-2020 Imixs Software Solutions GmbH,  
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
 *      https://www.imixs.org
 *      https://github.com/imixs/imixs-workflow
 *  
 *  Contributors:  
 *      Imixs Software Solutions GmbH - Project Management
 *      Ralph Soika - Software Developer
 */

package org.imixs.archive.importer.mail;

import java.util.Properties;

import org.imixs.workflow.ItemCollection;

import jakarta.mail.MessagingException;
import jakarta.mail.Store;

/**
 * The IMAPAuthenticator defines the interface to authenticate against a Mail
 * Store. Depending on the Mail system authentication can be very different.
 * Beside the BASIC Authentication there exist various procedures to
 * authenticate via OAUTH. The interface abstracts this procedure.
 * 
 * @see IMAPImportService
 * @author rsoika
 * @version 1.0
 */
public interface IMAPAuthenticator {

    /**
     * This method returns a MailStore object based on a given Configuration
     * 
     * @param sourceConfig
     * @param sourceOptions
     * @return
     * @throws MessagingException
     */
    public Store openMessageStore(ItemCollection sourceConfig, Properties sourceOptions) throws MessagingException;

}
