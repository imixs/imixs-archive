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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;

import jakarta.ejb.EJB;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;

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
        if (htmlMessage != null) {
            // attache file
            String filename = resolveSubjectToFileName(message) + ".html";
            FileData fileData = new FileData(filename, htmlMessage.getBytes("UTF-8"), "text/html", null);
            workitem.addFileData(fileData);
        }
    }

    /**
     * This method extracts a HTML representation from a MimeMessage and converts
     * the HTML into a PDF file by using the Goteberg Sevice. The method adds the
     * PDF file to a given workitem.
     * <p>
     * The name of the attached file is _subject_.html
     * 
     * @param message
     * @throws MessagingException
     * @throws IOException
     */
    public void attachPDFMessage(Message message, ItemCollection workitem, String gotebergServiceEndpoint)
            throws IOException, MessagingException {
        logger.fine("...attach message as html file...");
        // convert email to html...
        String htmlMessage = mailConverterService.convertToHTML(message);
        if (htmlMessage != null) {
            byte[] pdfContent = GotenbergClient.convertHTML("http://gotenberg:3000/",
                    new ByteArrayInputStream(htmlMessage.getBytes(StandardCharsets.UTF_8)));
            if (pdfContent != null) {
                // attache file
                String filename = resolveSubjectToFileName(message) + ".pdf";
                FileData fileData = new FileData(filename, pdfContent, "application/pdf", null);
                workitem.addFileData(fileData);
            }
        }
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
    public void attachMessage(Message message, ItemCollection workitem) throws IOException, MessagingException {
        logger.fine("...attach message as eml file...");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        message.writeTo(baos);
        String filename = resolveSubjectToFileName(message) + ".eml";
        FileData fileData = new FileData(filename, baos.toByteArray(), "message/rfc822", null);
        workitem.addFileData(fileData);
    }

    /**
     * Helper method to resolve the subject to a valid filename to be used to store
     * .pdf and .eml files.
     * 
     * @param message
     * @return
     * @throws MessagingException
     */
    private String resolveSubjectToFileName(Message message) throws MessagingException {
        String subject = "-- no subject --";
        if (message.getSubject() != null && !message.getSubject().isEmpty()) {
            subject = message.getSubject();
        }
        // Define a regex pattern for invalid file characters
        String invalidCharsRegex = "[\\\\/:*?\"<>|]";
        // Replace invalid characters with underscores
        subject = subject.replaceAll(invalidCharsRegex, "_");
        // remove leading and trailing whitespaces
        subject = subject.trim();

        return subject;
    }
}
