/****************************************************************************
 * Copyright (c) 2022-2025 Imixs Software Solutions GmbH and others.
 * https://www.imixs.com
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * This Source Code may also be made available under the terms of the
 * GNU General Public License, version 2 or later (GPL-2.0-or-later),
 * which is available at https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0-or-later
 ****************************************************************************/

package org.imixs.archive.backup.health;

import java.util.logging.Logger;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;
import org.imixs.archive.backup.metrics.MetricService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The Imixs HealthCheckService implements the Microservice HealthCheck
 * interface.
 * <p>
 * The service returns the count of workflow models
 * <p>
 * Example: <code>{
      "name": "imixs-backup",
      "status": "UP",
      "data": { "backup_events_unprocessed": 0, "backup_events_processed": 0, "backup_events_errors": 0, "backup.status": "ok" }
    }</code>
 * <p>
 * This check indicates the overall status of the backup service.
 *
 * @author rsoika
 * @version 1.0
 */
@Liveness
@ApplicationScoped
public class HealthCheckService implements HealthCheck {

    private static final Logger logger = Logger.getLogger(HealthCheckService.class.getName());

    @Inject
    private MetricService metricService;

    /**
     * This is the implementation for the health check call back method.
     * <p>
     * The method returns the status 'UP' together with the count of processed
     * bakcup events
     * <p>
     */
    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = null;
        long unprocessed = 0;
        long processed = 0;
        long errors = 0;

        unprocessed = metricService.getGaugeByName(MetricService.METRIC_EVENTS_UNPROCESSED);
        processed = metricService.getCounterByName(MetricService.METRIC_EVENTS_PROCESSED);
        errors = metricService.getCounterByName(MetricService.METRIC_EVENTS_ERRORS);

        builder = HealthCheckResponse.named("imixs-backup")
                .withData(MetricService.METRIC_EVENTS_UNPROCESSED, unprocessed)
                .withData(MetricService.METRIC_EVENTS_PROCESSED, processed)
                .withData(MetricService.METRIC_EVENTS_ERRORS, errors).withData("backup.status", "ok").up();

        return builder.build();
    }

}
