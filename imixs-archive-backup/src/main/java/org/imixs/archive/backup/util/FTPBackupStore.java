package org.imixs.archive.backup.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.imixs.archive.backup.BackupException;
import org.imixs.workflow.ItemCollection;

/**
 * BackupStore implementation for a FTP server. The class delegates the file
 * transfer to the existing {@link FTPConnector} so the FTP behavior remains
 * unchanged.
 * <p>
 * The FTP connection is opened in the constructor and closed by
 * {@link #close()}.
 *
 * @author rsoika
 */
public class FTPBackupStore implements BackupStore {

    private static Logger logger = Logger.getLogger(FTPBackupStore.class.getName());

    private final FTPConnector ftpConnector;
    private final String rootPath;
    private FTPSClient ftpClient = null;

    /**
     * Opens a new FTP connection and verifies that the root directory exists.
     *
     * @param ftpConnector the FTP connector bean
     * @param ftpPath      the configured backup root directory
     * @throws BackupException if the connection fails or the root directory is
     *                         missing
     */
    public FTPBackupStore(FTPConnector ftpConnector, String ftpPath) throws BackupException {
        this.ftpConnector = ftpConnector;
        this.rootPath = normalizeRootPath(ftpPath);
        try {
            ftpClient = ftpConnector.getFTPClient();
            // the root directory must exist - it is never created automatically
            if (!ftpClient.changeWorkingDirectory(rootPath)) {
                String reply = ftpClient.getReplyString();
                close();
                throw new BackupException(FTPConnector.FTP_ERROR,
                        "FTP file transfer failed: missing working directory '" + rootPath + "' : " + reply);
            }
        } catch (IOException e) {
            close();
            throw new BackupException(FTPConnector.FTP_ERROR, "FTP connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void put(ItemCollection snapshot, boolean overwrite) throws BackupException {
        ftpConnector.put(ftpClient, snapshot, overwrite);
    }

    @Override
    public List<String> listDirectories(String path) throws BackupException {
        try {
            FTPFile[] files = ftpClient.listDirectories(toAbsolutePath(path));
            return toSortedNames(files, true);
        } catch (IOException e) {
            throw new BackupException(FTPConnector.FTP_ERROR,
                    "FTP list directories failed for '" + path + "': " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listFiles(String path) throws BackupException {
        try {
            FTPFile[] files = ftpClient.listFiles(toAbsolutePath(path));
            return toSortedNames(files, false);
        } catch (IOException e) {
            throw new BackupException(FTPConnector.FTP_ERROR,
                    "FTP list files failed for '" + path + "': " + e.getMessage(), e);
        }
    }

    @Override
    public ItemCollection get(String path) throws BackupException {
        return ftpConnector.get(ftpClient, toAbsolutePath(path));
    }

    @Override
    public void close() {
        if (ftpClient != null) {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException e) {
                logger.fine("Failed to close ftp connection: " + e.getMessage());
            }
            ftpClient = null;
        }
    }

    /**
     * Ensures the root path starts and ends with a '/'.
     */
    private static String normalizeRootPath(String path) {
        String result = (path == null) ? "" : path.trim();
        if (!result.startsWith("/")) {
            result = "/" + result;
        }
        if (!result.endsWith("/")) {
            result = result + "/";
        }
        return result;
    }

    /**
     * Converts a relative path into an absolute FTP path.
     */
    private String toAbsolutePath(String path) {
        if (path == null || path.isBlank()) {
            return rootPath;
        }
        String relative = path.startsWith("/") ? path.substring(1) : path;
        return rootPath + relative;
    }

    /**
     * Extracts the sorted file names. Some FTP servers return the full path in the
     * file name, so only the last path segment is used. The entries '.' and '..'
     * are ignored.
     */
    private static List<String> toSortedNames(FTPFile[] files, boolean directories) {
        List<String> result = new ArrayList<>();
        if (files == null) {
            return result;
        }
        for (FTPFile file : files) {
            if (file == null) {
                continue;
            }
            if (directories ? !file.isDirectory() : !file.isFile()) {
                continue;
            }
            String name = file.getName();
            int pos = name.lastIndexOf('/');
            if (pos >= 0) {
                name = name.substring(pos + 1);
            }
            if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
                continue;
            }
            result.add(name);
        }
        Collections.sort(result);
        return result;
    }
}