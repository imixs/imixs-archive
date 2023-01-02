/*******************************************************************************
 *  Imixs Workflow Technology
 *  Copyright (C) 2001, 2008 Imixs Software Solutions GmbH,  
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
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika
 *******************************************************************************/
package org.imixs.archive.backup;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.EJBException;
import javax.ejb.ScheduleExpression;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.melman.DocumentClient;
import org.imixs.melman.EventLogClient;
import org.imixs.melman.RestAPIException;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.InvalidAccessException;

/**
 * The BackupService exports the workflow data from a Imixs-Workflow instance
 * into a FTP storage. The service class runs a TimerService based on the given
 * scheduler configuration.
 * <p>
 * The service automatically starts during deployment.
 * <p>
 * The scheduler configuration is based on the chron format. E.g:
 * 
 * <code>
 *    hour=*;minute=30;
 * </code>
 * 
 * @version 1.0
 * @author rsoika
 */

@Stateless
public class BackupService {

	public final static String TIMER_ID_EXPORTSERVICE = "IMIXS_ARCHIVE_EXPORT_TIMER";

	public final static String ITEM_EXPORTPOINT = "export.point";
	public final static String ITEM_EXPORTCOUNT = "export.count";
	public final static String ITEM_EXPORTSIZE = "export.size";
	public final static String ITEM_EXPORTERRORS = "export.errors";
	public final static String DEFAULT_SCHEDULER_DEFINITION = "hour=*";

	public static final String ENV_EXPORT_SCHEDULER_DEFINITION = "EXPORT_SCHEDULER_DEFINITION";

	public static final String ENV_EXPORT_FTP_HOST = "EXPORT_FTP_HOST";
	public static final String ENV_EXPORT_FTP_PATH = "EXPORT_FTP_PATH";
	public static final String ENV_EXPORT_FTP_PORT = "EXPORT_FTP_PORT";
	public static final String ENV_EXPORT_FTP_USER = "EXPORT_FTP_USER";
	public static final String ENV_EXPORT_FTP_PASSWORD = "EXPORT_FTP_PASSWORD";

	public static final String EVENTLOG_TOPIC_BACKUP = "snapshot.backup";
	public static final String BACKUP_SYNC_DEADLOCK = "backup.sync.deadlock";

	@Inject
	LogController logController;

	@Inject
	@ConfigProperty(name = ENV_EXPORT_SCHEDULER_DEFINITION)
	Optional<String> schedulerDefinition;

	@Inject
	@ConfigProperty(name = ENV_EXPORT_FTP_HOST)
	Optional<String> ftpServer;

	// deadlock timeout interval in ms
	@Inject
	@ConfigProperty(name = BACKUP_SYNC_DEADLOCK, defaultValue = "60000")
	long deadLockInterval;

	@Resource
	javax.ejb.TimerService timerService;

	@Inject
	FTPConnector ftpConnector;

	private static Logger logger = Logger.getLogger(BackupService.class.getName());

	/**
	 * This is the method which processes the timeout event depending on the running
	 * timer settings. The method lookups the event log entries and pushes new
	 * snapshots into the archive service.
	 * <p>
	 * Each eventLogEntry is locked to guaranty exclusive processing.
	 * 
	 * @throws RestAPIException
	 **/
	@SuppressWarnings("unused")
	@Timeout
	public void processEventLog(EventLogClient eventLogClient, DocumentClient documentClient) throws RestAPIException {
		String topic = null;
		String id = null;
		String ref = null;

		if (documentClient == null || eventLogClient == null) {
			// no client object
			logger.fine("...no eventLogClient available!");
			return;
		}

		// max 100 entries per iteration
		eventLogClient.setPageSize(100);
		List<ItemCollection> events = eventLogClient.searchEventLog(EVENTLOG_TOPIC_BACKUP);

		for (ItemCollection eventLogEntry : events) {
			topic = eventLogEntry.getItemValueString("topic");
			id = eventLogEntry.getItemValueString("id");
			ref = eventLogEntry.getItemValueString("ref");
			try {
				// first try to lock the eventLog entry....
				eventLogClient.lockEventLogEntry(id);
				// eventLogService.lock(eventLogEntry);

				// pull the snapshotEvent ...

				logger.finest("......pull snapshot " + ref + "....");
				// eventCache.add(eventLogEntry);
				ItemCollection snapshot = pullSnapshot(eventLogEntry, documentClient, eventLogClient);

				ftpConnector.put(snapshot);

				// finally remove the event log entry...
				eventLogClient.deleteEventLogEntry(id);
				// eventLogService.removeEvent(eventLogEntry);
			} catch (InvalidAccessException | EJBException | BackupException e) {
				// we also catch EJBExceptions here because we do not want to cancel the
				// ManagedScheduledExecutorService
				logger.severe("SnapshotEvent " + id + " backup failed: " + e.getMessage());
				
			}
		}

	}

	/**
	 * Asynchronous method to release dead locks
	 * 
	 * @param eventLogClient
	 * @param deadLockInterval
	 * @param topic
	 * @throws RestAPIException
	 */
	@TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
	public void releaseDeadLocks(EventLogClient eventLogClient) throws RestAPIException {
		if (eventLogClient == null) {
			// no client object
			logger.fine("...no eventLogClient available!");
			return;
		}
		eventLogClient.releaseDeadLocks(deadLockInterval, EVENTLOG_TOPIC_BACKUP);
	}

	/**
	 * This method lookups the event log entries and pushes new snapshots into the
	 * archive service.
	 * <p>
	 * The method returns a AsyncResult to indicate the completion of the push. A
	 * client can use this information for further control.
	 * 
	 * @throws BackupException
	 * @throws RestAPIException
	 */
	public ItemCollection pullSnapshot(ItemCollection eventLogEntry, DocumentClient documentClient,
			EventLogClient eventLogClient) throws BackupException {

		if (eventLogEntry == null || documentClient == null || eventLogClient == null) {
			// no client object
			logger.fine("...no eventLogClient available!");
			return null;
		}

		String ref = eventLogEntry.getItemValueString("ref");
		String id = eventLogEntry.getItemValueString("id");
		logger.finest("...push " + ref + "...");
		// lookup the snapshot...
		ItemCollection snapshot;
		try {
			snapshot = documentClient.getDocument(ref);
			if (snapshot != null) {
				logger.finest("...write snapshot into backup store...");
				return snapshot;
			}
		} catch (RestAPIException e) {
			logger.severe("Snapshot " + ref + " pull failed: " + e.getMessage());
			// now we need to remove the batch event
			logger.warning("EventLogEntry " + id + " will be removed!");
			try {
				eventLogClient.deleteEventLogEntry(id);
			} catch (RestAPIException e1) {
				throw new BackupException("REMOTE_EXCEPTION", "Unable to delte eventLogEntry: " + id, e1);
			}
		}

		return null;
	}

	/**
	 * This method initializes the scheduler.
	 * <p>
	 * The method also verifies the existence of the archive keyspace by loading the
	 * archive session object.
	 * 
	 * @throws BackupException
	 */
	public boolean startScheduler() throws BackupException {
		try {
			logger.info("...starting the export scheduler...");
			// start archive schedulers....
			start();
			return true;

		} catch (Exception e) {
			logger.warning("...Failed to initalize imixsarchive keyspace: " + e.getMessage());
			return false;

		}
	}

	/**
	 * This method stops the scheduler.
	 *
	 * 
	 * @throws BackupException
	 */
	public boolean stopScheduler() throws BackupException {
		stop(findTimer());
		return true;
	}

	/**
	 * Updates the timer details of a running timer service. The method updates the
	 * properties netxtTimeout and store them into the timer configuration.
	 * 
	 * 
	 * @param configuration - the current scheduler configuration to be updated.
	 */
	public Date getNextTimeout() {
		Timer timer;
		try {
			timer = this.findTimer();
			if (timer != null) {
				// load current timer details
				return timer.getNextTimeout();
			}
		} catch (Exception e) {
			logger.warning("unable to updateTimerDetails: " + e.getMessage());
		}
		return null;
	}

	/**
	 * Starts a new Timer for the scheduler defined by the Configuration.
	 * <p>
	 * The Timer can be started based on a Calendar setting stored in the property
	 * _scheduler_definition.
	 * <p>
	 * The item 'keyspace' of the configuration entity is the id of the timer to be
	 * controlled.
	 * <p>
	 * The method throws an exception if the configuration entity contains invalid
	 * attributes or values.
	 * <p>
	 * After the timer was started the configuration is updated with the latest
	 * statusmessage. The item _schedueler_enabled will be set to 'true'.
	 * <p>
	 * The method returns the updated configuration. The configuration will not be
	 * saved!
	 * 
	 * @param configuration - scheduler configuration
	 * @return updated configuration
	 * @throws BackupException
	 */
	private void start() throws BackupException {
		Timer timer = null;

		// try to cancel an existing timer for this workflowinstance
		timer = findTimer();
		if (timer != null) {
			try {
				timer.cancel();
				timer = null;
			} catch (Exception e) {
				logController.warning("Failed to stop existing timer - " + e.getMessage());
				throw new BackupException(BackupService.class.getName(), BackupException.INVALID_WORKITEM,
						" failed to cancel existing timer!");
			}
		}

		try {
			logger.finest("...starting scheduler export-service ...");
			// New timer will be started on calendar confiugration
			timer = createTimerOnCalendar();
			// start and set statusmessage
			if (timer != null) {
				logController.info("Timer started.");
			}

		} catch (ParseException e) {
			throw new BackupException(BackupService.class.getName(), BackupException.INVALID_WORKITEM,
					" failed to start timer: " + e.getMessage());
		}

	}

	/**
	 * Cancels the running timer instance.
	 * 
	 * @throws BackupException
	 */
	private void stop(Timer timer) throws BackupException {
		if (timer != null) {
			try {
				timer.cancel();
			} catch (Exception e) {
				logController.warning("Failed to stop timer - " + e.getMessage());
			}
			// update status message
			logController.info("Timer stopped. ");
		}
	}

	/**
	 * This method returns a timer for a corresponding id if such a timer object
	 * exists.
	 * 
	 * @param id
	 * @return Timer
	 * @throws Exception
	 */
	private Timer findTimer() {
		for (Object obj : timerService.getTimers()) {
			Timer timer = (javax.ejb.Timer) obj;
			if (TIMER_ID_EXPORTSERVICE.equals(timer.getInfo())) {
				return timer;
			}
		}
		return null;
	}

	/**
	 * Create a calendar-based timer based on a input schedule expression. The
	 * expression will be parsed by this method.
	 * 
	 * Example: <code>
	 *   second=0
	 *   minute=0
	 *   hour=*
	 *   dayOfWeek=
	 *   dayOfMonth=
	 *   month=
	 *   year=*
	 * </code>
	 * 
	 * @param sConfiguation
	 * @return
	 * @throws ParseException
	 * @throws BackupException
	 */
	Timer createTimerOnCalendar() throws ParseException, BackupException {

		TimerConfig timerConfig = new TimerConfig();

		timerConfig.setInfo(TIMER_ID_EXPORTSERVICE);
		ScheduleExpression scheduerExpression = new ScheduleExpression();

		if (!schedulerDefinition.isPresent() || schedulerDefinition.get().isEmpty()) {
			logController.warning("no scheduler definition found!");
			return null;
		}

		String calendarConfiguation[] = schedulerDefinition.get().split("(\\r?\\n)|(;)|(,)");

		// try to parse the configuration list....
		for (String confgEntry : calendarConfiguation) {

			if (confgEntry.startsWith("second=")) {
				scheduerExpression.second(confgEntry.substring(confgEntry.indexOf('=') + 1));
			}
			if (confgEntry.startsWith("minute=")) {
				scheduerExpression.minute(confgEntry.substring(confgEntry.indexOf('=') + 1));
			}
			if (confgEntry.startsWith("hour=")) {
				scheduerExpression.hour(confgEntry.substring(confgEntry.indexOf('=') + 1));
			}
			if (confgEntry.startsWith("dayOfWeek=")) {
				scheduerExpression.dayOfWeek(confgEntry.substring(confgEntry.indexOf('=') + 1));
			}
			if (confgEntry.startsWith("dayOfMonth=")) {
				scheduerExpression.dayOfMonth(confgEntry.substring(confgEntry.indexOf('=') + 1));
			}
			if (confgEntry.startsWith("month=")) {
				scheduerExpression.month(confgEntry.substring(confgEntry.indexOf('=') + 1));
			}
			if (confgEntry.startsWith("year=")) {
				scheduerExpression.year(confgEntry.substring(confgEntry.indexOf('=') + 1));
			}
			if (confgEntry.startsWith("timezone=")) {
				scheduerExpression.timezone(confgEntry.substring(confgEntry.indexOf('=') + 1));
			}

			/* Start date */
			if (confgEntry.startsWith("start=")) {
				SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
				Date convertedDate = dateFormat.parse(confgEntry.substring(confgEntry.indexOf('=') + 1));
				scheduerExpression.start(convertedDate);
			}

			/* End date */
			if (confgEntry.startsWith("end=")) {
				SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
				Date convertedDate = dateFormat.parse(confgEntry.substring(confgEntry.indexOf('=') + 1));
				scheduerExpression.end(convertedDate);
			}

		}

		Timer timer = timerService.createCalendarTimer(scheduerExpression, timerConfig);

		return timer;

	}

}
