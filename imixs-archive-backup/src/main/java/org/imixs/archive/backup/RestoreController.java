package org.imixs.archive.backup;

import java.io.Serializable;
import java.util.Date;
import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.util.LogController;
import org.imixs.workflow.ItemCollection;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * The RestoreController is used to start and monitor a restore process of the
 * {@link RestoreService}. The controller provides a processing log and shows
 * the current configuration and progress. This controller does not hold any
 * state.
 *
 * @author rsoika
 *
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class RestoreController implements Serializable {

    private static final long serialVersionUID = 1L;

    private static Logger logger = Logger.getLogger(RestoreController.class.getName());

    String syncSizeUnit = null;
    ItemCollection metaData = null;

    @Inject
    LogController logController;

    @Inject
    RestoreStatusHandler restoreStatusHandler;

    @Inject
    RestoreService restoreService;

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

    public boolean isConnected() {
        String status = restoreStatusHandler.getStatus();
        return RestoreStatusHandler.STATUS_RUNNING.equals(status);
    }

    public String getStatus() {
        return restoreStatusHandler.getStatus();
    }

    public Date getNextTimeout() {
        return restoreStatusHandler.getNextTimeout();
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
            restoreService.startScheduler();
        } catch (BackupException e) {
            logController.warning(BackupApi.TOPIC_RESTORE, e.getMessage());
        }
    }

    /**
     * Initialize a cancel request for the running timer service
     */
    public void stop() {

        restoreStatusHandler.setStatus(RestoreStatusHandler.STATUS_CANCELED);

    }

}