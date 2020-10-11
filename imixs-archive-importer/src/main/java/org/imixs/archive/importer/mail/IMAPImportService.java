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
 * The EmailImportAdapter scans a IMAP account
 * <p>
 * The method creates for each email in the INBOX a workitem based on the
 * configuration defined by the import source.
 * <p>
 * After the message was imported successfully, the message will be moved form
 * the INBOX into an archive folder. If the archive folder does not exist, the
 * method creates the folder. The folder name can be configured by the property
 * ARCHIVE_FOLDER. The default name is 'imixs-archive'
 * <p>
 * The email message can be converted into HTML or PDF in case the DETACHE_MODE
 * is set to ALL. To generate the message in PDF format a gotenberg service is
 * expected to convert HTML to PDF. The gotenberg service endpoint can be
 * defined by the option 'GOTENBERG_SERVICE'
 * 
 * @author rsoika
 * @version 1.0
 */
@Stateless
public class IMAPImportService {

    public static final String DETACH_MODE_PDF = "PDF";
    public static final String DETACH_MODE_ALL = "ALL";
    public static final String DETACH_MODE_NONE = "NONE";
    public static final String ARCHIVE_DEFAULT_NAME = "imixs-archive";

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
     * This method reacts on a CDI ImportEvent and reads documents form a IMAP
     * server.
     * <p>
     * Depending on the DETACH_MODE the method will detach file attachments form the
     * email.
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
            documentImportService.logMessage("...connecting to IMAP server: " + imapServer + " : " + imapFolder, event);

            store.connect(imapServer, new Integer(imapPort), imapUser, imapPassword);
            IMAPFolder inbox = (IMAPFolder) store.getFolder(imapFolder);
            inbox.open(Folder.READ_WRITE);

            // Depending on the DETACH_MODE attachments will be added to the new workitem.
            Properties sourceOptions = documentImportService.getOptionsProperties(event.getSource());
            String detachOption = sourceOptions.getProperty("DETACH_MODE", DETACH_MODE_PDF);
            documentImportService.logMessage("...DETACH_MODE = " + detachOption, event);

            // open archive folder...
            IMAPFolder archiveFolder = openImapArchive(store, inbox, sourceOptions, event);

            // fetches all messages from the INBOX...
            Message[] messages = inbox.getMessages();
            documentImportService.logMessage("..." + messages.length + " new messages found", event);

            for (Message message : messages) {
                Address[] fromAddress = message.getFrom();
                logger.finest("......receifed mail from: " + fromAddress[0].toString());
                ItemCollection workitem = createWorkitem(event.getSource());
                if (!DETACH_MODE_NONE.equals(detachOption)) {
                    // scan for attachments....
                    Multipart multiPart = (Multipart) message.getContent();
                    for (int i = 0; i < multiPart.getCount(); i++) {
                        MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(i);
                        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                            String fileName = part.getFileName();

                            // detach only add PDF files?
                            if (DETACH_MODE_PDF.equals(detachOption)) {
                                if (!fileName.toLowerCase().endsWith(".pdf")) {
                                    continue; // skip this attachment
                                }
                            }
                            // add this attachment
                            InputStream input = part.getInputStream();
                            byte[] content = readAllBytes(input);
                            FileData fileData = new FileData(fileName, content, part.getContentType(), null);
                            workitem.addFileData(fileData);
                        }
                    }
                }
                // in DETACH_MODE_ALL we attache the mail body as a PDF or HTML file
                if (DETACH_MODE_ALL.equals(detachOption)) {
                    // do we have a gotenberg service?
                    String gotenbergService = sourceOptions.getProperty("GOTENBERG_SERVICE");
                    if (gotenbergService != null && !gotenbergService.isEmpty()) {
                        try {
                            logger.info("using gotenbergservice: " + gotenbergService);
                            mailMessageService.attachPDFMessage(message, workitem, gotenbergService);
                        } catch (IOException eio) {
                            // there was a problem to call the gotenberg service - fallback to HTML
                            documentImportService
                                    .logMessage(
                                            "... connectiong to gotenberg service '" + gotenbergService + "' failed: "
                                                    + eio.getMessage() + " message will be added in HTML format!",
                                            event);
                            // attach email as HTML....
                            mailMessageService.attachHTMLMessage(message, workitem);
                        }
                    } else {
                        // attach the email as HTML....
                        mailMessageService.attachHTMLMessage(message, workitem);
                    }
                }

                // attach the full e-mail in case of DETACH_MODE_PDF or DETACH_MODE_NONE
                if (!DETACH_MODE_ALL.equals(detachOption)) {
                    mailMessageService.attachMessage(message, workitem);
                }

                // finally process the workitem
                workitem = workflowService.processWorkItemByNewTransaction(workitem);

                // move message into the archive-folder
                Message[] messageList = { message };
                inbox.moveMessages(messageList, archiveFolder);
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
     * This method opens the IMAP archive folder. If the folder does not exist, the
     * method creates the folder. The folder name can be configured by the property
     * ARCHIVE_FOLDER. The default name is 'imixs-archive'
     * 
     * @param sourceOptions
     * @param store
     * @param event
     * @return
     * @throws MessagingException
     */
    private IMAPFolder openImapArchive(Store store, IMAPFolder inbox, Properties sourceOptions,
            DocumentImportEvent event) throws MessagingException {
        // open Archive folder
        String imapArchiveFolder = sourceOptions.getProperty("ARCHIVE_FOLDER", ARCHIVE_DEFAULT_NAME);
        documentImportService.logMessage("...ARCHIVE_FOLDER = " + imapArchiveFolder, event);
        IMAPFolder archive = (IMAPFolder) inbox.getFolder(imapArchiveFolder);
        // if archive folder did not exist create it...
        if (archive.exists() == false) {
            logger.info("...creating folder '" + imapArchiveFolder + "'");
            boolean isCreated = archive.create(Folder.HOLDS_MESSAGES);
            if (isCreated) {
                logger.info("...folder sucessfull created");
            } else {
                logger.info("...failed to create new archvie folder!");
            }
        }
        archive.open(Folder.READ_WRITE);
        return archive;
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
