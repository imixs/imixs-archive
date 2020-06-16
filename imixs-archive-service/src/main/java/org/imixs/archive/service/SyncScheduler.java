package org.imixs.archive.service;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RunAs;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.inject.Inject;
import javax.ws.rs.NotFoundException;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.melman.RestAPIException;

/**
 * The SyncScheduler starts a ManagedScheduledExecutorService to pull new
 * snapshot events into the cassandra archive
 *
 * @see SyncService
 * @author ralph.soika@imixs.com
 * @version 2.0
 */
@Startup
@Singleton
@LocalBean
//@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@DeclareRoles({ "org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@RunAs("org.imixs.ACCESSLEVEL.MANAGERACCESS")
public class SyncScheduler {

    // timeout interval in ms
    @Inject
    @ConfigProperty(name = ImixsArchiveApp.WORKFLOW_SYNC_INTERVAL, defaultValue = "1000")
    long interval;

    @Inject
    @ConfigProperty(name = ImixsArchiveApp.WORKFLOW_SERVICE_ENDPOINT, defaultValue = "")
    String workflowServiceEndpoint;

    @Inject
    SyncService archiveSyncService;

    @Resource
    ManagedScheduledExecutorService scheduler;

    private static Logger logger = Logger.getLogger(SyncScheduler.class.getName());

    /**
     * Initialize ManagedScheduledExecutorService
     */
    @PostConstruct
    public void init() {
        if (workflowServiceEndpoint != null && !workflowServiceEndpoint.isEmpty()) {
            logger.info("Starting ArchivePushService - inverval=" + interval + " ....");
            this.scheduler.scheduleAtFixedRate(this::run, interval, interval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * This method is called by the ManagedScheduledExecutorService. The method
     * lookups the event log entries and pulls new snapshots into the archive
     * service.
     *
     * @throws ArchiveException
     */
    public void run() {
        try {
            // release dead locks
            archiveSyncService.releaseDeadLocks();

            // process....
            archiveSyncService.processEventLog();
        } catch (NotFoundException | RestAPIException e) {
            logger.warning("unable to process event log: " + e.getMessage());

        }

    }

}
