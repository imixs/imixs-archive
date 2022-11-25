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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;

import javax.inject.Named;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.net.ssl.HttpsURLConnection;

import org.imixs.archive.importer.DocumentImportService;
import org.imixs.workflow.ItemCollection;

/**
 * The IMAPOutlookAuthenticator authenticates against Microsoft Outlook using
 * OAUTH2.
 * <p>
 * This authenticator ignores the Source Server setting.
 * 
 * 
 * @see IMAPImportService
 * @author rsoika
 * @version 1.0
 */
@Named
public class IMAPOutlookAuthenticator implements IMAPAuthenticator, Serializable {
    private static final long serialVersionUID = 1L;
    private static Logger logger = Logger.getLogger(IMAPOutlookAuthenticator.class.getName());

    /**
     * This method returns a MailStore object based on a given Configuration
     * 
     * @param sourceConfig
     * @param sourceOptions
     * @return
     * @throws NumberFormatException
     * @throws MessagingException
     */
    @SuppressWarnings("unused")
    public Store openMessageStore(ItemCollection sourceConfig, Properties sourceOptions) throws MessagingException {
        String imapServer = sourceConfig.getItemValueString(DocumentImportService.SOURCE_ITEM_SERVER);
        String imapPort = sourceConfig.getItemValueString(DocumentImportService.SOURCE_ITEM_PORT);
        String imapUser = sourceConfig.getItemValueString(DocumentImportService.SOURCE_ITEM_USER);
        String imapPassword = sourceConfig.getItemValueString(DocumentImportService.SOURCE_ITEM_PASSWORD);

        boolean debug = Boolean.getBoolean(sourceOptions.getProperty(IMAPImportService.OPTION_DEBUG, "false"));
        
        // create an empty properties object...
        // Properties props = System.getProperties();
        Properties imapProperties = new Properties();

        // now we parse the mail properties provided by the options....
        @SuppressWarnings("unchecked")
        Enumeration<String> enums = (Enumeration<String>) sourceOptions.propertyNames();
        while (enums.hasMoreElements()) {
            String key = enums.nextElement();
            if (key.startsWith("mail.")) {
                // add key...
                imapProperties.setProperty(key, sourceOptions.getProperty(key));
                if (debug) {
                    logger.info("......setting property from source options: " + key);
                }
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

        imapProperties.put("mail.store.protocol", "imap");
        imapProperties.put("mail.imap.host", "outlook.office365.com");
        imapProperties.put("mail.imap.port", imapPort);
        imapProperties.put("mail.imap.ssl.enable", "true");

        imapProperties.put("mail.imap.starttls.enable", "true");
        imapProperties.put("mail.imap.auth", "true");
        imapProperties.put("mail.imap.auth.mechanisms", "XOAUTH2");
        imapProperties.put("mail.imap.user", imapUser);

        String tenantId = sourceOptions.getProperty("microsoft.tenantid");
        String clientId = sourceOptions.getProperty("microsoft.clientid");

        String token = null;
        Store store = null;
        try {
            token = getAuthToken(tenantId, clientId, imapPassword);
            Session session = Session.getInstance(imapProperties);
            // debug mode?
            if (debug) {
                session.setDebug(true);
            }
            store = session.getStore("imap");
            store.connect("outlook.office365.com", imapUser, token);
        } catch (Exception e) {
            e.printStackTrace();
            logger.severe("Failed to connect: " + e.getMessage());
        }
        return store;
    }

    /**
     * Helper method to receive a Microsoft access Token
     * 
     * 
     * @return
     * @throws IOException
     * @throws ClientProtocolException
     */
    public String getAuthToken(String tenantId, String clientId, String client_secret) throws IOException {
        String sURL = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        logger.finest("...oauth login url="+sURL);
        HttpsURLConnection httpClient = (HttpsURLConnection) new URL(sURL).openConnection();

        // add request header
        httpClient.setRequestMethod("POST");
        httpClient.setRequestProperty("User-Agent", "Mozilla/5.0");
        httpClient.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

        // Build Outlook Request...
        String scopes = "https://outlook.office365.com/.default";
        String urlParameters = "client_id=" + clientId + "&scope=" + scopes + "&client_secret=" + client_secret
                + "&grant_type=client_credentials";
        // Send post request
        httpClient.setDoOutput(true);
        try (DataOutputStream wr = new DataOutputStream(httpClient.getOutputStream())) {
            wr.writeBytes(urlParameters);
            wr.flush();
        }

        int responseCode = httpClient.getResponseCode();
        if (responseCode >= 200 && responseCode <= 299) {
            // Extract token form content
            byte[] response = IMAPImportHelper.readAllBytes(httpClient.getInputStream());
            JsonReader jsonReader = Json.createReader(new StringReader(new String(response)));
            JsonObject jsonObject = jsonReader.readObject();
            String token = jsonObject.getString("access_token");
            logger.fine("....access token = " + token);
            return token;
        } else {
            logger.severe("Failed to receive a valid token!");
            return null;
        }
    }

}
