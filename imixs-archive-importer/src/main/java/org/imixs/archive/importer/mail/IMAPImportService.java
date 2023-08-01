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

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
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
 * 'archive.folder'. The default name is 'imixs-archive'
 * <p>
 * The email message can be converted into HTML or PDF in case the option
 * 'detach.mode' is set to ALL. To generate the message in PDF format a
 * gotenberg service is expected to convert HTML to PDF. The gotenberg service
 * endpoint can be defined by the option 'gotenberg.service'
 * <p>
 * To access a mail store the IMAPImportService injects IMAPAuthenticator's.
 * These authenticator classes can be used to authenticate against a Mail Store.
 * The authenticator to be used can be defined in the optional property
 * imap.authenticator. If not defined, the service defaults to the
 * IMAPBaasicAuthenticator implementation.
 * 
 * @author rsoika
 * @version 1.0
 */
@Stateless
public class IMAPImportService {

    public static final String OPTION_DEBUG = "debug";
    public static final String OPTION_ARCHIVE_FOLDER = "archive.folder";
    public static final String OPTION_SUBJECT_REGEX = "subject.regex";
    public static final String OPTION_IMAP_AUTHENTICATOR = "imap.authenticator";

    public static final String OPTION_DETACH_MODE = "detach.mode";
    public static final String OPTION_PRESERVE_ORIGIN = "preserve.origin";
    public static final String OPTION_GOTENBERG_SERVICE = "gotenberg.service";

    public static final String DETACH_MODE_PDF = "PDF";
    public static final String DETACH_MODE_ALL = "ALL";
    public static final String DETACH_MODE_NONE = "NONE";
    public static final String ARCHIVE_DEFAULT_NAME = "imixs-archive";

    public static final String DEFAULT_NO_SUBJECT = "no subject";

    private static Logger logger = Logger.getLogger(IMAPImportService.class.getName());

    @EJB
    WorkflowService workflowService;

    @EJB
    ModelService modelService;

    @EJB
    DocumentImportService documentImportService;

    @EJB
    MailMessageService mailMessageService;

    @Inject
    @Any
    protected Instance<IMAPAuthenticator> imapAuthenticators;

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
        String imapFolder = event.getSource().getItemValueString(DocumentImportService.SOURCE_ITEM_SELECTOR);

        Properties sourceOptions = documentImportService.getOptionsProperties(event.getSource());

        Pattern subjectPattern = null;
        String subjectRegex = sourceOptions.getProperty(OPTION_SUBJECT_REGEX, "");
        boolean debug = Boolean.getBoolean(sourceOptions.getProperty(OPTION_DEBUG, "false"));
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

        if (imapFolder.isEmpty()) {
            imapFolder = "INBOX";
        }
        try {
            Store store = null;
            // depending on the option "imap.authenticator" we use the corresponding IMAPAuthenticator
            // to open the mail store
            IMAPAuthenticator imapAuthenticator=null;
            String authenticatorClass = sourceOptions.getProperty(OPTION_IMAP_AUTHENTICATOR, "org.imixs.archive.importer.mail.IMAPBasicAuthenticator");
            for (IMAPAuthenticator _imapAuthenticator : this.imapAuthenticators) {
                // find the matching authenticator....
                if (authenticatorClass.equals(_imapAuthenticator.getClass().getName() )) {
                    documentImportService.logMessage("...IMAPAuthenticator = "+authenticatorClass, event);
                    imapAuthenticator=_imapAuthenticator;
                    break;
                }
            }
            store = imapAuthenticator.openMessageStore(event.getSource(), sourceOptions);

            documentImportService.logMessage("...connected to IMAP server: " + imapServer + " /" + imapFolder, event);

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
                Address[] fromAddress = null;
                String subject = null;
                // if we can not open the mail (messaging Exception) than we simply skipp this
                // mail
                // see Issue #175
                try {
                    fromAddress = message.getFrom();
                    subject = message.getSubject();
                } catch (MessagingException me) {
                    documentImportService.logMessage("...Failed to read message from inbox: " + me.getMessage(), event);
                    continue;
                }

                if (subject == null || subject.trim().isEmpty()) {
                    subject = DEFAULT_NO_SUBJECT;
                }
                Date sent = message.getSentDate();
                logger.fine("......received mail from: " + fromAddress[0].toString());
                logger.fine("......subject = " + subject);
                // do we have a subject regular expression provided by the options?
                if (subjectPattern != null) {
                    if (subject == null || DEFAULT_NO_SUBJECT.equals(subject)) {
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
                                        logger.warning("...skip detaching file, because of missing filename");
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
                                    byte[] content = IMAPImportHelper.readAllBytes(input);
                                    String contentType = mimeBodyPart.getContentType();
                                    // fix mimeType if application/octet-stream and file extension is .pdf
                                    // (issue #147)
                                    if (debug) {
                                        logger.info("mimetype=" + contentType);
                                    }
                                    if (IMAPImportHelper.isMediaTypeOctet(contentType, fileName)
                                        && fileName.toLowerCase().endsWith(".pdf")) {
                                        logger.info("...converting mimetype '" + contentType + "' to application/pdf");
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

        // Add import Information
        workitem.setItemValue("document.import.type", source.getItemValue("type"));
        workitem.setItemValue("document.import.selector", source.getItemValue("selector"));
        workitem.setItemValue("document.import.options", source.getItemValue("options"));

        return workitem;
    }


}
