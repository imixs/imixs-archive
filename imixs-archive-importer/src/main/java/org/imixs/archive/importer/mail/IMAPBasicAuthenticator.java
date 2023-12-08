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

import java.io.Serializable;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;

import org.imixs.archive.importer.DocumentImportService;
import org.imixs.workflow.ItemCollection;

import jakarta.inject.Named;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;

/**
 * The IMAPBasicAuthenticator authenticates against an Mail store using BASIC
 * authentication
 * 
 * @see IMAPImportService
 * @author rsoika
 * @version 1.0
 */
@Named
public class IMAPBasicAuthenticator implements IMAPAuthenticator, Serializable {
    private static final long serialVersionUID = 1L;
    private static Logger logger = Logger.getLogger(IMAPBasicAuthenticator.class.getName());

    /**
     * This method returns a MailStore object based on a given Configuration
     * 
     * @param sourceConfig
     * @param sourceOptions
     * @return
     * @throws NumberFormatException
     * @throws MessagingException
     */
    public Store openMessageStore(ItemCollection sourceConfig, Properties sourceOptions) throws MessagingException {
        String imapServer = sourceConfig.getItemValueString(DocumentImportService.SOURCE_ITEM_SERVER);
        String imapPort = sourceConfig.getItemValueString(DocumentImportService.SOURCE_ITEM_PORT);
        String imapUser = sourceConfig.getItemValueString(DocumentImportService.SOURCE_ITEM_USER);
        String imapPassword = sourceConfig.getItemValueString(DocumentImportService.SOURCE_ITEM_PASSWORD);

        // create an empty properties object...
        // Properties props = System.getProperties();
        Properties imapProperties = new Properties();
        // add default protocol..
        imapProperties.setProperty("mail.store.protocol", "imaps");
        // now we parse the mail properties provided by the options....
        @SuppressWarnings("unchecked")
        Enumeration<String> enums = (Enumeration<String>) sourceOptions.propertyNames();
        while (enums.hasMoreElements()) {
            String key = enums.nextElement();
            if (key.startsWith("mail.")) {
                // add key...
                imapProperties.setProperty(key, sourceOptions.getProperty(key));
                logger.info("......setting property from source options: " + key);
            }
        }
        // custom port?
        if (imapProperties.containsKey("mail.imap.port")) {
            imapPort = imapProperties.getProperty("mail.imap.port");
        }

        if (imapPort.isEmpty()) {
            // set default port
            imapPort = "993";
        }

        // connect....
        Session session = Session.getDefaultInstance(imapProperties, null);
        Store store = session.getStore(); // "imaps"

        store.connect(imapServer, Integer.parseInt(imapPort), imapUser, imapPassword);

        return store;
    }
}
