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
package org.imixs.archive.service.exports;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.ScheduleExpression;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.archive.service.util.FTPConnector;
import org.imixs.archive.service.util.MessageService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.QueryException;
import org.imixs.workflow.xml.XMLDocumentAdapter;

/**
 * The ExportService exports the workflow data stored in the cassandra cluster
 * into a FTP storage. The service class runns a TimerService based on the given
 * scheduler configuration.
 * <p>
 * The service automatially starts during deployment.
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
public class ExportService {

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

	public final static String MESSAGE_TOPIC = "export";

	@Inject
	@ConfigProperty(name = ENV_EXPORT_SCHEDULER_DEFINITION, defaultValue = "")
	String schedulerDefinition;

	@Inject
	@ConfigProperty(name = ENV_EXPORT_FTP_HOST, defaultValue = "")
	String ftpServer;

	@Resource
	javax.ejb.TimerService timerService;

	@Inject
	DataService dataService;

	@Inject
	ClusterService clusterService;

	@Inject
	MessageService messageService;

	@Inject
	FTPConnector ftpConnector;

	private static Logger logger = Logger.getLogger(ExportService.class.getName());

	/**
	 * This method initializes the scheduler.
	 * <p>
	 * The method also verifies the existence of the archive keyspace by loading the
	 * archive session object.
	 * 
	 * @throws ArchiveException
	 */
	public boolean startScheduler() throws ArchiveException {
		try {
			logger.info("...starting the export scheduler...");
			if (clusterService.getSession() != null) {
				// start archive schedulers....
				start();
				return true;
			} else {
				logger.warning("...Failed to initalize imixs-archive keyspace!");
				return false;
			}
		} catch (Exception e) {
			logger.warning("...Failed to initalize imixsarchive keyspace: " + e.getMessage());
			return false;

		}
	}

	/**
	 * This method stops the scheduler.
	 *
	 * 
	 * @throws ArchiveException
	 */
	public boolean stopScheduler() throws ArchiveException {
		stop(findTimer());
		return true;
	}

	/**
	 * Updates the timer details of a running timer service. The method updates the
	 * properties netxtTimeout and store them into the timer configuration.
	 * 
	 * 
	 * @param configuration
	 *            - the current scheduler configuration to be updated.
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
	 * @param configuration
	 *            - scheduler configuration
	 * @return updated configuration
	 * @throws ArchiveException
	 */
	private void start() throws ArchiveException {
		Timer timer = null;

		// try to cancel an existing timer for this workflowinstance
		timer = findTimer();
		if (timer != null) {
			try {
				timer.cancel();
				timer = null;
			} catch (Exception e) {
				messageService.logMessage(MESSAGE_TOPIC, "Failed to stop existing timer - " + e.getMessage());
				throw new ArchiveException(ExportService.class.getName(), ArchiveException.INVALID_WORKITEM,
						" failed to cancel existing timer!");
			}
		}

		try {
			logger.finest("...starting scheduler export-service ...");
			// New timer will be started on calendar confiugration
			timer = createTimerOnCalendar();
			// start and set statusmessage
			if (timer != null) {
				messageService.logMessage(MESSAGE_TOPIC, "Timer started.");
			}

		} catch (ParseException e) {
			throw new ArchiveException(ExportService.class.getName(), ArchiveException.INVALID_WORKITEM,
					" failed to start timer: " + e.getMessage());
		}

	}

	/**
	 * Cancels the running timer instance.
	 * 
	 * @throws ArchiveException
	 */
	private void stop(Timer timer) throws ArchiveException {
		if (timer != null) {
			try {
				timer.cancel();
			} catch (Exception e) {
				messageService.logMessage(MESSAGE_TOPIC, "Failed to stop timer - " + e.getMessage());
			}
			// update status message
			messageService.logMessage(MESSAGE_TOPIC, "Timer stopped. ");
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
	 * @throws ArchiveException
	 */
	Timer createTimerOnCalendar() throws ParseException, ArchiveException {

		TimerConfig timerConfig = new TimerConfig();

		timerConfig.setInfo(TIMER_ID_EXPORTSERVICE);
		ScheduleExpression scheduerExpression = new ScheduleExpression();

		if (schedulerDefinition.isEmpty()) {
			messageService.logMessage(MESSAGE_TOPIC, "no scheduler definition found!");
			return null;
		}

		String calendarConfiguation[] = schedulerDefinition.split("(\\r?\\n)|(;)|(,)");

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

	/**
	 * This is the method which processes the timeout event depending on the running
	 * timer settings.
	 * <p>
	 * The method reads MAX_COUNT snapshot workitems from a imixs workflow instance.
	 * 
	 * 
	 * @param timer
	 * @throws Exception
	 * @throws QueryException
	 */
	@Timeout
	void onTimeout(javax.ejb.Timer timer) throws Exception {
		long syncPoint = 0;
		int exportCount = 0;
		long exportSize = 0;
		long exportErrors = 0;
		long totalCount = 0;
		long totalSize = 0;
		long latestExportPoint = 0;

		ItemCollection metaData = null;
		String lastUniqueID = null;

		if (ftpServer.isEmpty()) {
			messageService.logMessage(MESSAGE_TOPIC, "...Export failed - " + ENV_EXPORT_FTP_HOST + " not defined!");
			stop(timer);
			return;
		}

		try {

			// load metadata and get last syncpoint
			metaData = dataService.loadMetadata();
			syncPoint = metaData.getItemValueLong(ITEM_EXPORTPOINT);
			totalCount = metaData.getItemValueLong(ITEM_EXPORTCOUNT);
			totalSize = metaData.getItemValueLong(ITEM_EXPORTSIZE);

			latestExportPoint = syncPoint;

			// ...start sync
			messageService.logMessage(MESSAGE_TOPIC, "...Export started, syncpoint=" + new Date(syncPoint) + "...");

			// Daylight Saving Time Correction
			// issue #53
			Date now = new Date();
			if (syncPoint > now.getTime()) {
				logger.warning("...current syncpoint (" + syncPoint + ") is in the future! Adjust Syncpoint to now ("
						+ now.getTime() + ")....");
				syncPoint = now.getTime();
			}

			// we always restart the export on our last syncPoint....
			LocalDate localDateSyncPoint = new Date(syncPoint).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
			LocalDate localDateNow = new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

			while (!localDateSyncPoint.isAfter(localDateNow)) {
				int localCount = 0;
				long lProfiler = System.currentTimeMillis();
				logger.info("...export sync point=" + localDateSyncPoint);
				List<String> result = dataService.loadSnapshotsByDate(localDateSyncPoint);
				// iterate over all snapshots....
				for (String snapshotID : result) {
					// for each snapshotID test if we find a later snapshotID (created after our
					// current sync point)...
					String latestSnapshot = findLatestSnapshotID(snapshotID);
					if (latestSnapshot.equals(snapshotID)) {
						// we did NOT find a later snapshot, so this is the one we should export
						ItemCollection snapshot = dataService.loadSnapshot(snapshotID);
						// test if the $modified date is behind our current sync point....
						Date snaptshotDate = snapshot.getItemValueDate("$modified");
						if (snaptshotDate.getTime() > syncPoint) {
							// start export for this snapshot
							ftpConnector.put(snapshot);

							long _tmpSize = dataService.calculateSize(XMLDocumentAdapter.getDocument(snapshot));
							logger.finest("......size=: " + _tmpSize);
							exportSize = exportSize + _tmpSize;
							exportCount++;
							localCount++;

							// compute latesExportPoint we found so far...
							if (snaptshotDate.getTime() > latestExportPoint) {
								latestExportPoint = snaptshotDate.getTime();
							}

						}

						
					}
					
					// update meta data?
					if (localCount >= 100) {

						messageService.logMessage(MESSAGE_TOPIC, "...... [" + localDateSyncPoint + "] " + localCount
								+ " snapshots exported in " + (System.currentTimeMillis() - lProfiler) + "ms, last export="+new Date(latestExportPoint));
						// reset local count
						localCount = 0;
						// update sync data...
						metaData.setItemValue(ITEM_EXPORTPOINT, latestExportPoint);
						metaData.setItemValue(ITEM_EXPORTCOUNT, totalCount + exportCount);
						metaData.setItemValue(ITEM_EXPORTSIZE, totalSize + exportSize);
						metaData.setItemValue(ITEM_EXPORTERRORS, exportErrors);
						dataService.saveMetadata(metaData);
					}

				}

				// update sync data...
				metaData.setItemValue(ITEM_EXPORTPOINT, latestExportPoint);
				metaData.setItemValue(ITEM_EXPORTCOUNT, totalCount + exportCount);
				metaData.setItemValue(ITEM_EXPORTSIZE, totalSize + exportSize);
				metaData.setItemValue(ITEM_EXPORTERRORS, exportErrors);
				dataService.saveMetadata(metaData);

				// adjust snyncdate for one day....
				localDateSyncPoint = localDateSyncPoint.plusDays(1);
			}

			messageService.logMessage(MESSAGE_TOPIC, "...Export finished, " + exportCount
					+ " snapshots exported in total = " + messageService.userFriendlyBytes(exportSize) + ".");
		} catch (ArchiveException | RuntimeException e) {
			// print the stack trace
			e.printStackTrace();
			messageService.logMessage(MESSAGE_TOPIC, "Export failed "
					+ ("0".equals(lastUniqueID) ? " (failed to save metadata)" : "(last uniqueid=" + lastUniqueID + ")")
					+ " : " + e.getMessage());

			stop(timer);
		}
	}

	/**
	 * THis method reset the export sync data and returns an updated metaData object
	 * 
	 * @throws ArchiveException
	 * @return an updated meta data object
	 */
	public ItemCollection reset() throws ArchiveException {
		logger.info("Reset Export SyncPoint...");
		ItemCollection metaData = dataService.loadMetadata();
		// update sync date...
		metaData.setItemValue(ExportService.ITEM_EXPORTPOINT, 0);
		metaData.setItemValue(ExportService.ITEM_EXPORTCOUNT, 0);
		metaData.setItemValue(ExportService.ITEM_EXPORTERRORS, 0);
		metaData.setItemValue(ExportService.ITEM_EXPORTSIZE, 0);
		dataService.saveMetadata(metaData);
		return metaData;
	}

	/**
	 * The method finds for a given SnapshotID the corresponding latest snapshotID.
	 * There for the method loads the list of snapshotIDs in reverse order with a limit of 1.
	 * 
	 * @param snapshotID
	 *            - a snapshotID to be analyzed
	 * @return latest snapshot ID
	 */
	String findLatestSnapshotID(String snapshotID) {
		List<String> _tmpSnapshots = dataService.loadSnapshotsByUnqiueID(dataService.getUniqueID(snapshotID),1,true);
		if (_tmpSnapshots!=null && _tmpSnapshots.size()>0) {
		    return _tmpSnapshots.get(0);
		}
		return null;
	}

}
