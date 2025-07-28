package org.imixs.archive.backup;

import java.io.Serializable;
import java.util.Date;
import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.backup.util.LogController;
import org.imixs.workflow.ItemCollection;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * The FullBackupController is used to start and monitor a backup process of the
 * {@link FullBackupService}. The controller provides a processing log and shows
 * the current configuration and progress. This controller does not hold any
 * state.
 *
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class FullBackupController implements Serializable {

    private static final long serialVersionUID = 1L;

    private static Logger logger = Logger.getLogger(FullBackupController.class.getName());

    String syncSizeUnit = null;
    ItemCollection metaData = null;

    @Inject
    LogController logController;

    @Inject
    FullBackupStatusHandler fullBackupStatusHandler;

    @Inject
    FullBackupService fullBackupService;

    @Inject
    @ConfigProperty(name = BackupService.ENV_WORKFLOW_SERVICE_ENDPOINT)
    Optional<String> instanceEndpoint;

    @Inject
    @ConfigProperty(name = BackupService.ENV_BACKUP_FTP_HOST)
    Optional<String> ftpServer;

    @Inject
    @ConfigProperty(name = BackupService.ENV_BACKUP_FTP_PATH)
    Optional<String> ftpPath;

    @Inject
    @ConfigProperty(name = BackupService.ENV_BACKUP_FTP_PORT, defaultValue = "21")
    int ftpPort;

    public boolean isConnected() {
        String status = fullBackupStatusHandler.getStatus();
        return FullBackupStatusHandler.STATUS_RUNNING.equals(status);
    }

    public String getStatus() {
        return fullBackupStatusHandler.getStatus();
    }

    public Date getNextTimeout() {
        return fullBackupStatusHandler.getNextTimeout();
    }

    public String getFtpServer() {
        return ftpServer.orElse("");
    }

    public String getFtpPath() {
        return ftpPath.orElse("");
    }

    public int getFtpPort() {
        return ftpPort;
    }

    public String getInstanceEndpoint() {
        return instanceEndpoint.orElse("");
    }

    /**
     * Starts the timer service
     */
    public void start() {
        try {
            fullBackupService.startScheduler();
        } catch (BackupException e) {
            logController.warning(BackupService.TOPIC_BACKUP, e.getMessage());
        }
    }

    /**
     * Initialize a cancel request for the running timer service
     */
    public void stop() {
        try {
            fullBackupService.stopScheduler(FullBackupStatusHandler.STATUS_CANCELED);
        } catch (BackupException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

}