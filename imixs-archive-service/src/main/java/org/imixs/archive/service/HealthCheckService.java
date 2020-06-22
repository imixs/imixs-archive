/*  
 *  Imixs-Workflow 
 *  
 *  Copyright (C) 2001-2020 Imixs Software Solutions GmbH,  
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
 *  Project: 
 *      https://www.imixs.org
 *      https://github.com/imixs/imixs-workflow
 *  
 *  Contributors:  
 *      Imixs Software Solutions GmbH - Project Management
 *      Ralph Soika - Software Developer
 */

package org.imixs.archive.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.eclipse.microprofile.health.Health;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.imixs.archive.service.cassandra.ClusterService;

/**
 * The Imixs HealthCheckService implements the Microservice HealthCheck
 * interface.
 * <p>
 * The service returns the count of workflow models
 * <p>
 * Example:
 * <code>{"data":{"archive.version":2.2.0},"name":"imixs-archive","state":"UP"}</code>
 * <p>
 * This check indicates the overall status of the cassandra cluster.
 * 
 * @author rsoika
 * @version 1.0
 */
@Health
@ApplicationScoped
public class HealthCheckService implements HealthCheck {

    private String archiveVersion = null;
    private static Logger logger = Logger.getLogger(HealthCheckService.class.getName());

    @Inject
    ClusterService clusterService;

    /**
     * This is the implementation for the health check call back method.
     * <p>
     * The method returns the status 'UP' if a valid cluster session exits.
     * <p>
     * Example:
     * <code>{"data":{"archive.version":2.2.0},"name":"imixs-archive","state":"UP"}</code>
     *
     * 
     */
    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = null;

        if (clusterService.getSession() != null) {
            builder = HealthCheckResponse.named("imixs-archive").withData("archive.version", getArchiveVersion()).up();
        } else {
            builder = HealthCheckResponse.named("imixs-archive").down();
        }

        return builder.build();
    }

    /**
     * This method extracts the archive version form the maven pom.properties
     * 
     * META-INF/maven/${groupId}/${artifactId}/pom.properties
     * 
     */
    private String getArchiveVersion() {
        if (archiveVersion == null) {
            try {
                InputStream resourceAsStream = this.getClass()
                        .getResourceAsStream("/META-INF/maven/org.imixs.workflow/imixs-archive-service/pom.properties");
                if (resourceAsStream != null) {
                    Properties prop = new Properties();
                    prop.load(resourceAsStream);
                    archiveVersion = prop.getProperty("version");
                }
            } catch (IOException e1) {
                logger.warning("failed to load pom.properties");
            }
        }
        // if not found -> 'unknown'
        if (archiveVersion == null || archiveVersion.isEmpty()) {
            archiveVersion = "unknown";
        }

        return archiveVersion;
    }

}
