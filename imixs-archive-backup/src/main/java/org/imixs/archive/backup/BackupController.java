package org.imixs.archive.backup;

import java.io.Serializable;
import java.util.Date;
import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.util.LogController;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * The BackupController is used to monitor the backup status of the
 * {@link BackupService}. The controller provides a processing log and shows the
 * current configuration. This controller does not hold any state.
 *
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class BackupController implements Serializable {

    private static final long serialVersionUID = 7027147503119012594L;

    @Inject
    @ConfigProperty(name = BackupApi.WORKFLOW_SERVICE_ENDPOINT)
    Optional<String> instanceEndpoint;

    @Inject
    @ConfigProperty(name = BackupApi.ENV_BACKUP_FTP_HOST)
    Optional<String> ftpServer;

    @Inject
    @ConfigProperty(name = BackupApi.ENV_BACKUP_FTP_PATH)
    Optional<String> ftpPath;

    @Inject
    @ConfigProperty(name = BackupApi.ENV_BACKUP_FTP_PORT, defaultValue = "21")
    int ftpPort;

    // timeout interval in ms
    @Inject
    @ConfigProperty(name = BackupApi.WORKFLOW_SYNC_INTERVAL, defaultValue = "1000")
    long interval;

    @SuppressWarnings("unused")
    private static Logger logger = Logger.getLogger(BackupController.class.getName());

    @Inject
    BackupService backupService;

    @Inject
    LogController logController;

    public boolean isConnected() {
        String status = backupService.getStatus();
        return "scheduled".equals(status) || "running".equals(status);
    }

    public String getStatus() {
        return backupService.getStatus();
    }

    public Date getNextTimeout() {
        return backupService.getNextTimeout();
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

    public long getInterval() {
        return interval;
    }

    /**
     * Starts the timer service
     */
    public void start() {
        try {
            backupService.startScheduler();
        } catch (BackupException e) {
            logController.warning(BackupApi.TOPIC_BACKUP, e.getMessage());
        }
    }

    /**
     * Stop the timer service
     */
    public void stop() {
        try {
            backupService.stopScheduler();
        } catch (BackupException e) {
            logController.warning(BackupApi.TOPIC_BACKUP, e.getMessage());
        }
    }

}