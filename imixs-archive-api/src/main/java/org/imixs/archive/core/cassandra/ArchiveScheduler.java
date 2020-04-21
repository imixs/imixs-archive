package org.imixs.archive.core.cassandra;

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

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.core.SnapshotService;
import org.imixs.workflow.engine.EventLogService;

/**
 * The ArchiveScheduler starts a ManagedScheduledExecutorService to process
 * snapshot events in an asynchronous way by calling the ArchiveRemovteService.
 * <p>
 * 
 * The timer service checks all new Snapshot Event Log entries and pushes the
 * entries into the Archive service via the Rest API. The default timeout for
 * the timer service is 1 second but can be configured by the system property
 * 'archive.service.interval'
 * 
 * @author ralph.soika@imixs.com
 * @version 1.1
 */
@Startup
@Singleton
@LocalBean
//@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@DeclareRoles({ "org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@RunAs("org.imixs.ACCESSLEVEL.MANAGERACCESS")
public class ArchiveScheduler {

    // timeout interval in ms
    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_INTERVAL, defaultValue = "1000")
    long interval;

    // deadlock timeout interval in ms
    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_DEADLOCK, defaultValue = "60000")
    long deadLockInterval;

    // archive service endpoint
    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_ENDPOINT, defaultValue = "")
    String archiveServiceEndpoint;

    @Inject
    EventLogService eventLogService;

    @Inject
    ArchiveRemoteService archiveClientService;

    @Resource
    ManagedScheduledExecutorService scheduler;

    private static Logger logger = Logger.getLogger(ArchiveScheduler.class.getName());

    /**
     * Initialize ManagedScheduledExecutorService
     */
    @PostConstruct
    public void init() {
        if (archiveServiceEndpoint != null && !archiveServiceEndpoint.isEmpty()) {
            logger.info("Starting ArchivePushService - inverval=" + interval + " ....");
            this.scheduler.scheduleAtFixedRate(this::run, interval, interval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * This method is called by the ManagedScheduledExecutorService. The method
     * lookups the event log entries and pushes new snapshots into the archive
     * service.
     * <p>
     * Each eventLogEntry is cached in the eventCache. The cache is cleared from all
     * eventLogEntries not part of the current collection. We can assume that the
     * event was successfully processed by the ArchiveHandler
     * 
     * @throws ArchiveException
     */
    public void run() {

        eventLogService.releaseDeadLocks(deadLockInterval, SnapshotService.EVENTLOG_TOPIC_ADD,
                SnapshotService.EVENTLOG_TOPIC_REMOVE);

        archiveClientService.processEventLog();

    }

}
