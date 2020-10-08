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
package org.imixs.archive.importer.mail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.mail.Message;
import javax.mail.MessagingException;

import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;

/**
 * The MessageService provides methods attach Message objects as a file to a
 * workitem.
 * 
 * @author rsoika
 * 
 */
@Stateless
@LocalBean
public class MailMessageService {

 
    
    @EJB
    MailConverterService mailConverterService;

    private static Logger logger = Logger.getLogger(MailMessageService.class.getName());

    /**
     * This method extracts a HTML representation from a MimeMessage and adds the
     * HTML file to a given workitem.
     * <p>
     * The name of the attached file is _subject_.html
     * 
     * @param message
     * @throws MessagingException
     * @throws IOException
     */
    public void attachHTMLMessage(Message message, ItemCollection workitem) throws IOException, MessagingException {
        logger.fine("...attach message as html file...");
        // convert email to html...
        String htmlMessage = mailConverterService.convertToHTML(message);
        // attache file
        String filename = message.getSubject() + ".html";
        FileData fileData = new FileData(filename, htmlMessage.getBytes("UTF-8"), "text/html", null);
        workitem.addFileData(fileData);
    }

    /**
     * This method attaches a Mail Message object as a .eml file to a given
     * workitem.
     * <p>
     * The name of the attached file is _subject_.eml
     * 
     * @param message
     * @throws MessagingException
     * @throws IOException
     */
    public void attachMessage(Message message, ItemCollection workitem) throws IOException, MessagingException  {
        logger.fine("...attach message as eml file...");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        message.writeTo(baos);
        String filename = message.getSubject() + ".eml";
        FileData fileData = new FileData(filename, baos.toByteArray(), "message/rfc822", null);
        workitem.addFileData(fileData);
    }

}
