package org.imixs.archive.export;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@RequestScoped
@Path("/test")
@Produces({ MediaType.TEXT_PLAIN })
public class ExportTest {

    @Inject
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    MetricRegistry metricRegistry;

    @GET
    @Path("/ping")
    @Produces("text/plain")
    @Counted(name = "exportPing", description = "Counting pings", displayName = "exportPing")
    public String ping() {

        return "Pinga - " + System.currentTimeMillis();
    }

    /**
     * Test to read the current coutner
     *
     * @return
     */
    @GET
    @Path("/count")
    @Produces("text/plain")
    public String count() {
        System.out.println("Test P1");

        Counter http200Count = metricRegistry.counter("http200Count");
        System.out.println("Test P2");

        // SortedMap<MetricID, Counter> counters = metricRegistry.getCounters();
        // Counter counter =
        // counters.get("application_org_imixs_archive_export_ExportTest_exportPing_total");

        // if (counter != null) {
        // return " ping counter = " + counter.getCount();
        // }

        return "test counter found";
    }
}
