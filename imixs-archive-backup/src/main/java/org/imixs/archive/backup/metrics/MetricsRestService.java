package org.imixs.archive.backup.metrics;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/metrics")
public class MetricsRestService {

    @Inject
    private MetricService metricService;

    @Inject
    @ConfigProperty(name = "metrics.api-key")
    private Optional<String> metricsApiKey;

    /**
     * Returns metrics in Prometheus-Format - compatible with WildFly, Payara, Open
     * Liberty
     *
     * Requires API key if configured: ?key=your-api-key
     */
    @GET
    @Produces("text/plain")
    public Response getPrometheusMetrics(@QueryParam("key") String providedKey) {
        // Check API key if configured
        if (metricsApiKey.isPresent() && !metricsApiKey.get().isEmpty()) {
            if (providedKey == null || !metricsApiKey.get().equals(providedKey)) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity("# Unauthorized: Invalid or missing API key").build();
            }
        }

        try {
            String metrics = metricService.scrape();
            return Response.ok(metrics).header("Content-Type", "text/plain; version=0.0.4; charset=utf-8").build();
        } catch (Exception e) {
            return Response.status(500).entity("# Error generating metrics: " + e.getMessage()).build();
        }
    }

    /**
     * Gibt Service-Statistiken zurück
     */
    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStats(@QueryParam("key") String providedKey) {

        // Check API key if configured
        if (metricsApiKey.isPresent() && !metricsApiKey.get().isEmpty()) {
            if (providedKey == null || !metricsApiKey.get().equals(providedKey)) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity("{\"error\": \"Invalid or missing API key\"}").build();
            }
        }

        return Response.ok(new MetricStats(metricService.getCachedMetricsCount(), System.currentTimeMillis())).build();
    }

    public static class MetricStats {
        public final int metricsCount;
        public final long timestamp;

        public MetricStats(int metricsCount, long timestamp) {
            this.metricsCount = metricsCount;
            this.timestamp = timestamp;
        }
    }
}