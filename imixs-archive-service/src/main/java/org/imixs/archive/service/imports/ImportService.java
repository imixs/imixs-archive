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
package org.imixs.archive.service.imports;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.inject.Inject;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.archive.service.exports.ExportService;
import org.imixs.archive.service.util.FTPConnector;
import org.imixs.archive.service.util.MessageService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.QueryException;
import org.imixs.workflow.xml.XMLDocumentAdapter;

/**
 * The ImportService imports the workflow data form a FTP storage into the
 * cassandra cluster. The import can be started by the method start(). The
 * import is implemented as a SingleActionTimer so the import can run in
 * backgroud.
 * 
 * @version 1.0
 * @author rsoika
 */
@Stateless
public class ImportService {

    public final static String TIMER_ID_IMPORTSERVICE = "IMIXS_ARCHIVE_IMPORT_TIMER";

    public final static String ITEM_IMPORTPOINT = "import.point";
    public final static String ITEM_IMPORTCOUNT = "import.count";
    public final static String ITEM_IMPORTSIZE = "import.size";
    public final static String ITEM_IMPORTERRORS = "import.errors";

    public final static String MESSAGE_TOPIC = "import";
    public static final String FTP_ERROR = "FTP_ERROR";

    @Inject
    @ConfigProperty(name = ExportService.ENV_EXPORT_FTP_HOST)
    Optional<String> ftpServer;

    @Inject
    @ConfigProperty(name = ExportService.ENV_EXPORT_FTP_PATH)
    Optional<String> ftpPath;

    @Inject
    @ConfigProperty(name = ExportService.ENV_EXPORT_FTP_PORT, defaultValue = "21")
    int ftpPort;

    @Inject
    @ConfigProperty(name = ExportService.ENV_EXPORT_FTP_USER)
    Optional<String> ftpUser;

    @Inject
    @ConfigProperty(name = ExportService.ENV_EXPORT_FTP_PASSWORD)
    Optional<String> ftpPassword;

    @Inject
    DataService dataService;

    @Inject
    ClusterService clusterService;

    @Inject
    MessageService messageService;

    @Inject
    FTPConnector ftpConnector;

    @Inject
    ImportStatusHandler importStatusHandler;

    @Resource
    javax.ejb.TimerService timerService;

    private static Logger logger = Logger.getLogger(ImportService.class.getName());

    /**
     * This method initializes a new timer for the import ....
     * 
     * @throws ArchiveException
     */
    public void start() throws ArchiveException {
        Timer timer = null;

        // try to cancel an existing timer for this workflowinstance
        timer = findTimer();
        if (timer != null) {
            try {
                timer.cancel();
                timer = null;
            } catch (Exception e) {
                messageService.logMessage(MESSAGE_TOPIC, "Failed to stop existing timer - " + e.getMessage());
                throw new ArchiveException(ExportService.class.getName(), ArchiveException.INVALID_WORKITEM,
                        " failed to cancel existing timer!");
            }
        }

        logger.finest("...starting scheduler import-service ...");
        TimerConfig timerConfig = new TimerConfig();

        timerConfig.setInfo(TIMER_ID_IMPORTSERVICE);
        // New timer will start imediatly
        timer = timerService.createSingleActionTimer(0, timerConfig);
        // start and set statusmessage
        if (timer != null) {
            messageService.logMessage(MESSAGE_TOPIC, "Timer started.");
        }

    }

    /**
     * This is the method which imports the data from the FTP storage.
     * 
     * @throws Exception
     * @throws QueryException
     */
    @Timeout
    void onTimeout(javax.ejb.Timer timer) throws ArchiveException {
        importStatusHandler.setStatus(ImportStatusHandler.STAUS_RUNNING);
        ItemCollection metaData = null;
        long lastImportPoint = 0;
        long totalCount = 0;
        long importSize = 0;
        String lastUniqueID = null;

        if (!ftpServer.isPresent() || ftpServer.get().isEmpty()) {
            messageService.logMessage(MESSAGE_TOPIC,
                    "...Import failed - " + ExportService.ENV_EXPORT_FTP_HOST + " not defined!");
            return;
        }

        try {

            // load metadata and get last syncpoint
            metaData = dataService.loadMetadata();

            metaData.setItemValue(ITEM_IMPORTPOINT, lastImportPoint);
            metaData.setItemValue(ITEM_IMPORTCOUNT, 0);
            metaData.setItemValue(ITEM_IMPORTSIZE, 0);
            dataService.saveMetadata(metaData);

            messageService.logMessage(MESSAGE_TOPIC, "...starting import from " + ftpServer + "...");

            // init FTP Client
            FTPSClient ftpClient = new FTPSClient("TLS", false);
            ftpClient.setControlEncoding("UTF-8");
            ftpClient.connect(ftpServer.get(), ftpPort);
            if (ftpClient.login(ftpUser.get(), ftpPassword.get()) == false) {
                throw new ArchiveException(FTP_ERROR, "FTP file transfer failed: login failed!");
            }

            ftpClient.enterLocalPassiveMode();
//			ftpClient.setFileType(FTP.ASCII_FILE_TYPE);
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            ftpClient.setControlEncoding("UTF-8");

            ftpClient.setBufferSize(1024 * 64);

            // verify directories
            String ftpRootPath = ftpPath.get();
            if (!ftpRootPath.startsWith("/")) {
                ftpRootPath = "/" + ftpRootPath;
            }
            if (!ftpRootPath.endsWith("/")) {
                ftpRootPath = ftpRootPath + "/";
            }

            if (!ftpClient.changeWorkingDirectory(ftpRootPath)) {
                throw new ArchiveException(FTP_ERROR, "FTP file transfer failed: missing workfing directory '"
                        + ftpRootPath + "' : " + ftpClient.getReplyString());
            }

            // check subdirectories.....
            String ftpWorkingPath = "";
            FTPFile[] directoryListYears = ftpClient.listDirectories();
            for (FTPFile ftpFileYear : directoryListYears) {
                if (ftpFileYear.isDirectory()) {
                    ftpWorkingPath = ftpFileYear.getName();
                    if (!ftpClient.changeWorkingDirectory(ftpWorkingPath)) {
                        throw new ArchiveException(FTP_ERROR, "FTP file transfer failed: missing working directory '"
                                + ftpWorkingPath + "' : " + ftpClient.getReplyString());
                    }
                    // now switch to Month....
                    FTPFile[] directoryListMonths = ftpClient.listDirectories();
                    for (FTPFile ftpFileMonth : directoryListMonths) {
                        if (ftpFileMonth.isDirectory()) {
                            ftpWorkingPath = ftpFileMonth.getName();
                            if (!ftpClient.changeWorkingDirectory(ftpWorkingPath)) {
                                throw new ArchiveException(FTP_ERROR,
                                        "FTP file transfer failed: missing working directory '" + ftpWorkingPath
                                                + "' : " + ftpClient.getReplyString());
                            }

                            messageService.logMessage(MESSAGE_TOPIC,
                                    "......import: " + ftpFileYear.getName() + "/" + ftpWorkingPath + " ...");
                            // read all files....
                            int count = 0;
                            int verified = 0;
                            FTPFile[] importFiles = ftpClient.listFiles();
                            for (FTPFile importFile : importFiles) {
                                if (importFile.isFile()) {
                                    ItemCollection snapshot = ftpConnector.get(ftpClient, importFile.getName());
                                    if (snapshot != null) {
                                        verified++;
                                        if (!dataService.existSnapshot(snapshot.getUniqueID())) {
                                            // import!
                                            try {
                                                dataService.saveSnapshot(snapshot);
                                                logger.info("...." + snapshot.getUniqueID() + " successfull imported");
                                                count++;
                                                totalCount++;
                                                long _tmpSize = dataService
                                                        .calculateSize(XMLDocumentAdapter.getDocument(snapshot));
                                                logger.finest("......size=: " + _tmpSize);
                                                importSize = importSize + _tmpSize;
    
                                                lastImportPoint = dataService.getSnapshotTime(snapshot.getUniqueID());
                                            
                                            } catch (java.lang.IllegalArgumentException e) {
                                                logger.warning("Failed to import snapshot id '"+snapshot.getUniqueID()+"' - error: "+e.getMessage());
                                                // we continue....
                                            }
                                        }
                                    }
                                }
                                if (importStatusHandler.getStatus() == ImportStatusHandler.STAUS_CANCELED) {
                                    break;
                                }
                            }

                            messageService.logMessage(MESSAGE_TOPIC,
                                    "......" + ftpFileYear.getName() + "/" + ftpWorkingPath + ": " + verified
                                            + " snapshots verified, " + count + " snapshots imported...");
                            metaData.setItemValue(ITEM_IMPORTPOINT, lastImportPoint);
                            metaData.setItemValue(ITEM_IMPORTCOUNT, totalCount);
                            metaData.setItemValue(ITEM_IMPORTSIZE, importSize);
                            dataService.saveMetadata(metaData);
                            ftpClient.changeToParentDirectory();
                            if (importStatusHandler.getStatus() == ImportStatusHandler.STAUS_CANCELED) {
                                break;
                            }
                        }
                    }

                }
                if (importStatusHandler.getStatus() == ImportStatusHandler.STAUS_CANCELED) {
                    break;
                }
                ftpClient.changeToParentDirectory();
            }

        } catch (ArchiveException | RuntimeException | IOException e) {
            if (logger.isLoggable(Level.FINE)) {
                e.printStackTrace();
            }
            messageService.logMessage(MESSAGE_TOPIC, "Import failed "
                    + ("0".equals(lastUniqueID) ? " (failed to save metadata)" : "(last uniqueid=" + lastUniqueID + ")")
                    + " : " + e.getMessage());

        }

        messageService.logMessage(MESSAGE_TOPIC, "... import completed!");

        stop(findTimer());
    }

    /**
     * Stops the current import
     * 
     * @throws ArchiveException
     */
    public void cancel() throws ArchiveException {
        importStatusHandler.setStatus(ImportStatusHandler.STAUS_CANCELED);
        messageService.logMessage(MESSAGE_TOPIC, "... import canceled!");

        stop(findTimer());
    }

    /**
     * Cancels the running timer instance.
     * 
     * @throws ArchiveException
     */
    private void stop(Timer timer) throws ArchiveException {
        if (timer != null) {
            try {
                timer.cancel();
            } catch (Exception e) {
                messageService.logMessage(MESSAGE_TOPIC, "Failed to stop timer - " + e.getMessage());
            }
            // update status message
            messageService.logMessage(MESSAGE_TOPIC, "Timer stopped. ");
            importStatusHandler.setStatus(ImportStatusHandler.STAUS_STOPPED);
        }
    }

    /**
     * returns true if the service is running
     * 
     * @return
     */
    public boolean isRunning() {
        return (findTimer() != null);
    }

    /**
     * This method returns a timer for a corresponding id if such a timer object
     * exists.
     * 
     * @param id
     * @return Timer
     * @throws Exception
     */
    private Timer findTimer() {
        for (Object obj : timerService.getTimers()) {
            Timer timer = (javax.ejb.Timer) obj;
            if (TIMER_ID_IMPORTSERVICE.equals(timer.getInfo())) {
                return timer;
            }
        }
        return null;
    }
}
