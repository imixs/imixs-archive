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
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.enterprise.event.Observes;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.ws.rs.core.MediaType;

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
 * 'archive.folder'. The default name is 'imixs-archive'
 * <p>
 * The email message can be converted into HTML or PDF in case the option
 * 'detach.mode' is set to ALL. To generate the message in PDF format a
 * gotenberg service is expected to convert HTML to PDF. The gotenberg service
 * endpoint can be defined by the option 'gotenberg.service'
 * 
 * @author rsoika
 * @version 1.0
 */
@Stateless
public class IMAPImportService {

    public static final String OPTION_ARCHIVE_FOLDER = "archive.folder";
    public static final String OPTION_SUBJECT_REGEX = "subject.regex";
    public static final String OPTION_DETACH_MODE = "detach.mode";
    public static final String OPTION_PRESERVE_ORIGIN = "preserve.origin";
    public static final String OPTION_GOTENBERG_SERVICE = "gotenberg.service";

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

        Properties sourceOptions = documentImportService.getOptionsProperties(event.getSource());

        // List<String> options =
        // event.getSource().getItemValue(DocumentImportService.SOURCE_ITEM_OPTIONS);
        Pattern subjectPattern = null;
        String subjectRegex = sourceOptions.getProperty(OPTION_SUBJECT_REGEX, "");
        // do we have filter options providing a Subject Regex?
        if (subjectRegex != null && !subjectRegex.trim().isEmpty()) {
            try {
                subjectPattern = Pattern.compile(subjectRegex);
                documentImportService.logMessage("...subject.regex = " + subjectRegex, event);
            } catch (PatternSyntaxException e) {
                documentImportService.logMessage("Invalid IMAP regex filter: " + e.getMessage(), event);
                return;
            }
        }

        if (imapPort.isEmpty()) {
            // set default port
            imapPort = "993";
        }

        if (imapFolder.isEmpty()) {
            imapFolder = "INBOX";
        }
        try {
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
                imapPort=imapProperties.getProperty("mail.imap.port");
            }

            // connect....
            Session session = Session.getDefaultInstance(imapProperties, null);
            Store store = session.getStore(); // "imaps"
            documentImportService.logMessage(
                    "...connecting to IMAP server: " + imapServer + ":" + imapPort + " /" + imapFolder, event);

            store.connect(imapServer, Integer.parseInt(imapPort), imapUser, imapPassword);
            IMAPFolder inbox = (IMAPFolder) store.getFolder(imapFolder);
            inbox.open(Folder.READ_WRITE);

            // Depending on the option 'detach.mode' attachments will be added to the new
            // workitem.
            String detachOption = sourceOptions.getProperty(OPTION_DETACH_MODE, DETACH_MODE_PDF);
            documentImportService.logMessage("...detach.mode = " + detachOption, event);

            // open archive folder...
            IMAPFolder archiveFolder = openImapArchive(store, inbox, sourceOptions, event);

            // fetches all messages from the INBOX...
            Message[] messages = inbox.getMessages();
            documentImportService.logMessage("..." + messages.length + " new messages found", event);

            for (Message message : messages) {
                Address[] fromAddress = message.getFrom();
                String subject = message.getSubject();
                if (subject == null || subject.trim().isEmpty()) {
                    subject = "no-subject";
                }
                Date sent = message.getSentDate();
                logger.fine("......received mail from: " + fromAddress[0].toString());
                logger.fine("......subject = " + subject);
                // do we have a subject regular expression provided by the options?
                if (subjectPattern != null) {
                    if (subject == null || subject.isEmpty()) {
                        // skip this mail
                        continue;
                    }
                    // test if subject matches?
                    Matcher subjectMatcher = subjectPattern.matcher(subject);
                    if (subjectMatcher == null) {
                        logger.finest("matcher is null!");
                        // skip this mail
                        continue;
                    }
                    if (!subjectMatcher.find()) {
                        // skip this mail
                        continue;
                    }
                } else {
                    logger.finest("...no regex pattern mail will be processed...");
                }

                ItemCollection workitem = createWorkitem(event.getSource());
                // store message attributes
                if (fromAddress[0] instanceof InternetAddress) {
                    InternetAddress internetAddr = (InternetAddress) fromAddress[0];
                    workitem.setItemValue("mail.from", internetAddr.getAddress());
                    workitem.setItemValue("mail.from.personal", internetAddr.getPersonal());

                } else {
                    workitem.setItemValue("mail.from", fromAddress[0].toString());
                }
                workitem.setItemValue("mail.subject", subject);
                workitem.setItemValue("mail.sent", sent);

                if (!DETACH_MODE_NONE.equals(detachOption)) {
                    // scan for attachments....
                    // we need to test if the content is a multipart of if is is plain text mail
                    Object contentObject = message.getContent();
                    if (contentObject instanceof Multipart) {
                        // here we are save to cast the content to Mulipart
                        Multipart multiPart = (Multipart) message.getContent();
                        for (int i = 0; i < multiPart.getCount(); i++) {

                            BodyPart bodyPart = multiPart.getBodyPart(i);
                            if (bodyPart instanceof MimeBodyPart) {
                                MimeBodyPart mimeBodyPart = (MimeBodyPart) multiPart.getBodyPart(i);
                                if (Part.ATTACHMENT.equalsIgnoreCase(mimeBodyPart.getDisposition())) {

                                    String fileName = mimeBodyPart.getFileName();
                                    if (fileName == null) {
                                        logger.info("...skip because of missing filename");
                                        continue; // skip this attachment
                                    }
                                    // detach only add PDF files?
                                    if (DETACH_MODE_PDF.equals(detachOption)) {
                                        if (!fileName.toLowerCase().endsWith(".pdf")) {
                                            continue; // skip this attachment
                                        }
                                    }
                                    // add this attachment
                                    InputStream input = mimeBodyPart.getInputStream();
                                    byte[] content = readAllBytes(input);
                                    String contentType = mimeBodyPart.getContentType();
                                    // fix mimeType if application/octet-stream and file extension is .pdf
                                    // (issue #147)
                                    logger.info("mimetype=" + contentType);
                                    if (contentType.contains(MediaType.APPLICATION_OCTET_STREAM)
                                            && fileName.toLowerCase().endsWith(".pdf")) {
                                        logger.info("converting mimetype to application/pdf");
                                        contentType = "application/pdf";
                                    }
                                    // strip ; prafixes
                                    if (contentType.contains(";")) {
                                        contentType = contentType.substring(0, contentType.indexOf(";"));
                                    }
                                    FileData fileData = new FileData(fileName, content, contentType, null);
                                    workitem.addFileData(fileData);
                                }
                            }
                        }
                    } else {
                        // email content if of type plain/text - so we can not extract data...
                    }
                }

                // in DETACH_MODE_ALL we attach the mail body as a PDF or HTML file
                if (DETACH_MODE_ALL.equals(detachOption)) {
                    // do we have a gotenberg service?
                    String gotenbergService = sourceOptions.getProperty(OPTION_GOTENBERG_SERVICE);
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

                    // only if OPTION_PRESERVE_ORIGIN=true than attach the origin message!
                    String preserveOrigin = sourceOptions.getProperty(OPTION_PRESERVE_ORIGIN, "true");
                    if (preserveOrigin != null && "true".equalsIgnoreCase(preserveOrigin)) {
                        mailMessageService.attachMessage(message, workitem);
                    }
                }

                // attach the full e-mail in case of DETACH_MODE_PDF or DETACH_MODE_NONE
                if (!DETACH_MODE_ALL.equalsIgnoreCase(detachOption)) {
                    mailMessageService.attachMessage(message, workitem);
                }

                // finally process the workitem
                workitem = workflowService.processWorkItemByNewTransaction(workitem);

                // move message into the archive-folder
                Message[] messageList = { message };
                inbox.moveMessages(messageList, archiveFolder);
            }

            logger.finest("...completed");
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
        String imapArchiveFolder = sourceOptions.getProperty(OPTION_ARCHIVE_FOLDER, ARCHIVE_DEFAULT_NAME);
        documentImportService.logMessage("...archive.folder = " + imapArchiveFolder, event);
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
