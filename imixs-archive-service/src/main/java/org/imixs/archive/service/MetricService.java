package org.imixs.archive.service;

import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.ObserverException;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.imixs.archive.service.cassandra.ArchiveEvent;
import org.imixs.workflow.exceptions.AccessDeniedException;

/**
 * The Imixs MetricSerivce is a monitoring resource for Imixs-Archive in the
 * prometheus format. The MetricService is based on Microprofile 2.2 and
 * MP-Metric-API 2.2
 * <p>
 * A metric is created each time when an ArchiveEvent is fired. The service
 * exports metrics in prometheus text format.
 * <p>
 * The service provides counter metrics. A counter will always increase. To
 * extract the values in prometheus use the rate function - Example:
 * <p>
 * <code>rate(http_requests_total[5m])</code>
 * <p>
 * The service expects MP Metrics v2.0. A warning is logged if corresponding
 * version is missing.
 * 
 * @See https://www.robustperception.io/how-does-a-prometheus-counter-work
 * @author rsoika
 * @version 1.0
 */
@ApplicationScoped
public class MetricService {

    public static final String METRIC_DOCUMENTS = "documents";

    @Inject
    @ConfigProperty(name = "metrics.enabled", defaultValue = "false")
    private boolean metricsEnabled;

    @Inject
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    MetricRegistry metricRegistry;

    boolean mpMetricNoSupport = false;
    private static Logger logger = Logger.getLogger(MetricService.class.getName());

    /**
     * ProcessingEvent listener to generate a metric.
     * 
     * @param archiveEvent
     * @throws AccessDeniedException
     */
    public void onArchiveEvent(@Observes ArchiveEvent archiveEvent) throws AccessDeniedException {

        if (!metricsEnabled) {
            return;
        }
        if (archiveEvent == null) {
            return;
        }
        if (mpMetricNoSupport) {
            // missing MP Metric support!
            return;
        }

        try {
            Counter counter = buildArchiveMetric(archiveEvent);
            counter.inc();
        } catch (IncompatibleClassChangeError | ObserverException oe) {
            mpMetricNoSupport = true;
            logger.warning("...Microprofile Metrics v2.2 not supported!");
        }

    }

    /**
     * This method builds a prometheus metric from a DocumentEvent object containing
     * the lables save, load delete
     * 
     * @return
     */
    private Counter buildArchiveMetric(ArchiveEvent event) {
        // Constructs a Metadata object from a map with the following keys:
        // - name - The name of the metric
        // - displayName - The display (friendly) name of the metric
        // - description - The description of the metric
        // - type - The type of the metric
        // - tags - The tags of the metric - cannot be null
        // - reusable - If true, this metric name is permitted to be used at multiple

        Metadata metadata = Metadata.builder().withName(METRIC_DOCUMENTS)
                .withDescription("Imixs-Workflow count documents").withType(MetricType.COUNTER).build();

        String method = null;
        // build tags...
        if (ArchiveEvent.ON_ARCHIVE == event.getEventType()) {
            method = "archive";
        }

        if (ArchiveEvent.ON_RESTORE == event.getEventType()) {
            method = "restore";
        }

        if (ArchiveEvent.ON_DELETE == event.getEventType()) {
            method = "delete";
        }

        Tag[] tags = { new Tag("method", method) };

        Counter counter = metricRegistry.counter(metadata, tags);

        return counter;

    }

}
