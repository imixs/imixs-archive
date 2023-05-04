package org.imixs.archive.export;

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
 * The ExportController is used to monitor the export status of the
 * {@link ExportService}. The controller provides a processing log and shows the
 * current configuration. This controller does not hold any state.
 *
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class ExportController implements Serializable {

    private static final long serialVersionUID = 7027147503119012594L;

    @Inject
    @ConfigProperty(name = ExportApi.WORKFLOW_SERVICE_ENDPOINT)
    Optional<String> instanceEndpoint;

    @Inject
    @ConfigProperty(name = ExportApi.ENV_EXPORT_FTP_HOST)
    Optional<String> ftpServer;

    @Inject
    @ConfigProperty(name = ExportApi.ENV_EXPORT_FTP_PATH)
    Optional<String> ftpPath;

    @Inject
    @ConfigProperty(name = ExportApi.ENV_EXPORT_FTP_PORT, defaultValue = "21")
    int ftpPort;

    // timeout interval in ms
    @Inject
    @ConfigProperty(name = ExportApi.WORKFLOW_SYNC_INTERVAL, defaultValue = "1000")
    long interval;

    @SuppressWarnings("unused")
    private static Logger logger = Logger.getLogger(ExportController.class.getName());

    @Inject
    ExportService exportService;

    @Inject
    LogController logController;

    @Inject
    ExportStatusHandler exportStatusHandler;

    public boolean isConnected() {
        String status = exportStatusHandler.getStatus();
        return (ExportStatusHandler.STATUS_RUNNING.equals(status)
                || ExportStatusHandler.STATUS_SCHEDULED.equals(status));
    }

    public String getStatus() {
        return exportStatusHandler.getStatus();
    }

    public Date getNextTimeout() {
        return exportStatusHandler.getNextTimeout();
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
            exportService.startScheduler(true);
        } catch (ExportException e) {
            logController.warning(ExportApi.TOPIC_EXPORT, e.getMessage());
        }
    }

    /**
     * Stop the timer service
     */
    public void stop() {
        try {
            exportService.stopScheduler();
        } catch (ExportException e) {
            logController.warning(ExportApi.TOPIC_EXPORT, e.getMessage());
        }
    }

}