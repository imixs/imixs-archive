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
package org.imixs.archive.export.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.logging.Logger;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.export.ExportApi;
import org.imixs.archive.export.ExportException;
import org.imixs.workflow.FileData;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

/**
 * The FileService is used to write a file into a local directory or on a FTP
 * Server. The service is used by the SchedulerService.
 *
 * Depending on the environment variable {@code EXPORT_FTP_HOST} a file is
 * written/read from a FTP server or a local directory.
 *
 * The variable {@code EXPORT_PATH} defines the root path to write/read files.
 *
 * @version 1.0
 * @author rsoika
 */
@Stateless
@LocalBean
public class FileService {

    private static Logger logger = Logger.getLogger(FileService.class.getName());
    public static final String FTP_ERROR = "FTP_ERROR";

    @Inject
    LogService logService;

    @Inject
    @ConfigProperty(name = ExportApi.EXPORT_FTP_HOST)
    Optional<String> ftpServer;

    @Inject
    @ConfigProperty(name = ExportApi.EXPORT_FTP_PORT)
    Optional<Integer> ftpPort;

    @Inject
    @ConfigProperty(name = ExportApi.EXPORT_FTP_USER)
    Optional<String> ftpUser;

    @Inject
    @ConfigProperty(name = ExportApi.EXPORT_FTP_PASSWORD)
    Optional<String> ftpPassword;

    @Inject
    @ConfigProperty(name = ExportApi.EXPORT_PATH)
    Optional<String> filePath;

    /**
     * This method write a single file to the export path (optional on a FTP
     * Server). The method verifies if the file already exists and compares the
     * Checksum. If the file content (equals checksum) has not changed, the method
     * returns with no action.
     *
     * @param fileData - the File Data object
     * @param path     - optional sub directory
     */
    public void writeFileData(FileData fileData, String path) {

        // verify checksum if file exists
        try {
            String newChecksum = fileData.generateMD5();
            FileData oldFile = readFileData(fileData.getName(), path);
            if (oldFile != null && oldFile.generateMD5().equals(newChecksum)) {
                // content hast not changed.
                // no operation needed.
                return;
            }

            // do we have a ftp server?
            if (ftpServer.isPresent()) {
                // FTPSClient ftpClient = getFTPClient();
                ftpPut(fileData, path);

            } else {
                // write file to local file storage
                String ftpWorkingPath = computeWorkingDirectory(path);
                ftpWorkingPath = ftpWorkingPath + fileData.getName();
                logger.info("---> write file to: " + ftpWorkingPath);
                Path nioPath = Paths.get(ftpWorkingPath);
                Files.write(nioPath, fileData.getContent());
            }

        } catch (NoSuchAlgorithmException | ExportException | IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Reads a fileData from a given export path (optional on a FTP Server)..
     *
     * @param path
     */
    public FileData readFileData(String fileName, String path) {

        return null;

        // do we have a ftp server?

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
    private FTPSClient getFTPClient() throws ExportException, SocketException, IOException {

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
     * directory exits the method creates a new one.
     *
     * @throws ExportException
     */
    private void changeWorkingDirectory(FTPClient ftpClient, String subDirectory) throws ExportException {
        // test if we have the subdirectory
        try {
            if (!ftpClient.changeWorkingDirectory(subDirectory)) {
                // try to create it....
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

    /**
     * This method transfers a single byte to a ftp server.
     *
     * @param snapshot
     * @throws ExportException
     */
    private void ftpPut(FileData fileData, String path) throws ExportException {
        if (!ftpServer.isPresent() || !filePath.isPresent()) {
            throw new ExportException(FTP_ERROR,
                    "FTP file transfer failed: no ftp host provided (" + ExportApi.EXPORT_FTP_HOST + ")!");
        }

        String ftpWorkingPath = computeWorkingDirectory(path);
        InputStream writer = null;
        FTPClient ftpClient = null;
        try {
            logger.finest("......put " + fileData.getName() + " to FTP server: " + ftpServer + "...");
            ftpClient = getFTPClient();

            // verify directories
            changeWorkingDirectory(ftpClient, ftpWorkingPath);
            // upload file to FTP server.
            writer = new ByteArrayInputStream(fileData.getContent());

            if (!ftpClient.storeFile(fileData.getName(), writer)) {
                throw new ExportException(FTP_ERROR, "FTP file transfer failed: unable to write '" + ftpWorkingPath
                        + fileData.getName() + "' : " + ftpClient.getReplyString());
            }

            logger.finest("...." + ftpWorkingPath + fileData.getName() + " transferred successful to " + ftpServer);

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
     * Helper method computes the full target file path
     */
    private String computeWorkingDirectory(String path) {
        // Compute file path
        String ftpWorkingPath = filePath.orElse("");
        if (!ftpWorkingPath.startsWith("/")) {
            ftpWorkingPath = "/" + ftpWorkingPath;
        }
        if (path != null && !path.isEmpty()) {
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            ftpWorkingPath = ftpWorkingPath + path;
        }
        if (!ftpWorkingPath.endsWith("/")) {
            ftpWorkingPath = ftpWorkingPath + "/";
        }
        return ftpWorkingPath;
    }

    /**
     * This method reads a snapshot form the current working directory
     *
     * @param snapshot
     * @throws ExportException
     * @return snapshot
     */
    private FileData ftpGet(FTPClient ftpClient, String fileName) throws ExportException {

        long l = System.currentTimeMillis();
        ByteArrayOutputStream bos = null;

        try {
            logger.finest("......get " + fileName + "...");
            bos = new ByteArrayOutputStream();
            ftpClient.retrieveFile(fileName, bos);
            byte[] result = bos.toByteArray();

            logger.finest("......" + fileName + " transferred successful from " + ftpServer + " in "
                    + (System.currentTimeMillis() - l) + "ms");

            FileData fileData = new FileData(fileName, result, null, null);
            return fileData;
        } catch (IOException e) {
            throw new ExportException(FTP_ERROR, "FTP file transfer failed: " + e.getMessage(), e);
        }
    }

}
