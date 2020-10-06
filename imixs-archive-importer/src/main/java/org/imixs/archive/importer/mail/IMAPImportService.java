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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.enterprise.event.Observes;
import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;

import org.imixs.archive.importer.DocumentImportEvent;
import org.imixs.archive.importer.DocumentImportService;
import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.ModelService;
import org.imixs.workflow.engine.WorkflowService;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.exceptions.ProcessingErrorException;

import com.sun.mail.imap.IMAPFolder;

/**
 * The EmailImportAdapter scanns a IMAP account
 * 
 * @author rsoika
 *
 */
@Stateless
public class IMAPImportService {

    private static Logger logger = Logger.getLogger(IMAPImportService.class.getName());

    @EJB
    WorkflowService workflowService;

    @EJB
    ModelService modelService;

    @EJB
    DocumentImportService documentImportService;
    
    @EJB
    MailMessageService mailMessageService;

    /**
     * This method reacts on a CDI ImportEvent and reads documents form a ftp
     * server.
     * 
     * 
     */
    public void onEvent(@Observes DocumentImportEvent event) {

        // check if source is already completed
        if (event.getResult() == DocumentImportEvent.PROCESSING_COMPLETED) {
            logger.finest("...... import source already completed - no processing will be performed.");
            return;
        }

        if (!"IMAP".equalsIgnoreCase(event.getSource().getItemValueString("type"))) {
            // ignore data source
            logger.finest("...... type '" + event.getSource().getItemValueString("type") + "' skiped.");
            return;
        }

        String imapServer = event.getSource().getItemValueString(DocumentImportService.SOURCE_ITEM_SERVER);
        String imapPort = event.getSource().getItemValueString(DocumentImportService.SOURCE_ITEM_PORT);
        String imapUser = event.getSource().getItemValueString(DocumentImportService.SOURCE_ITEM_USER);
        String imapPassword = event.getSource().getItemValueString(DocumentImportService.SOURCE_ITEM_PASSWORD);
        String imapFolder = event.getSource().getItemValueString(DocumentImportService.SOURCE_ITEM_SELECTOR);

        if (imapPort.isEmpty()) {
            // set default port
            imapPort = "993";
        }

        if (imapFolder.isEmpty()) {
            imapFolder = "INBOX";
        }
        try {

            Properties props = System.getProperties();
            props.setProperty("mail.store.protocol", "imaps");
            Session session = Session.getDefaultInstance(props, null);
            Store store = session.getStore("imaps");

            documentImportService.logMessage("Connecting to IMAP server: " + imapServer, event);

            store.connect(imapServer, new Integer(imapPort), imapUser, imapPassword);
            IMAPFolder inbox = (IMAPFolder) store.getFolder(imapFolder);
            inbox.open(Folder.READ_ONLY);

            // fetches new messages from server
            Message[] messages = inbox.getMessages();

            for (Message message : messages) {

                Address[] fromAddress = message.getFrom();
                documentImportService.logMessage("...receifed mail from: " + fromAddress[0].toString(), event);


                ItemCollection workitem = createWorkitem(event.getSource());

                List<Multipart> partsToDelete=new ArrayList<Multipart>();
                // scan for attachements....
                Multipart multiPart = (Multipart) message.getContent();
                for (int i = 0; i < multiPart.getCount(); i++) {
                    MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(i);
                    if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                        // this part is attachment
                        InputStream input = part.getInputStream();
                        byte[] content = readAllBytes(input);
                        FileData fileData = new FileData(part.getFileName(), content, part.getContentType(), null);
                        workitem.addFileData(fileData);
                        partsToDelete.add(multiPart);
                    }
                }

               
                // Find the html body
                if (message instanceof MimeMessage) {
                    mailMessageService.attachHTMLMessage((MimeMessage) message, workitem);
                }
               
                
                // finally we attache the full e-mail....
                mailMessageService.attachMessage(message,workitem);
               
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                message.writeTo(baos);
                String filename=message.getSubject()+".eml";
                FileData fileData = new FileData(filename, baos.toByteArray(), "message/rfc822", null);
                workitem.addFileData(fileData);
                
                
                // finally process the workitem
                workitem = workflowService.processWorkItemByNewTransaction(workitem);

            }

            documentImportService.logMessage("finished", event);

        } catch (AccessDeniedException | ProcessingErrorException | PluginException | ModelException e) {

            documentImportService.logMessage("IMAP import failed: " + e.getMessage(), event);
            event.setResult(DocumentImportEvent.PROCESSING_ERROR);
            return;

        } catch (MessagingException | IOException e) {
            documentImportService.logMessage("IMAP import failed: " + e.getMessage(), event);
            event.setResult(DocumentImportEvent.PROCESSING_ERROR);
            return;
        }

    }

    /**
     * Creates and processes a new workitem with a given filedata
     * 
     * @return
     * @throws ModelException
     * @throws PluginException
     * @throws ProcessingErrorException
     * @throws AccessDeniedException
     */
    public ItemCollection createWorkitem(ItemCollection source)
            throws AccessDeniedException, ProcessingErrorException, PluginException, ModelException {
        ItemCollection workitem = new ItemCollection();
        workitem.model(source.getItemValueString(DocumentImportService.SOURCE_ITEM_MODELVERSION));
        workitem.task(source.getItemValueInteger(DocumentImportService.SOURCE_ITEM_TASK));
        workitem.event(source.getItemValueInteger(DocumentImportService.SOURCE_ITEM_EVENT));
        workitem.setWorkflowGroup(source.getItemValueString("workflowgroup"));

        return workitem;
    }

    /**
     * Read inputstream into a byte array.
     * 
     * @param inputStream
     * @return
     * @throws IOException
     */
    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        final int bufLen = 4 * 0x400; // 4KB
        byte[] buf = new byte[bufLen];
        int readLen;
        IOException exception = null;

        try {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                while ((readLen = inputStream.read(buf, 0, bufLen)) != -1)
                    outputStream.write(buf, 0, readLen);

                return outputStream.toByteArray();
            }
        } catch (IOException e) {
            exception = e;
            throw e;
        } finally {
            if (exception == null)
                inputStream.close();
            else
                try {
                    inputStream.close();
                } catch (IOException e) {
                    exception.addSuppressed(e);
                }
        }
    }
    
   
}
