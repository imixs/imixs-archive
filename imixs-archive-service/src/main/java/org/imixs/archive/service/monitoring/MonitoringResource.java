package org.imixs.archive.service.monitoring;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.management.ObjectName;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

/**
 * See:
 * http://www.adam-bien.com/roller/abien/entry/singleton_the_simplest_possible_jmx
 * http://www.adam-bien.com/roller/abien/entry/monitoring_java_ee_appservers_with
 * 
 * TODO - exposed with JAX-RS to make the statistics also accessible via HTTP
 * 
 * - support receive events:
 * http://www.adam-bien.com/roller/abien/entry/java_ee_6_observer_with
 * 
 * 
 * @author rsoika
 *
 */
@Singleton
@Startup
@Path("monitoring")
public class MonitoringResource {

	private ConcurrentHashMap diagnostics = new ConcurrentHashMap();

	private ObjectName objectName = null;

	private AtomicLong exceptionCount;

	@PostConstruct
	public void registerInJMX() {
		this.exceptionCount = new AtomicLong();
		try {

		} catch (Exception e) {
			throw new IllegalStateException("Problem during registration of Monitoring service:" + e);
		}

		diagnostics.put("mykey", "some value");
	}
	// bookkeeping methods omitted

	/**
	 * Prometheus format
	 */
	@GET
	@Produces({ MediaType.TEXT_PLAIN })
	public Response getDiagnostics() {
		StreamingOutput stream = new StreamingOutput() {
			@Override
			public void write(OutputStream os) throws IOException, WebApplicationException {
				Writer writer = new BufferedWriter(new OutputStreamWriter(os));

				long time = System.currentTimeMillis();

				writer.write("# HELP http_requests_total The total number of HTTP requests." + "\n");
				writer.write("# TYPE http_requests_total counter" + "\n");
				writer.write("http_requests_total{method=\"post\",code=\"200\"} 1027 " + time + "\n");
				writer.write("http_requests_total{method=\"post\",code=\"400\"}    3 " + time + "\n");

				writer.flush();
			}
		};

		return Response.ok(stream).build();
	}

	@PreDestroy
	public void unregisterFromJMX() {
		try {

		} catch (Exception e) {
			throw new IllegalStateException("Problem during unregistration of Monitoringservice:" + e);
		}
	}
}
