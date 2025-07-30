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
import java.util.logging.Logger;

import jakarta.ws.rs.core.MediaType;

/**
 * A helper bean with static methods
 * 
 * @author rsoika
 * @version 1.0
 */
public class IMAPImportHelper {
    private static Logger logger = Logger.getLogger(IMAPImportHelper.class.getName());

    /**
     * This method returns true if the mediaType of a file attachment is
     * <p>
     * "application/octet-stream"
     * <p>
     * In some cases we have a situation where the contentType is
     * "application/octet" which is not a valid content type.
     * Also in this case we return true!
     * 
     * @param contentType
     * @return
     */
    public static boolean isMediaTypeOctet(String contentType, String filename) {

        if (contentType.contains(MediaType.APPLICATION_OCTET_STREAM)) {
            return true;
        }

        if (contentType.toLowerCase().startsWith("application/octet")) {
            // print a warning for later analysis
            logger.warning("Unknow ContentType: " + contentType + " - in " + filename);
            // Issue #167
            return true;
        }

        // no octet type
        return false;
    }

    /**
     * Read inputstream into a byte array.
     * 
     * @param inputStream
     * @return
     * @throws IOException
     */
    public static byte[] readAllBytes(InputStream inputStream) throws IOException {
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

    /**
     * Fixes malformed MIME content types in file attachments.
     * 
     * Some email clients or servers generate incorrect MIME types with invalid
     * characters (dots, spaces) around the slash separator. This method cleans
     * up these common formatting errors to ensure proper content type recognition.
     *
     * Common fixes applied:
     * - application/.pdf → application/pdf
     * - text /html → text/html
     * - image. /jpeg → image/jpeg
     * - application . /pdf → application/pdf
     *
     * The method also converts 'application/octet-stream' into 'application/pdf' if
     * the file is a pdf file.
     * 
     * prefixes will be striped automatically.
     * 
     * @param snapshot the ItemCollection containing FileData objects to fix
     */
    public static String fixContentType(String contentType, String fileName, boolean debug) {

        if (contentType == null || contentType.isEmpty()) {
            // no op
            return contentType;
        }

        // Remove invalid characters (dots and spaces) around the slash separator
        // Regex pattern: [\\s.]* matches zero or more spaces or dots
        String fixedContentType = contentType.replaceAll("[\\s.]*(/)[\\s.]*", "$1");
        if (!contentType.equals(fixedContentType)) {
            // Optional: Log the fix for debugging
            logger.info("...fixed content type: '" + contentType + "' -> '" + fixedContentType + "'");
            contentType = fixedContentType;
        }

        // fix mimeType if application/octet-stream and file extension is .pdf
        // (issue #147)
        if (IMAPImportHelper.isMediaTypeOctet(contentType, fileName)
                && fileName.toLowerCase().endsWith(".pdf")) {
            logger.info("...converting mimetype '" + contentType + "' to application/pdf");
            contentType = "application/pdf";
        }
        // strip ; prefixes
        if (contentType.contains(";")) {
            contentType = contentType.substring(0, contentType.indexOf(";"));
        }

        if (debug) {
            logger.info("mimetype=" + contentType);
        }
        return contentType;
    }
}
