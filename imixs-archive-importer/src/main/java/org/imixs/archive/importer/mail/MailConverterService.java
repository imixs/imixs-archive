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
        logger.info("Start converting");
        String htmlResult = null;
        String rawText = null;

        // analyze all the available body parts of the message.
        Multipart multiPart = (Multipart) message.getContent();
        int countBodyParts = multiPart.getCount();
        for (int i = 0; i < countBodyParts; i++) {
            logger.info("-------------------------------------------------------------");
            BodyPart bodyPart = multiPart.getBodyPart(i);
            String contentType = bodyPart.getContentType();
            logger.info("Mail Bodypart-" + i + " contenttype=" + contentType);
            rawText = getText(bodyPart);
            if (rawText != null) {
                logger.info("RawText=" + rawText);
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
            logger.info("...contentID=" + contentID);
            logger.info("...toBeReplaced=" + toBeReplaced);

            Multipart mp = (Multipart) htmlBodyPart.getContent();
            int partCount = mp.getCount();
            logger.info(" found " + partCount + " embedded parts");
            for (int i = 0; i < mp.getCount(); i++) {
                Part bp = mp.getBodyPart(i);
                // test if this is the embedded image we are looking for..
                if (bp instanceof javax.mail.internet.MimeBodyPart) {
                    MimeBodyPart mbp = (MimeBodyPart) bp;
                    logger.info("...verify part " + i);
                    logger.info("...contentID=" + mbp.getContentID());
                    logger.info("...contentType=" + mbp.getContentType());
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
                        logger.info("new data src=" + newDataSrc);
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

}
