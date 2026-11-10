package org.imixs.archive.backup.util;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.backup.BackupException;
import org.imixs.archive.backup.BackupService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The BackupStoreFactory validates the storage configuration and creates the
 * matching {@link BackupStore}.
 * <p>
 * FTP storage and local storage are strictly mutually exclusive:
 * <ul>
 * <li>BACKUP_FTP_HOST + BACKUP_FTP_PATH : FTP storage</li>
 * <li>BACKUP_LOCAL_PATH : local file system storage</li>
 * </ul>
 * Any other combination is rejected. This prevents a misspelled FTP variable
 * from silently switching the backup to the local file system.
 *
 * @author rsoika
 */
@ApplicationScoped
public class BackupStoreFactory {

    public static final String MODE_FTP = "FTP";
    public static final String MODE_LOCAL = "LOCAL";
    public static final String CONFIG_ERROR = "CONFIG_ERROR";

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
    @ConfigProperty(name = BackupService.ENV_BACKUP_LOCAL_PATH)
    Optional<String> localPath;

    @Inject
    FTPConnector ftpConnector;

    /**
     * Validates the storage configuration and returns the storage mode.
     *
     * @return MODE_FTP or MODE_LOCAL
     * @throws BackupException if the configuration is invalid
     */
    public String validateConfig() throws BackupException {
        boolean ftpHostSet = isSet(ftpServer);
        boolean ftpPathSet = isSet(ftpPath);
        boolean localPathSet = isSet(localPath);

        if (ftpHostSet && localPathSet) {
            throw new BackupException(CONFIG_ERROR,
                    "Invalid storage configuration: BACKUP_FTP_HOST and BACKUP_LOCAL_PATH must not be set both!");
        }
        if (localPathSet) {
            if (ftpPathSet) {
                throw new BackupException(CONFIG_ERROR,
                        "Invalid storage configuration: BACKUP_FTP_PATH must not be set together with BACKUP_LOCAL_PATH!");
            }
            return MODE_LOCAL;
        }
        if (ftpHostSet) {
            if (!ftpPathSet) {
                throw new BackupException(CONFIG_ERROR, "Invalid storage configuration: BACKUP_FTP_PATH is missing!");
            }
            return MODE_FTP;
        }
        throw new BackupException(CONFIG_ERROR,
                "Invalid storage configuration: either BACKUP_FTP_HOST/BACKUP_FTP_PATH or BACKUP_LOCAL_PATH must be set!");
    }

    /**
     * Returns the storage mode or null if the configuration is invalid.
     */
    public String getMode() {
        try {
            return validateConfig();
        } catch (BackupException e) {
            return null;
        }
    }

    /**
     * Returns the configuration error message or an empty string if the
     * configuration is valid.
     */
    public String getConfigError() {
        try {
            validateConfig();
            return "";
        } catch (BackupException e) {
            return e.getMessage();
        }
    }

    /**
     * Returns a short human readable description of the backup storage, used for
     * logging.
     */
    public String getStorageDescription() {
        String mode = getMode();
        if (MODE_FTP.equals(mode)) {
            return "ftp://" + ftpServer.get() + ":" + ftpPort.orElse(21) + ftpPath.get();
        }
        if (MODE_LOCAL.equals(mode)) {
            return "local:" + localPath.get();
        }
        return "invalid storage configuration";
    }

    /**
     * Validates the configuration and opens a new BackupStore. The store must be
     * closed after usage.
     *
     * @throws BackupException if the configuration is invalid or the storage is not
     *                         accessible
     */
    public BackupStore openStore() throws BackupException {
        String mode = validateConfig();
        if (MODE_LOCAL.equals(mode)) {
            return new LocalBackupStore(localPath.get());
        }
        return new FTPBackupStore(ftpConnector, ftpPath.get());
    }

    private static boolean isSet(Optional<String> value) {
        return value.isPresent() && !value.get().isBlank();
    }
}