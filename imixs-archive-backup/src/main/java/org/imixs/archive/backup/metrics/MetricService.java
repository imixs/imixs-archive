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
package org.imixs.archive.backup.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.backup.util.LogController;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;

/**
 * The MetricService provides methods to access metrics
 *
 * @version 1.0
 * @author rsoika
 */
@Singleton
@Startup
public class MetricService {

    private static Logger logger = Logger.getLogger(MetricService.class.getName());

    @Inject
    LogController logController;

    public static final String METRIC_EVENTS_PROCESSED = "backup_events_processed";
    public static final String METRIC_EVENTS_ERRORS = "backup_events_errors";
    public static final String METRIC_EVENTS_UNPROCESSED = "backup_events_unprocessed";

    // Micrometer MeterRegistry, provided by MeterRegistryProducer (imixs-metrics)
    @Inject
    private MeterRegistry meterRegistry;

    @Inject
    @ConfigProperty(name = "metrics.enabled", defaultValue = "true")
    private boolean metricsEnabled;

    // Cache for Counter instances to avoid re-registering meters on every call
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();

    // Holds the current value for the unprocessed events gauge.
    // Updated in onTimeout(), read by the MicroProfile metrics registry.
    private final AtomicLong unprocessedEventLogEntries = new AtomicLong(-1);

    @PostConstruct
    public void init() {

        if (metricsEnabled) {
            // Register a gauge backed by the AtomicLong above.
            // Micrometer will call get() on scrape, no manual push needed.
            Gauge.builder(METRIC_EVENTS_UNPROCESSED, unprocessedEventLogEntries, AtomicLong::get)
                    .description("Imixs-Backup Service - current number of unprocessed backup events")
                    .register(meterRegistry);
        }

    }

    /**
     * Setter for the gauge backup_events_unprocessed
     *
     * @param totalUnprocessed
     */
    public void setUnprocessedEventLogEntries(long totalUnprocessed) {
        unprocessedEventLogEntries.set(totalUnprocessed);
    }

    /**
     * Helper method to create (or reuse) a Micrometer Counter and increment it. The
     * counter instance is cached to avoid re-registering the same meter on every
     * invocation.
     *
     * @param name the metric name, e.g. METRIC_EVENTS_PROCESSED
     */
    public void countMetric(String name) {
        if (!metricsEnabled) {
            return;
        }
        try {
            Counter counter = counterCache.computeIfAbsent(name, key -> Counter.builder(key)
                    .description("Imixs-Backup Service - processed backup events").register(meterRegistry));
            counter.increment();
        } catch (Exception e) {
            logger.severe("Unable to update metric '" + name + "': " + e.getMessage());
        }
    }

    /**
     * Returns the current value of a Micrometer {@link Counter} whose metric name
     * ends with the given suffix.
     * <p>
     * This replaces the former MicroProfile Metrics based lookup
     * (MetricRegistry.getCounters()) that used to live in the CDI backing bean.
     *
     * @param name suffix of the counter's metric name, e.g. METRIC_EVENTS_PROCESSED
     * @return the current counter value, or 0 if no matching counter was found
     */
    public long getCounterByName(String name) {
        for (Meter meter : meterRegistry.getMeters()) {
            if (meter instanceof Counter && meter.getId().getName().endsWith(name)) {
                return (long) ((Counter) meter).count();
            }
        }
        logger.fine("Metric Counter : " + name + " not found!");
        return 0;
    }

    /**
     * Returns the current value of a Micrometer {@link Gauge} whose metric name
     * ends with the given suffix.
     *
     * @param name suffix of the gauge's metric name, e.g. METRIC_EVENTS_UNPROCESSED
     * @return the current gauge value, or 0 if no matching gauge was found
     */
    public long getGaugeByName(String name) {
        for (Meter meter : meterRegistry.getMeters()) {
            if (meter instanceof Gauge && meter.getId().getName().endsWith(name)) {
                Double result = ((Gauge) meter).value();
                return result.longValue();
            }
        }
        logger.fine("Metric Gauge : " + name + " not found!");
        return 0;
    }

    /**
     * Gibt Prometheus-Format zurück (nur für embedded Registry)
     */
    public String scrape() {
        if (!metricsEnabled) {
            return "# Metrics disabled\n";
        }

        if (meterRegistry instanceof PrometheusMeterRegistry prometheusRegistry) {
            return prometheusRegistry.scrape();
        }

        // Fallback für andere Registry-Typen
        return "# Prometheus format not available for registry type: " + meterRegistry.getClass().getSimpleName()
                + "\n";
    }

    /**
     * Gibt Statistiken über den Service zurück (für Monitoring/Debugging)
     */
    public int getCachedMetricsCount() {
        return counterCache.size();
    }

    /**
     * Leert den Cache (nur für Tests/Debugging)
     */
    public void clearCache() {
        counterCache.clear();
    }
}
