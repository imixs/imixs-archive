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
package org.imixs.archive.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.logging.Logger;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.export.ExportApi;
import org.imixs.archive.export.ExportException;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.xml.XMLDocumentAdapter;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

/**
 * The FTPConnector service provides methods to push a snapshot document into an
 * FTP storage. The snapshot is stored in a directory based on the snapshot
 * creation date. E.g. $created=2017-03-19 will put the data into the sub
 * directory /2017/03/
 *
 * @version 1.0
 * @author rsoika
 */

@Stateless
public class FTPConnector {

    public static final String FTP_ERROR = "FTP_ERROR";

    private static Logger logger = Logger.getLogger(FTPConnector.class.getName());

    @Inject
    @ConfigProperty(name = ExportApi.ENV_EXPORT_FTP_HOST)
    Optional<String> ftpServer;

    @Inject
    @ConfigProperty(name = ExportApi.ENV_EXPORT_FTP_PATH)
    Optional<String> ftpPath;

    @Inject
    @ConfigProperty(name = ExportApi.ENV_EXPORT_FTP_PORT)
    Optional<Integer> ftpPort;

    @Inject
    @ConfigProperty(name = ExportApi.ENV_EXPORT_FTP_USER)
    Optional<String> ftpUser;

    @Inject
    @ConfigProperty(name = ExportApi.ENV_EXPORT_FTP_PASSWORD)
    Optional<String> ftpPassword;

    /**
     * This method transfers a snapshot to a ftp server.
     *
     * @param snapshot
     * @throws ExportException
     */
    public void put(ItemCollection snapshot) throws ExportException {
        if (!ftpServer.isPresent() || !ftpPath.isPresent()) {
            throw new ExportException(FTP_ERROR,
                    "FTP file transfer failed: no ftp host provided (" + ExportApi.ENV_EXPORT_FTP_HOST + ")!");
        }

        String snapshotID = snapshot.getUniqueID();

        logger.finest("......snapshotid=" + snapshotID);
        String originUnqiueID = ExportApi.getUniqueIDFromSnapshotID(snapshotID);

        logger.finest("......originUnqiueID=" + originUnqiueID);
        byte[] rawData;

        rawData = ExportApi.getRawData(snapshot);
        String fileName = originUnqiueID + ".xml";

        // Compute file path
        Date created = snapshot.getItemValueDate(WorkflowKernel.CREATED);
        String ftpWorkingPath = ftpPath.orElse("");
        if (!ftpWorkingPath.startsWith("/")) {
            ftpWorkingPath = "/" + ftpWorkingPath;
        }
        if (!ftpWorkingPath.endsWith("/")) {
            ftpWorkingPath = ftpWorkingPath + "/";
        }
        InputStream writer = null;

        FTPClient ftpClient = null;
        try {
            logger.finest("......put " + fileName + " to FTP server: " + ftpServer + "...");
            ftpClient = getFTPClient();

            // verify directories
            if (!ftpClient.changeWorkingDirectory(ftpWorkingPath)) {
                throw new ExportException(FTP_ERROR, "FTP file transfer failed: missing working directory '"
                        + ftpWorkingPath + "' : " + ftpClient.getReplyString());
            }
            // test if we have the year as an subdirecory
            changeWorkingDirectory(ftpClient, new SimpleDateFormat("yyyy").format(created));
            changeWorkingDirectory(ftpClient, new SimpleDateFormat("MM").format(created));

            // upload file to FTP server.
            writer = new ByteArrayInputStream(rawData);

            if (!ftpClient.storeFile(fileName, writer)) {
                throw new ExportException(FTP_ERROR, "FTP file transfer failed: unable to write '" + ftpWorkingPath
                        + fileName + "' : " + ftpClient.getReplyString());
            }

            logger.finest("...." + ftpWorkingPath + fileName + " transfered successfull to " + ftpServer);

        } catch (IOException e) {
            throw new ExportException(FTP_ERROR, "FTP file transfer failed: " + e.getMessage(), e);
        } finally {
            // do logout....
            try {
                if (writer != null) {
                    writer.close();
                }
                if (ftpClient != null) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException e) {
                throw new ExportException(FTP_ERROR, "FTP file transfer failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * This method reads a snapshot form the current working directory
     *
     * @param snapshot
     * @throws ExportException
     * @return snapshot
     */
    public ItemCollection get(FTPClient ftpClient, String fileName) throws ExportException {

        long l = System.currentTimeMillis();
        ByteArrayOutputStream bos = null;

        try {
            logger.finest("......get " + fileName + "...");
            bos = new ByteArrayOutputStream();
            ftpClient.retrieveFile(fileName, bos);
            byte[] result = bos.toByteArray();
            ItemCollection snapshot = XMLDocumentAdapter.readItemCollection(result);
            logger.finest("......" + fileName + " transfered successfull from " + ftpServer + " in "
                    + (System.currentTimeMillis() - l) + "ms");
            return snapshot;
        } catch (IOException | jakarta.xml.bind.JAXBException e) {
            throw new ExportException(FTP_ERROR, "FTP file transfer failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new FTPClient.
     * <p>
     * The client should be closed after usage!
     *
     * @return
     * @throws ExportException
     * @throws IOException
     * @throws SocketException
     */
    public FTPSClient getFTPClient() throws ExportException, SocketException, IOException {

        FTPSClient ftpClient = new FTPSClient("TLS", false);
        ftpClient.setControlEncoding("UTF-8");
        ftpClient.connect(ftpServer.orElse(""), ftpPort.orElse(21));
        if (ftpClient.login(ftpUser.orElse(""), ftpPassword.orElse("")) == false) {
            throw new ExportException(FTP_ERROR, "FTP file transfer failed: login failed!");
        }

        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

        return ftpClient;
    }

    /**
     * This method changes the current working sub-directy. If no corresponding
     * directory exits the method creats one.
     *
     * @throws ExportException
     */
    private void changeWorkingDirectory(FTPClient ftpClient, String subDirectory) throws ExportException {
        // test if we have the subdreictory
        try {
            if (!ftpClient.changeWorkingDirectory(subDirectory)) {
                // try to creat it....
                if (!ftpClient.makeDirectory(subDirectory)) {
                    throw new ExportException(FTP_ERROR, "FTP Error: unable to create sub-directory '" + subDirectory
                            + "' : " + ftpClient.getReplyString());
                }
                ftpClient.changeWorkingDirectory(subDirectory);
            }
        } catch (IOException e) {
            throw new ExportException(FTP_ERROR, "FTP file transfer failed: " + e.getMessage(), e);
        }
    }
}
