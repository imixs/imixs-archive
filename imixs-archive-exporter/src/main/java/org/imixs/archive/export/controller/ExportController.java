package org.imixs.archive.export.controller;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.imixs.archive.export.ExportApi;
import org.imixs.archive.export.ExportException;
import org.imixs.archive.export.services.ExportService;
import org.imixs.archive.export.services.ExportStatusHandler;

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
    @ConfigProperty(name = "health.endpoint", defaultValue = "http://localhost:9990/health")
    String healthEndpoint;

    @Inject
    @ConfigProperty(name = "metrics.endpoint", defaultValue = "http://localhost:9990/metrics")
    String metricsEndpoint;

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

    @Inject
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    MetricRegistry metricRegistry;

    @SuppressWarnings("unused")
    private static Logger logger = Logger.getLogger(ExportController.class.getName());

    @Inject
    ExportService exportService;
    @Inject
    ExportStatusHandler exportStatusHandler;

    public String getHealthEndpoint() {
        return healthEndpoint;
    }

    public String getMetricsEndpoint() {
        return metricsEndpoint;
    }

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
            exportService.warning(ExportApi.EVENTLOG_TOPIC_EXPORT, e.getMessage());
        }
    }

    /**
     * Stop the timer service
     */
    public void stop() {
        try {
            exportService.stopScheduler();
        } catch (ExportException e) {
            exportService.warning(ExportApi.EVENTLOG_TOPIC_EXPORT, e.getMessage());
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

    public List<String> getLogEntries(String context) {
        return exportService.getLogEntries();// logTopics.get(context);
    }
}