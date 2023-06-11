package org.imixs.archive.backup;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.imixs.archive.backup.util.LogController;

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
    @ConfigProperty(name = "health.endpoint", defaultValue = "http://localhost:9990/health")
    String healthEndpoint;

    @Inject
    @ConfigProperty(name = "metrics.endpoint", defaultValue = "http://localhost:9990/metrics")
    String metricsEndpoint;

    @Inject
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    MetricRegistry metricRegistry;

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

    @Inject
    BackupStatusHandler backupStatusHandler;

    public String getHealthEndpoint() {
        return healthEndpoint;
    }

    public String getMetricsEndpoint() {
        return metricsEndpoint;
    }

    public boolean isConnected() {
        String status = backupStatusHandler.getStatus();
        return (BackupStatusHandler.STATUS_RUNNING.equals(status)
                || BackupStatusHandler.STATUS_SCHEDULED.equals(status));
    }

    public String getStatus() {
        return backupStatusHandler.getStatus();
    }

    public Date getNextTimeout() {
        return backupStatusHandler.getNextTimeout();
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
            backupService.startScheduler(true);
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

    /**
     * This method returns the current event processing counter
     *
     * @return
     */
    public long getCounterByName(String name) {

        // find counter by name
        SortedMap<MetricID, Counter> allCounters = metricRegistry.getCounters();

        for (Map.Entry<MetricID, Counter> entry : allCounters.entrySet()) {

            MetricID metricID = entry.getKey();
            if (metricID.getName().endsWith(name)) {
                return entry.getValue().getCount();
            }
        }
        logger.warning("Metric Counter : " + name + " not found!");
        return 0;
    }
}