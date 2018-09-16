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
package org.imixs.workflow.archive.cassandra.services;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
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

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.archive.cassandra.ImixsArchiveApp;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.exceptions.InvalidAccessException;
import org.imixs.workflow.exceptions.QueryException;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;

/**
 * The SchedulerService starts and stops TimerService for each archive
 * configuration to archive imixs-workflow data periodically.
 * 
 * The TimerService can be started using the method start(). The Methods
 * findTimerDescription and findAllTimerDescriptions are used to lookup enabled
 * and running service instances.
 * 
 * Each Method expects or generates a TimerDescription Object. This object is
 * the corresponding archive configuration.
 * 
 * the following additional attributes are generated by the finder methods and
 * can be used by an application to verfiy the status of a running instance:
 * 
 * nextTimeout - Next Timeout - pint of time when the service will be scheduled
 * 
 * timeRemaining - Timeout in milliseconds
 * 
 * statusmessage - text message
 * 
 * 
 * @author rsoika
 * 
 */
@Stateless
public class SchedulerService {

	public final static String ITEM_SCHEDULER_NAME = "txtname";
	public final static String ITEM_SCHEDULER_ENABLED = "_scheduler_enabled";
	public final static String ITEM_SCHEDULER_CLASS = "_scheduler_class";
	public final static String ITEM_SCHEDULER_DEFINITION = "_scheduler_definition";

	@Resource
	SessionContext ctx;

	@Resource
	javax.ejb.TimerService timerService;

	@EJB
	SyncService syncService;

	@EJB
	ConfigurationService confiugrationService;

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
	 * @throws AccessDeniedException
	 * @throws ParseException
	 */
	public ItemCollection start(ItemCollection configuration) throws AccessDeniedException, ParseException {
		Timer timer = null;
		if (configuration == null)
			return null;
		
		

		String id = configuration.getItemValueString(ImixsArchiveApp.ITEM_KEYSPACE);
		// try to cancel an existing timer for this workflowinstance
		timer = findTimer(id);
		if (timer != null) {
			try {
				timer.cancel();
				timer = null;
			} catch (Exception e) {
				logger.warning("...failed to stop existing timer for '" +id + "'!");
				throw new InvalidAccessException(SchedulerService.class.getName(), SchedulerException.INVALID_WORKITEM,
						" failed to cancle existing timer!");
			}
		}

		logger.info("...Scheduler Service " +id + " will be started...");
		String schedulerDescription = configuration.getItemValueString(ITEM_SCHEDULER_DEFINITION);

		if (!schedulerDescription.isEmpty()) {
			// New timer will be started on calendar confiugration
			timer = createTimerOnCalendar(configuration);
		}
		// start and set statusmessage
		if (timer != null) {

			Calendar calNow = Calendar.getInstance();
			SimpleDateFormat dateFormatDE = new SimpleDateFormat("dd.MM.yy hh:mm:ss");
			String msg = "started at " + dateFormatDE.format(calNow.getTime()) + " by "
					+ ctx.getCallerPrincipal().getName();
			configuration.replaceItemValue("statusmessage", msg);

			if (timer.isCalendarTimer()) {
				configuration.replaceItemValue("Schedule", timer.getSchedule().toString());
			} else {
				configuration.replaceItemValue("Schedule", "");

			}
			logger.info("...Scheduler Service" + id + " (" + configuration.getItemValueString("txtName")
					+ ") successfull started.");
		}
		configuration.replaceItemValue(ITEM_SCHEDULER_ENABLED, true);
		configuration.replaceItemValue("errormessage", "");
		return configuration;
	}

	/**
	 * Cancels a running timer instance. After cancel a timer the corresponding
	 * timerDescripton (ItemCollection) is no longer valid.
	 * <p>
	 * The method returns the current configuration. The configuration will not be
	 * saved!
	 * 
	 * 
	 */
	public ItemCollection stop(ItemCollection configuration) {
		String id = configuration.getItemValueString(ImixsArchiveApp.ITEM_KEYSPACE);
		Timer timer = findTimer(id);
		return stop(configuration, timer);

	}

	public ItemCollection stop(ItemCollection configuration, Timer timer) {
		String id = configuration.getItemValueString(ImixsArchiveApp.ITEM_KEYSPACE);
		if (timer != null) {
			try {
				timer.cancel();
			} catch (Exception e) {
				logger.info("...failed to stop timer for '" + id + "'!");
			}

			// update status message
			Calendar calNow = Calendar.getInstance();
			SimpleDateFormat dateFormatDE = new SimpleDateFormat("dd.MM.yy hh:mm:ss");

			String message = "stopped at " + dateFormatDE.format(calNow.getTime());
			String name = ctx.getCallerPrincipal().getName();
			if (name != null && !name.isEmpty() && !"anonymous".equals(name)) {
				message += " by " + name;
			}
			configuration.replaceItemValue("statusmessage", message);

			logger.info("... scheduler " + configuration.getItemValueString("txtName") + " stopped: "
					+ id);
		} else {
			String msg = "stopped";
			configuration.replaceItemValue("statusmessage", msg);

		}
		configuration.removeItem("nextTimeout");
		configuration.removeItem("timeRemaining");
		configuration.replaceItemValue(ITEM_SCHEDULER_ENABLED, false);
		return configuration;
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
	 * properties netxtTimeout and timeRemaining and store them into the timer
	 * configuration.
	 * 
	 * 
	 * @param configuration - the current scheduler configuration to be updated.
	 */
	public void updateTimerDetails(ItemCollection configuration) {
		if (configuration == null)
			return;// configuration;
		
		String id = configuration.getItemValueString(ImixsArchiveApp.ITEM_KEYSPACE);
		
		Timer timer;
		try {
			timer = this.findTimer(id);
			if (timer != null) {
				// load current timer details
				configuration.replaceItemValue("nextTimeout", timer.getNextTimeout());
				configuration.replaceItemValue("timeRemaining", timer.getTimeRemaining());
				configuration.replaceItemValue(ITEM_SCHEDULER_ENABLED, true);
			} else {
				configuration.removeItem("nextTimeout");
				configuration.removeItem("timeRemaining");
				configuration.replaceItemValue(ITEM_SCHEDULER_ENABLED, false);
			}
		} catch (Exception e) {
			logger.warning("unable to updateTimerDetails: " + e.getMessage());
			configuration.removeItem("nextTimeout");
			configuration.removeItem("timeRemaining");
			configuration.replaceItemValue(ITEM_SCHEDULER_ENABLED, false);
		}
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
	 */
	Timer createTimerOnCalendar(ItemCollection configuration) throws ParseException {

		TimerConfig timerConfig = new TimerConfig();
		String id = configuration.getItemValueString(ImixsArchiveApp.ITEM_KEYSPACE);
		timerConfig.setInfo(id);

		ScheduleExpression scheduerExpression = new ScheduleExpression();

		String sDefinition = configuration.getItemValueString(ITEM_SCHEDULER_DEFINITION);
		String calendarConfiguation[] = sDefinition.split("\\r?\\n");
		
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
	 * timer settings. The method calls the abstract method 'process' which need to
	 * be implemented by a subclass.
	 * 
	 * @param timer
	 * @throws Exception
	 * @throws QueryException
	 */
	@Timeout
	void onTimeout(javax.ejb.Timer timer) throws Exception {
		String errorMes = "";
		// start time....
		long lProfiler = System.currentTimeMillis();
		String keyspaceID = timer.getInfo().toString();

		ItemCollection configuration = confiugrationService.loadConfiguration(keyspaceID);

		if (configuration == null) {
			logger.severe("...failed to load scheduler configuration for current timer. Timer will be stopped...");
			return;
		}

		try {
			// ...start processing
			logger.info("...run scheduler '" + keyspaceID + "....");
			XMLDocument xmlDocument = syncService.readSyncData(configuration);

			if (xmlDocument != null) {
				ItemCollection snapshot = XMLDocumentAdapter.putDocument(xmlDocument);
				logger.info("......new snapshot found: " + keyspaceID);
			
				// update stats....
				logger.info("...Data found - new Syncpoint=");

				int syncs = configuration.getItemValue("_sync_count", Integer.class);
				syncs++;
				configuration.setItemValue("_sync_count", syncs);
				logger.info("...run scheduler  '" + keyspaceID + "' finished in: "
						+ ((System.currentTimeMillis()) - lProfiler) + " ms");

			}

		} catch (ImixsArchiveException | RuntimeException e) {
			// in case of an exception we did not cancel the Timer service
			if (logger.isLoggable(Level.FINEST)) {
				e.printStackTrace();
			}
			errorMes = e.getMessage();
			logger.severe("Scheduler '" + keyspaceID + "' failed: " + errorMes);

			configuration = stop(configuration, timer);
		} finally {
			// Save statistic in configuration
			if (configuration != null) {
				configuration.replaceItemValue("errormessage", errorMes);
				confiugrationService.saveConfiguration(configuration);

			}
		}
	}

}
