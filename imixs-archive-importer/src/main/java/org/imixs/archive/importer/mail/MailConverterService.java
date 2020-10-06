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
import java.text.DateFormat;
import java.util.Date;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

/**
 * The MailConverterService provides methods to convert a Mail Message object
 * into a html or pdf document.
 * <p>
 * The service resolves embedded images and converts them into HTML base64
 * images (img tags with a data uri):
 * 
 * @author rsoika
 * 
 */
@Stateless
@LocalBean
public class MailConverterService {

    private static Logger logger = Logger.getLogger(MailConverterService.class.getName());
    private static final DateFormat DATE_FORMATTER = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG);

    private static final Pattern IMG_CID_REGEX = Pattern.compile("cid:(.*?)\"", Pattern.DOTALL);

    private static final String HTML_WRAPPER_TEMPLATE_HEADER = "<!DOCTYPE html><html><head><style>body{font-size: 0.5cm;}</style><meta charset=\"utf-8\"><title>title</title></head><body>";
    private static final String HTML_WRAPPER_TEMPLATE_FOOTER = "</body></html>";

    /**
     * This method converts a MimeMessage object into HTML
     * <p>
     * A mail may contain text or HTML content. If the mail is a plain text mail,
     * than a HTML template will be used to convert the plain text into a HTML
     * presentation.
     * <p>
     * In case of a HTML mail, image source references to embedded mail parts will
     * be extracted into in-line content.
     * 
     * 
     * @throws MessagingException
     * @throws IOException
     * 
     * 
     * @throws Exception
     */
    public String convertToHTML(final MimeMessage message) throws IOException, MessagingException {
        logger.fine("Start converting MimeMessage...");
        String htmlResult = null;
        String rawText = null;

        // analyze all the available body parts of the message.
        Multipart multiPart = (Multipart) message.getContent();
        int countBodyParts = multiPart.getCount();
        for (int i = 0; i < countBodyParts; i++) {
            logger.fine("-------------------------------------------------------------");
            BodyPart bodyPart = multiPart.getBodyPart(i);
            String contentType = bodyPart.getContentType();
            logger.fine("Mail Bodypart-" + i + " contenttype=" + contentType);
            rawText = getText(bodyPart);
            if (rawText != null) {
                logger.fine("RawText=" + rawText);
                // is the content html?
                if (isHtmlContent(rawText)) {
                    // HTML content found!
                    // convert embedded images to in-line HTML code.
                    htmlResult = convertEmbeddedHTMLImages(rawText, bodyPart);
                    break;
                }
            }

        }

        // if we have not found a HTML content than we build a default html structure
        if (htmlResult == null) {
            htmlResult = buildHtmlfromPlainText(rawText);
        }

        // insert mail header
        htmlResult=insertEmailHeader(message,htmlResult);
        
        return htmlResult;
    }

   
    /**
     * This method extracts the embedded images for a htmlMultipart object and
     * converts the content to inline image HTML tags.
     * 
     */
    private String convertEmbeddedHTMLImages(String html, BodyPart htmlBodyPart)
            throws IOException, MessagingException {

        Matcher m = IMG_CID_REGEX.matcher(html);
        while (m.find()) {
            String toBeReplaced = m.group(0);
            String contentID = "<" + m.group(1) + ">";
            logger.fine("...contentID=" + contentID);
            logger.fine("...toBeReplaced=" + toBeReplaced);

            Multipart mp = (Multipart) htmlBodyPart.getContent();
            int partCount = mp.getCount();
            logger.fine(" found " + partCount + " embedded parts");
            for (int i = 0; i < mp.getCount(); i++) {
                Part bp = mp.getBodyPart(i);
                // test if this is the embedded image we are looking for..
                if (bp instanceof javax.mail.internet.MimeBodyPart) {
                    MimeBodyPart mbp = (MimeBodyPart) bp;
                    logger.fine("...verify part " + i);
                    logger.fine("...contentID=" + mbp.getContentID());
                    logger.fine("...contentType=" + mbp.getContentType());
                    if (mbp.getContentType().startsWith("image/")) {
                        // fetch content to byte array
                        ByteArrayOutputStream aos = new ByteArrayOutputStream();
                        mbp.writeTo(aos);

                        String imgContent = aos.toString();

                        String[] teile = imgContent.split("\\r\\n");
                        // find empty line...
                        String newContent = "";
                        boolean start = false;
                        for (String base64part : teile) {
                            if (start == true) {
                                newContent = newContent + base64part;
                            } else {
                                if (base64part.isEmpty()) {
                                    start = true;
                                }
                            }
                        }

                        String newDataSrc = "data:" + mbp.getContentType() + ";base64," + newContent;

                        html = html.replace(toBeReplaced, newDataSrc);
                        logger.fine("new data src=" + newDataSrc);
                    }

                }

            }

        }

        return html;
    }

    private String buildHtmlfromPlainText(String rawText) {
        String htmlResult = null;

        // if we have no html content than we build a default html structure
        // replace newline with <br>....
        rawText = rawText.replace("\n", "<br/>");
        rawText = rawText.replace("\r", "");
        // build a HTML template and embed the raw text...
        htmlResult = HTML_WRAPPER_TEMPLATE_HEADER;
        htmlResult = htmlResult + "<div style=\"white-space: pre-wrap\">";
        htmlResult = htmlResult + rawText + "</div>";
        htmlResult = htmlResult + HTML_WRAPPER_TEMPLATE_FOOTER;

        return htmlResult;
    }

    /**
     * Returns true if the content is a html page
     * 
     * @param content
     * @return
     */
    private boolean isHtmlContent(final String content) {
        // is the content html?
        String _tmp_content = content.trim();
        if (_tmp_content.startsWith("<html")) {
            return true;
        }
        if (_tmp_content.startsWith("<!DOCTYPE")) {
            return true;
        }

        return false;
    }

    /**
     * Return the primary text content of the mail message part.
     */
    private String getText(Part p) throws MessagingException, IOException {
        if (p.isMimeType("text/*")) {
            String s = (String) p.getContent();
            return s;
        }

        if (p.isMimeType("multipart/alternative")) {
            // prefer html text over plain text
            Multipart mp = (Multipart) p.getContent();
            String text = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/plain")) {
                    if (text == null)
                        text = getText(bp);
                    continue;
                } else if (bp.isMimeType("text/html")) {
                    String s = getText(bp);
                    if (s != null)
                        return s;
                } else {
                    return getText(bp);
                }
            }
            return text;
        } else if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = getText(mp.getBodyPart(i));
                if (s != null)
                    return s;
            }
        }

        return null;
    }

    
    /**
     * This helper method inserts a email header into a HTML mail with the subject and receiver information.
     * 
     * @param html
     * @return
     * @throws MessagingException 
     */
    private String insertEmailHeader(final MimeMessage message,String html) throws MessagingException {
        
        String subject = message.getSubject();
        
        String sentDateStr = null;
        try {
            Date sentDate = message.getSentDate();
            sentDateStr = DATE_FORMATTER.format(sentDate);
        } catch (Exception e) {
            // fallback to raw value
            sentDateStr = message.getHeader("date", null);
        }
       

        String from = message.getHeader("From", null);
        if (from == null) {
            from = message.getHeader("Sender", null);
        }
        try {
            from = MimeUtility.decodeText(MimeUtility.unfold(from));
        } catch (Exception e) {
            // ignore this error
        }

        String[] recipientsTo = new String[0];
        String recipientsRaw = message.getHeader("To", null);
        if (recipientsRaw != null && !recipientsRaw.isEmpty()) {
            try {
                recipientsRaw = MimeUtility.unfold(recipientsRaw);
                recipientsTo = recipientsRaw.split(",");
                for (int i = 0; i < recipientsTo.length; i++) {
                    recipientsTo[i] = MimeUtility.decodeText(recipientsTo[i]);
                }
            } catch (Exception e) {
                // ignore this error
            }
        }
        
        String[] recipientsCC= new String[0];
         recipientsRaw = message.getHeader("CC", null);
        if (recipientsRaw != null && !recipientsRaw.isEmpty()) {
            try {
                recipientsRaw = MimeUtility.unfold(recipientsRaw);
                recipientsCC = recipientsRaw.split(",");
                for (int i = 0; i < recipientsCC.length; i++) {
                    recipientsCC[i] = MimeUtility.decodeText(recipientsCC[i]);
                }
            } catch (Exception e) {
                // ignore this error
            }
        }

        
        String[] recipientsBCC= new String[0];
        recipientsRaw = message.getHeader("BCC", null);
       if (recipientsRaw != null && !recipientsRaw.isEmpty()) {
           try {
               recipientsRaw = MimeUtility.unfold(recipientsRaw);
               recipientsBCC = recipientsRaw.split(",");
               for (int i = 0; i < recipientsBCC.length; i++) {
                   recipientsBCC[i] = MimeUtility.decodeText(recipientsBCC[i]);
               }
           } catch (Exception e) {
               // ignore this error
           }
       }
        
       
       
       // build a html header box....
       String mailHeader="<table style=\"border:none;\">\n" + 
               "  <tr><td><strong>Date: </strong></td><td>" + sentDateStr + "</td></tr>\n" + 
               "  <tr><td><strong>From: </strong></td><td>" + from + "</td></tr>\n" + 
               "  <tr><td><strong>Subject: </strong></td><td>" + subject + "</td></tr>\n" + 
               "  <tr><td><strong>To: </strong></td><td>" +  String.join(",", recipientsTo)  + "</td></tr>\n";
       
       if (recipientsCC.length>0) {
           mailHeader=mailHeader+  "  <tr><td><strong>CC: </strong></td><td>" +  String.join(",", recipientsCC)  + "</td></tr>\n";
           
       }
       if (recipientsBCC.length>0) {
           mailHeader=mailHeader+  "  <tr><td><strong>BCC: </strong></td><td>" +  String.join(",", recipientsBCC)  + "</td></tr>\n";
           
       }

       mailHeader=mailHeader+  "</table><hr />";
       
       
       // insert after <body
       int iPos=html.toLowerCase().indexOf("<body");
       iPos=html.indexOf(">",iPos+4)+1;
       
       String result=html.substring(0,iPos);
       result=result+mailHeader;
       result=result+html.substring(iPos);
       
       return result;
       
    }
    
}
