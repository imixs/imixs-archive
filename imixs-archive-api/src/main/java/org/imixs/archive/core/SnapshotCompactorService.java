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
import java.util.logging.Level;
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
    @ConfigProperty(name = ARCHIVE_SNAPSHOT_COMPACTOR_GRACE_PERIOD, defaultValue = "1")
    int compactorGracePeriod;

    @Inject
    @ConfigProperty(name = ARCHIVE_SNAPSHOT_COMPACTOR_INTERVAL, defaultValue = "4")
    int compactorInterval;

    @Inject
    @ConfigProperty(name = ARCHIVE_SNAPSHOT_COMPACTOR_INITIALDELAY, defaultValue = "30000")
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
                startScheduler(true);
            } catch (IllegalArgumentException e) {
                logger.warning("Failed to init scheduler: " + e.getMessage());
            }
        }
    }

    /**
     * This method initializes an in-memory scheduler.
     * <p>
     *
     *
     * @throws BackupException
     */
    public void startScheduler(boolean clearLog) throws SnapshotException {
        try {

            logger.info(
                    "Starting backup scheduler - initalDelay=" + initialDelay + "ms  inverval=" + compactorInterval
                            + "hours ....");

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
        logger.info("...starting snapshot compactor run....");
        try {

            // compactSnapshots(compactorGracePeriod, 100);
        } catch (InvalidAccessException | EJBException e) {

            logger.warning("processing EventLog failed: " + e.getMessage());

        }
    }

    /**
     * This method selects outdated snapshots based on a grace period and verifies
     * if the snaphsots exists in the archive.
     * 
     * It yes, the method deletes the entity.
     * If not, the method saves the parent to fix a missing-snapshot issue.
     * 
     * @param period
     */
    private void compactSnapshots(int period, int maxCount) {

        List<ItemCollection> result = findAllSnapshotsByGracePeriod(period, maxCount);
        for (ItemCollection snapshot : result) {

            String id = snapshot.getUniqueID();
            boolean snapshotExists = false;
            try {
                List<ItemCollection> remoteSnapshot = archiveRemoteService.loadSnapshotFromArchive(id);
                if (remoteSnapshot != null && remoteSnapshot.size() > 0) {
                    snapshotExists = true;

                    // documentService.remove(snapshot);
                }
            } catch (RestAPIException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            if (snapshotExists) {
                logger.info("Snapshot " + id + " exists in archive and can be deleted");
            } else {
                logger.warning("Snapshot " + id + " not found in archive! Snapshot data will be archived now!");
                try {
                    String originId = id.substring(id.lastIndexOf("-") + 1);
                    ItemCollection originWorkitem = documentService.load(originId);
                    // force snapshot creation by saving the origin data...
                    documentService.save(originWorkitem);
                } catch (AccessDeniedException e) {
                    logger.warning("Snapshotdata for document  " + id + " could not be refreshed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * This endpoint performs a dry run without deleting any data
     */
    @GET
    @Path("/test/{period}")
    public String dryRun(@PathParam("period") int period) {
        boolean debug = logger.isLoggable(Level.FINE);

        logger.info("snapshot compactor test mode - period=" + period);

        compactSnapshots(period, 10);
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