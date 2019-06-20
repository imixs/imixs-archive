package org.imixs.archive.core.cassandra;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RunAs;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.core.SnapshotService;
import org.imixs.workflow.engine.EventLogService;
import org.imixs.workflow.engine.jpa.EventLog;

/**
 * The SnapshotPushService uses an EJB TimerService for periodic snapshot
 * synchronization. It allows not only single time execution, but also interval
 * timers.
 * <p>
 * The timer service checks all new Snapshot Event Log entries and pushes the
 * entries inot the Archive service via the Rest API.
 * 
 * @author rsoika
 *
 */
@Startup
@Singleton
@DeclareRoles({ "org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@RunAs("org.imixs.ACCESSLEVEL.MANAGERACCESS")
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class ArchivePushService {
	private static final long INTERVAL = 1 * 1000L; // 1 second

	@Inject
	@ConfigProperty(name = SnapshotService.ENV_ARCHIVE_SERVICE_ENDPOINT, defaultValue = "")
	String archiveServiceEndpoint;

	@Inject
	EventLogService eventLogService;

	@Inject
	ArchiveClientService archiveClientService;

	@Resource
	private TimerService timerService;

	@SuppressWarnings("unused")
	private Timer timer;

	private ConcurrentLinkedQueue<EventLog> eventCache = null;
	

	private static Logger logger = Logger.getLogger(ArchivePushService.class.getName());

	@PostConstruct
	public void init() {
		eventCache = new ConcurrentLinkedQueue<>();
		if (archiveServiceEndpoint != null && !archiveServiceEndpoint.isEmpty()) {
			TimerConfig config = new TimerConfig();
			config.setPersistent(false);
			timer = timerService.createIntervalTimer(INTERVAL, INTERVAL, config);
		}
	}

	/**
	 * Thie method lookups the event log entries and pushes new snapshots into the
	 * archvie service.
	 * <p>
	 * Each eventLogEntry is cached in the eventCache. The cache is cleared from all
	 * eventLogEntries not part of the current collection. We can assume that the
	 * event was succefully processed by the ArchiveHandler
	 * 
	 * @throws ArchiveException
	 */
	@Timeout
	private synchronized void onTimer() {
		// test for new event log entries...
		List<EventLog> events = eventLogService.findEventsByTopic(100, SnapshotService.EVENTLOG_TOPIC_ADD,
				SnapshotService.EVENTLOG_TOPIC_REMOVE);

		// prune cache 
		clearCache(events);
		for (EventLog eventLogEntry : events) {
			// push the snapshotEvent only if not just qued...
			if ( !eventCache.contains(eventLogEntry)) {
				if (SnapshotService.EVENTLOG_TOPIC_ADD.equals(eventLogEntry.getTopic())) {

					logger.finest("......push snapshot " + eventLogEntry.getRef() + "....");
					eventCache.add(eventLogEntry);

					archiveClientService.pushSnapshot(eventLogEntry);
				}
			} else {
				logger.finest("......snapshot " + eventLogEntry.getId() + " was already fired but not yet done....");
			}

		}
	}

	/**
	 * This method checks for each event in the cache if it is part of the given
	 * list. If not the event will be removed from the cache.
	 * 
	 * @param events
	 */
	private void clearCache(List<EventLog> events) {
		if (events == null) {
			return;
		}
		Iterator<EventLog> iter = eventCache.iterator();
		while (iter.hasNext()) {
			EventLog eventLogEntry = iter.next();
			if (!events.contains(eventLogEntry)) {
				logger.info("removing " + eventLogEntry.getId() + " from cache...");
				eventCache.remove();
			}

		}

	}
}
