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
package org.imixs.archive.backup.util;

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
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.backup.BackupApi;
import org.imixs.archive.backup.BackupException;
import org.imixs.archive.backup.BackupService;
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
    @ConfigProperty(name = BackupService.ENV_BACKUP_FTP_HOST)
    Optional<String> ftpServer;

    @Inject
    @ConfigProperty(name = BackupService.ENV_BACKUP_FTP_PATH)
    Optional<String> ftpPath;

    @Inject
    @ConfigProperty(name = BackupService.ENV_BACKUP_FTP_PORT)
    Optional<Integer> ftpPort;

    @Inject
    @ConfigProperty(name = BackupService.ENV_BACKUP_FTP_USER)
    Optional<String> ftpUser;

    @Inject
    @ConfigProperty(name = BackupService.ENV_BACKUP_FTP_PASSWORD)
    Optional<String> ftpPassword;

    /**
     * This method transfers a snapshot to a ftp server.
     *
     * @param snapshot
     * @param overwrite if true, existing files will be overwritten; if false, an
     *                  exception is thrown if file exists
     * @throws BackupException
     */
    public void put(FTPClient ftpClient, ItemCollection snapshot, boolean overwrite) throws BackupException {
        if (!ftpServer.isPresent() || !ftpPath.isPresent()) {
            throw new BackupException(FTP_ERROR,
                    "FTP file transfer failed: no ftp host provided (" + BackupService.ENV_BACKUP_FTP_HOST + ")!");
        }
        String snapshotID = snapshot.getUniqueID();
        logger.finest("......snapshotid=" + snapshotID);
        String originUnqiueID = BackupApi.getUniqueIDFromSnapshotID(snapshotID);
        logger.finest("......originUnqiueID=" + originUnqiueID);
        byte[] rawData;
        rawData = BackupApi.getRawData(snapshot);
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

        try {
            logger.finest("......put " + fileName + " to FTP server: " + ftpServer + "...");

            // verify directories
            if (!ftpClient.changeWorkingDirectory(ftpWorkingPath)) {
                throw new BackupException(FTP_ERROR, "FTP file transfer failed: missing working directory '"
                        + ftpWorkingPath + "' : " + ftpClient.getReplyString());
            }

            // test if we have the year as an subdirectory
            changeWorkingDirectory(ftpClient, new SimpleDateFormat("yyyy").format(created));
            changeWorkingDirectory(ftpClient, new SimpleDateFormat("MM").format(created));

            // Check if file already exists and handle overwrite logic
            FTPFile[] existingFiles = ftpClient.listFiles(fileName);
            if (existingFiles.length > 0 && !overwrite) {
                logger.info("......file " + fileName + " already exists, skipping upload (overwrite=false)");
                return; // Skip upload if file exists and overwrite is disabled
            }

            // upload file to FTP server.
            writer = new ByteArrayInputStream(rawData);
            if (!ftpClient.storeFile(fileName, writer)) {
                throw new BackupException(FTP_ERROR, "FTP file transfer failed: unable to write '" + ftpWorkingPath
                        + fileName + "' : " + ftpClient.getReplyString());
            }
            logger.finest("...." + ftpWorkingPath + fileName + " transferred successful to " + ftpServer);

        } catch (IOException e) {
            throw new BackupException(FTP_ERROR, "FTP file transfer failed: " + e.getMessage(), e);
        } finally {
            // do logout....
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                throw new BackupException(FTP_ERROR, "FTP file transfer failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Overloaded method for backward compatibility - defaults to overwrite = false
     */
    public void put(FTPClient ftpClient, ItemCollection snapshot) throws BackupException {
        put(ftpClient, snapshot, true);
    }

    /**
     * This method reads a snapshot form the current working directory
     *
     * @param snapshot
     * @throws BackupException
     * @return snapshot
     */
    public ItemCollection get(FTPClient ftpClient, String fileName) throws BackupException {

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
            throw new BackupException(FTP_ERROR, "FTP file transfer failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new FTPClient.
     * <p>
     * The client should be closed after usage!
     *
     * @return
     * @throws BackupException
     * @throws IOException
     * @throws SocketException
     */
    public FTPSClient getFTPClient() throws BackupException, SocketException, IOException {

        FTPSClient ftpClient = new FTPSClient("TLS", false);
        ftpClient.setControlEncoding("UTF-8");
        ftpClient.connect(ftpServer.orElse(""), ftpPort.orElse(21));
        if (ftpClient.login(ftpUser.orElse(""), ftpPassword.orElse("")) == false) {
            throw new BackupException(FTP_ERROR, "FTP file transfer failed: login failed!");
        }

        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

        return ftpClient;
    }

    /**
     * This method changes the current working sub-directy. If no corresponding
     * directory exits the method creats one.
     *
     * @throws BackupException
     */
    private void changeWorkingDirectory(FTPClient ftpClient, String subDirectory) throws BackupException {
        // test if we have the subdreictory
        try {
            if (!ftpClient.changeWorkingDirectory(subDirectory)) {
                // try to creat it....
                if (!ftpClient.makeDirectory(subDirectory)) {
                    throw new BackupException(FTP_ERROR, "FTP Error: unable to create sub-directory '" + subDirectory
                            + "' : " + ftpClient.getReplyString());
                }
                ftpClient.changeWorkingDirectory(subDirectory);
            }
        } catch (IOException e) {
            throw new BackupException(FTP_ERROR, "FTP file transfer failed: " + e.getMessage(), e);
        }
    }
}
