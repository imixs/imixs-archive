/*******************************************************************************
 * Imixs-Workflow Archive 
 * Copyright (C) 2001-2018 Imixs Software Solutions GmbH,  
 * http://www.imixs.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * 
 *
 * Project: 
 * 	http://www.imixs.org
 *
 * Contributors:  
 * 	Imixs Software Solutions GmbH - initial API and implementation
 * 	Ralph Soika - Software Developer
 *******************************************************************************/

package org.imixs.archive.core;

import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.melman.RestAPIException;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.InvalidAccessException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.EJBException;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/**
 * This service deletes snapshot entities from the database after a grace period
 * of minimum 1 year.
 * 
 * Configuration:
 * 
 * <pre>
 * ARCHIVE_SNAPSHOT_COMPACTOR_GRACE_PERIOD=3
 * ARCHIVE_SNAPSHOT_COMPACTOR_ENABLED=true
 * ARCHIVE_SNAPSHOT_COMPACTOR_INTERVAL=14400
 * ARCHIVE_SNAPSHOT_COMPACTOR_INITIALDELAY=30
 * </pre>
 * <p>
 * 
 * 
 * @version 1.0
 * @author rsoika
 */
@Singleton
@Startup
@DeclareRoles({ "org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@RunAs("org.imixs.ACCESSLEVEL.MANAGERACCESS")
@Path("/snapshot/compactor")
public class SnapshotCompactorService {
    private static Logger logger = Logger.getLogger(SnapshotCompactorService.class.getName());
    // rest service endpoint
    public static final String ARCHIVE_SNAPSHOT_COMPACTOR_GRACE_PERIOD = "archive.snapshot.compactor.grace.period";
    public static final String ARCHIVE_SNAPSHOT_COMPACTOR_ENABLED = "archive.snapshot.compactor.enabled";
    public static final String ARCHIVE_SNAPSHOT_COMPACTOR_INTERVAL = "archive.snapshot.compactor.interval";
    public static final String ARCHIVE_SNAPSHOT_COMPACTOR_INITIALDELAY = "archive.snapshot.compactor.intialdelay";

    public static final String SNAPSHOT_COMPACTOR_ERROR = "SNAPSHOT_COMPACTOR_ERROR";

    @Resource
    SessionContext ejbCtx;

    @Inject
    SnapshotCompactorJob snapshotCompactorJob;

    @Inject
    @ConfigProperty(name = ARCHIVE_SNAPSHOT_COMPACTOR_ENABLED, defaultValue = "false")
    boolean compactorEnabled;

    @Inject
    @ConfigProperty(name = ARCHIVE_SNAPSHOT_COMPACTOR_GRACE_PERIOD, defaultValue = "1") // years
    int compactorGracePeriod;

    @Inject
    @ConfigProperty(name = ARCHIVE_SNAPSHOT_COMPACTOR_INTERVAL, defaultValue = "14400") // sec
    int compactorInterval;

    @Inject
    @ConfigProperty(name = ARCHIVE_SNAPSHOT_COMPACTOR_INITIALDELAY, defaultValue = "30") // sec
    long initialDelay;

    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_ENDPOINT)
    Optional<String> archiveServiceEndpoint;

    @Resource
    TimerService timerService;

    Timer timer = null;

    @PostConstruct
    public void init() {
        // init timer....
        if (compactorEnabled && archiveServiceEndpoint.isPresent() && !archiveServiceEndpoint.get().isEmpty()) {
            // Registering a non-persistent Timer Service.
            try {
                logger.info("├── Scheduling SnapshotCompactor:");
                logger.info("│   ├── grace period=" + compactorGracePeriod + " years ");
                logger.info("│   ├── initialDelay=" + initialDelay + "sec  interval=" + compactorInterval + "sec...");
                logger.info("│   ├── interval=" + compactorInterval + " sec...");
                // Registering a non-persistent Timer Service.
                final TimerConfig timerConfig = new TimerConfig();
                timerConfig.setInfo("ARCHIVE_SNAPSHOT_COMPACTOR");
                timerConfig.setPersistent(false);
                long interval = compactorInterval * 1000;
                timer = timerService.createIntervalTimer(initialDelay * 1000, interval, timerConfig);
            } catch (IllegalArgumentException | IllegalStateException | EJBException e) {
                logger.warning("Failed to init scheduler: " + e.getMessage());
            }
        }
    }

    /**
     * This is the method which processes the timeout event depending on the running
     * timer settings. The method lookups the event log entries and pushes new
     * snapshots into the archive service.
     * <p>
     * Each eventLogEntry is locked to guaranty exclusive processing.
     *
     * @throws RestAPIException
     **/
    @Timeout
    public void onTimeout(jakarta.ejb.Timer _timer) {
        try {
            compactSnapshots(compactorGracePeriod, 100, true);
        } catch (InvalidAccessException | EJBException e) {
            logger.warning("processing EventLog failed: " + e.getMessage());
        }
    }

    /**
     * This method selects outdated snapshots based on a grace period and verifies
     * if the snapshots exists in the archive.
     * 
     * <ul>
     * <li>If yes, the method deletes the entity.
     * <li>If not, the method saves the parent to fix a missing-snapshot issue.
     * </ul>
     * 
     * The method processes the snapshot in smaller batches
     * 
     * @param period
     */
    // @TransactionAttribute(value = TransactionAttributeType.NOT_SUPPORTED)
    public void compactSnapshots(int period, int maxCount, boolean doDelete) {
        int batchSize = 10;
        int totalCount = 0;
        int totalDeletions = 0;
        logger.info("├── Started compactSnapshots:");
        logger.info("│   ├── grace period=" + period);
        logger.info("│   ├── max count=" + maxCount);
        // compute batch count
        int totalPages = (int) Math.ceil((double) maxCount / batchSize);
        try {
            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                // load snapshot batch...
                ItemCollection metaData = snapshotCompactorJob.processSnapshotBatchWithFinding(period, batchSize,
                        doDelete);
                int processed = metaData.getItemValueInteger("snapshots.processed");
                int deletions = metaData.getItemValueInteger("snapshots.deleted");
                totalCount = totalCount + processed;
                totalDeletions = totalDeletions + deletions;
                logger.info("│   ├── Processed " + totalCount + " snapshots - " + totalDeletions + " deletions");
                if (processed == 0) {
                    // no more data
                    break;
                }
                // short break...
                Thread.sleep(500);
            }
            logger.info("├── compactSnapshots completed:");
            logger.info("│   ├── snapshots processed = " + totalCount);
            logger.info("│   ├── snapshots deleted   = " + totalDeletions);
            logger.info("├── Finished!");
        } catch (InterruptedException e) {
            logger.warning("├── Failed to process snapshots : " + e.getMessage());
        }

    }

    /**
     * This endpoint performs a dry run without deleting any data
     */
    @GET
    @Path("/test/{period}")
    public String dryRun(@PathParam("period") int period) {
        logger.info("snapshot compactor test mode - period=" + period);
        compactSnapshots(period, 10, false);
        return "test period = " + period;
    }

}