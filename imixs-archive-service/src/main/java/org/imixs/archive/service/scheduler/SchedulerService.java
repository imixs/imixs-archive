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
package org.imixs.archive.service.scheduler;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.ScheduleExpression;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DocumentService;
import org.imixs.archive.service.rest.SyncService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.QueryException;
import org.imixs.workflow.xml.XMLDataCollection;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;

/**
 * The SchedulerService runns the TimerService based on the given configuration.
 * <p>
 * The service provides a message log which can be used to monitor the timer
 * status.
 * 
 * 
 * @author rsoika
 * 
 */

@Stateless
public class SchedulerService {

	public final static String ITEM_SCHEDULER_ENABLED = "_scheduler_enabled";
	public final static String DEFAULT_SCHEDULER_DEFINITION = "hour=*";

	private final static int MAX_COUNT = 100;

	@Resource
	SessionContext ctx;

	@Resource
	javax.ejb.TimerService timerService;

	@EJB
	SyncService syncService;

	@EJB
	DocumentService documentService;

	@EJB
	ClusterService clusterService;

	@EJB
	MessageService messageService;

	private static Logger logger = Logger.getLogger(SchedulerService.class.getName());

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
	 * @throws ArchiveException
	 */
	public void start() throws ArchiveException {
		Timer timer = null;

		String id = ClusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_KEYSPACE, null);
		// try to cancel an existing timer for this workflowinstance
		timer = findTimer(id);
		if (timer != null) {
			try {
				timer.cancel();
				timer = null;
			} catch (Exception e) {
				messageService.logMessage("...failed to stop existing timer for '" + id + "'!");
				throw new ArchiveException(SchedulerService.class.getName(), ArchiveException.INVALID_WORKITEM,
						" failed to cancle existing timer!");
			}
		}

		try {
			logger.info("...Scheduler Service " + id + " will be started...");
			// New timer will be started on calendar confiugration
			timer = createTimerOnCalendar();

			// start and set statusmessage
			if (timer != null) {
				messageService.logMessage("...Timer Service " + id + " successfull started.");
			}

		} catch (ParseException e) {
			throw new ArchiveException(SchedulerService.class.getName(), ArchiveException.INVALID_WORKITEM,
					" failed to start timer: " + e.getMessage());
		}

	}

	/**
	 * Cancels a running timer instance. After cancel a timer the corresponding
	 * timerDescripton (ItemCollection) is no longer valid.
	 * <p>
	 * The method returns the current configuration. The configuration will not be
	 * saved!
	 * 
	 * @throws ArchiveException
	 * 
	 * 
	 */
	public void stop() throws ArchiveException {
		String id = ClusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_KEYSPACE, null);
		Timer timer = findTimer(id);
		stop(timer);

	}

	public void stop(Timer timer) throws ArchiveException {
		String id = ClusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_KEYSPACE, null);
		if (timer != null) {
			try {
				timer.cancel();
			} catch (Exception e) {
				messageService.logMessage("...failed to stop Timer Service '" + id + "'!");

			}

			// update status message
			messageService.logMessage("...Timer Service " + id + " stopped. ");
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
	public Timer findTimer(String id) {
		for (Object obj : timerService.getTimers()) {
			Timer timer = (javax.ejb.Timer) obj;
			if (id.equals(timer.getInfo())) {
				return timer;
			}
		}
		return null;
	}

	/**
	 * Updates the timer details of a running timer service. The method updates the
	 * properties netxtTimeout and store them into the timer configuration.
	 * 
	 * 
	 * @param configuration - the current scheduler configuration to be updated.
	 */
	public Date getNextTimeout() {
		String id = ClusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_KEYSPACE, null);

		Timer timer;
		try {
			timer = this.findTimer(id);
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
		String id = ClusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_KEYSPACE, null);
		timerConfig.setInfo(id);

		ScheduleExpression scheduerExpression = new ScheduleExpression();

		String sDefinition = ClusterService.getEnv(ClusterService.ENV_ARCHIVE_SCHEDULER_DEFINITION,
				DEFAULT_SCHEDULER_DEFINITION);
		String calendarConfiguation[] = sDefinition.split("(\\r?\\n)|(;)|(,)");

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
		String errorMes = "";
		// ItemCollection metaData = null;
		// start time....
		long lProfiler = System.currentTimeMillis();
		String keyspaceID = timer.getInfo().toString();

		try {
			// ...start processing
			logger.info("...run scheduler '" + keyspaceID + "....");
			// metaData = metadataService.loadMetadata();
			int count = 0;

			while (count < MAX_COUNT) {
				long syncPoint = documentService.getSyncpoint();
				XMLDataCollection xmlDataCollection = syncService.readSyncData(syncPoint);

				if (xmlDataCollection != null) {
					List<XMLDocument> snapshotList = Arrays.asList(xmlDataCollection.getDocument());

					for (XMLDocument xmlDocument : snapshotList) {
						count++;
						ItemCollection snapshot = XMLDocumentAdapter.putDocument(xmlDocument);

						// update snypoint
						Date syncpointdate = snapshot.getItemValueDate("$modified");
						logger.info("...data found - new syncpoint=" + syncpointdate.getTime());

						// store data into archive
						documentService.saveDocument(snapshot);
						// metaData.setItemValue(ImixsArchiveApp.ITEM_SYNCPOINT,
						// syncpointdate.getTime());
						// update stats....
						// int syncs = metaData.getItemValue("_sync_count", Integer.class);
						// syncs++;
						// metaData.setItemValue("_sync_count", syncs);
					}

				} else {
					// no more syncpoints
					logger.info("...no more data found for syncpoint: " + syncPoint);
					break;
				}
			}

			messageService.logMessage("...scheduler  '" + keyspaceID + "' finished - " + count + " snapshots synchronized in: "
					+ ((System.currentTimeMillis()) - lProfiler) + " ms");

		} catch (ArchiveException | RuntimeException e) {
			// in case of an exception we did not cancel the Timer service
			if (logger.isLoggable(Level.FINEST)) {
				e.printStackTrace();
			}
			errorMes = e.getMessage();
			messageService.logMessage("Scheduler '" + keyspaceID + "' failed: " + errorMes);

			stop(timer);
		} finally {

		}
	}

}
