package org.imixs.archive.backup.metrics;

import java.util.logging.Logger;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class MeterRegistryProducer {

    private static final Logger logger = Logger.getLogger(MeterRegistryProducer.class.getName());
    private PrometheusMeterRegistry prometheusMeterRegistry;

    @PostConstruct
    public void init() {
        // Erstelle Prometheus Registry
        prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        // Registriere bei globalem Registry für Server-Integration
        Metrics.addRegistry(prometheusMeterRegistry);

        logger.info("Prometheus MeterRegistry initialized and registered globally");
    }

    @Produces
    @Dependent // Statt @ApplicationScoped - verhindert Proxy-Erstellung
    public MeterRegistry produceMeterRegistry() {
        return prometheusMeterRegistry;
    }

    @PreDestroy
    public void cleanup() {
        if (prometheusMeterRegistry != null) {
            Metrics.removeRegistry(prometheusMeterRegistry);
            prometheusMeterRegistry.close();
            logger.info("Prometheus MeterRegistry cleaned up");
        }
    }
}