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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.core.cassandra.ArchiveRemoteService;
import org.imixs.melman.RestAPIException;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.exceptions.InvalidAccessException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.EJB;
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
 * ARCHIVE_SNAPSHOT_COMPACTOR_GRACE_PERIOD=5
 * ARCHIVE_SNAPSHOT_COMPACTOR_ENABLED=true
 * ARCHIVE_SNAPSHOT_COMPACTOR_INTERVAL=4
 * ARCHIVE_SNAPSHOT_COMPACTOR_INITIALDELAY=30000
 * </pre>
 * <p>
 * 
 * 
 * @version 2.0
 * @author rsoika
 */
@Singleton
@Startup
@DeclareRoles({ "org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@RunAs("org.imixs.ACCESSLEVEL.MANAGERACCESS")
@Path("/snapshot/compactor")
public class SnapshotCompactorService {

    // rest service endpoint
    public static final String ARCHIVE_SNAPSHOT_COMPACTOR_GRACE_PERIOD = "archive.snapshot.compactor.grace.period";
    public static final String ARCHIVE_SNAPSHOT_COMPACTOR_ENABLED = "archive.snapshot.compactor.enabled";
    public static final String ARCHIVE_SNAPSHOT_COMPACTOR_INTERVAL = "archive.snapshot.compactor.interval";
    public static final String ARCHIVE_SNAPSHOT_COMPACTOR_INITIALDELAY = "archive.snapshot.compactor.intialdelay";

    public static final String SNAPSHOT_COMPACTOR_ERROR = "SNAPSHOT_COMPACTOR_ERROR";

    @Resource
    SessionContext ejbCtx;

    @EJB
    DocumentService documentService;

    @EJB
    ArchiveRemoteService archiveRemoteService;

    @Inject
    @ConfigProperty(name = ARCHIVE_SNAPSHOT_COMPACTOR_ENABLED, defaultValue = "false")
    boolean compactorEnabled;

    @Inject
    @ConfigProperty(name = ARCHIVE_SNAPSHOT_COMPACTOR_GRACE_PERIOD, defaultValue = "1") // years
    int compactorGracePeriod;

    @Inject
    @ConfigProperty(name = ARCHIVE_SNAPSHOT_COMPACTOR_INTERVAL, defaultValue = "4") // hours
    int compactorInterval;

    @Inject
    @ConfigProperty(name = ARCHIVE_SNAPSHOT_COMPACTOR_INITIALDELAY, defaultValue = "30000") // ms
    long initialDelay;

    @Resource
    TimerService timerService;

    Timer timer = null;

    private static Logger logger = Logger.getLogger(SnapshotService.class.getName());

    @PostConstruct
    public void init() {
        // init timer....
        if (compactorEnabled) {
            logger.info("init SnapshotCompactorService - grace period=" + compactorGracePeriod);
            // Registering a non-persistent Timer Service.
            try {
                startScheduler();
            } catch (IllegalArgumentException e) {
                logger.warning("Failed to init scheduler: " + e.getMessage());
            }
        }
    }

    /**
     * This method initializes an in-memory scheduler.
     *
     * @throws BackupException
     */
    public void startScheduler() throws SnapshotException {
        try {
            logger.info(
                    "├── Scheduling SnapshotCompactor:");
            logger.info("│   ├── initialDelay=" + initialDelay + "ms  interval=" + compactorInterval + "hours...");
            // Registering a non-persistent Timer Service.
            final TimerConfig timerConfig = new TimerConfig();
            timerConfig.setInfo("ARCHIVE_SNAPSHOT_COMPACTOR"); // empty info string indicates no JSESSIONID!
            timerConfig.setPersistent(false);
            long interval = compactorInterval * 60 * 60 * 1000;
            timer = timerService.createIntervalTimer(initialDelay, interval, timerConfig);
        } catch (IllegalArgumentException | IllegalStateException | EJBException e) {
            throw new SnapshotException("TIMER_EXCEPTION", "Failed to init scheduler ", e);
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
            // compactSnapshots(compactorGracePeriod, 100);
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
    private void compactSnapshots(int period, int maxCount, boolean doDelete) {
        int batchSize = 10;
        int totalCount = 0;
        int totalDeletions = 0;
        logger.info("├── compactSnapshots - grace period=" + period + " max count=" + maxCount);
        // compute batch count
        int totalPages = (int) Math.ceil((double) maxCount / batchSize);
        try {
            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                // load snapshot batch...
                List<ItemCollection> snapshots = findAllSnapshotsByGracePeriod(period, batchSize);
                totalCount = totalCount + snapshots.size();
                int deletions = processSnapshotBatch(snapshots, doDelete);
                totalDeletions = totalDeletions + deletions;
                // Fortschritt loggen
                logger.info("│   ├── Processed " + batchSize + " snapshots -  " + deletions + " deletions");
                // short break...
                Thread.sleep(500);
            }
            logger.info("├── compactSnapshots completed:");
            logger.info("│   ├── " + totalCount + " snapshots verified - " + totalDeletions + " successful deletions.");
        } catch (InterruptedException e) {
            logger.warning("├── Failed to process snapshots : " + e.getMessage());
        }

    }

    /**
     * Process a smaller batch of snapshots
     */
    private int processSnapshotBatch(List<ItemCollection> snapshots, boolean doDelete) {
        int deletions = 0;
        for (ItemCollection snapshot : snapshots) {

            String id = snapshot.getUniqueID();
            boolean snapshotExists = false;
            try {
                List<ItemCollection> remoteSnapshot = archiveRemoteService.loadSnapshotFromArchive(id);
                snapshotExists = (remoteSnapshot != null && remoteSnapshot.size() > 0);

                if (snapshotExists) {
                    logger.info("│   │   ├── Snapshot " + id + " exists in archive and will be deleted now...");
                    if (doDelete) {
                        documentService.remove(snapshot);
                        deletions++;
                    }
                } else {
                    logger.warning("│   │   ├── Snapshot " + id
                            + " not found in archive! Snapshot data will be refreshed!");
                    String originId = id.substring(id.lastIndexOf("-") + 1);
                    ItemCollection originWorkitem = documentService.load(originId);
                    if (originWorkitem == null) {
                        logger.severe("│   │   ├── Fatal Error - origin workitem '" + originId + "' does not exist!");
                    } else {
                        // force snapshot creation by saving the origin data...
                        documentService.save(originWorkitem);
                        // short delay (5s)....
                        Thread.sleep(5000);
                    }
                }
            } catch (AccessDeniedException | RestAPIException | InterruptedException e) {
                logger.warning("│   │   ├── Failed to process snapshot " + id + " : " + e.getMessage());
            }
        }
        return deletions;
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

    /**
     * This method returns all existing Snapshot-workitems after a given grace
     * period.
     * 
     * The method selects only archive, archivedeleted and workitemdeleted.
     * 
     * 
     * @param uniqueid
     * @return
     */
    public List<ItemCollection> findAllSnapshotsByGracePeriod(int period, int maxCount) {
        if (period < 1) {
            throw new SnapshotException(SNAPSHOT_COMPACTOR_ERROR, "grace period should not be lower than 1!");
        }

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -period);
        Date gracePeriodDate = calendar.getTime();
        SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd");

        String query = "SELECT document FROM Document AS document WHERE "
                + "(document.type = 'snapshot-workitemarchive' OR document.type = 'snapshot-workitemarchivedeleted'  OR document.type = 'snapshot-workitemdeleted' )"
                + "  AND document.modified <'" + dateformat.format(gracePeriodDate)
                + "' ORDER BY document.modified DESC";
        return documentService.getDocumentsByQuery(query, maxCount);
    }

}