package org.imixs.archive.lucene;

/*******************************************************************************
 *  Imixs Workflow 
 *  Copyright (C) 2001, 2011 Imixs Software Solutions GmbH,  
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
 *  Project: 
 *  	http://www.imixs.org
 *  	http://java.net/projects/imixs-workflow
 *  
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika - Software Developer
 *******************************************************************************/

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RunAs;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.ScheduleExpression;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.exceptions.InvalidAccessException;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.exceptions.ProcessingErrorException;
import org.imixs.workflow.exceptions.QueryException;

/**
 * This EJB implements a TimerService which scans snapshot-workitems and
 * updates the imixs-archive lucene index.
 * 
 * @see LuceneDocumentService
 * 
 * @author rsoika
 * 
 */
@Stateless
@LocalBean
@DeclareRoles({ "org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@RunAs("org.imixs.ACCESSLEVEL.MANAGERACCESS")
public class LuceneDocumentSchedulerService {

	final static public String TYPE_CONFIGURATION = "configuration";
	final static public String NAME = "org.imixs.archive.lucene.scheduler";

	final static private int MAX_RESULT=100;
	
	private static Logger logger = Logger.getLogger(LuceneDocumentSchedulerService.class.getName());

	@EJB
	DocumentService documentService;
	
	@Resource
	javax.ejb.TimerService timerService;

	@Resource
	SessionContext ctx;

	int iProcessWorkItems = 0;
	List<String> unprocessedIDs = null;

	/**
	 * This method loads the current scheduler configuration. If no configuration
	 * entity yet exists the method returns an empty ItemCollection.
	 * 
	 * The method updates the timer details for a running timer.
	 * 
	 * @return configuration ItemCollection
	 */
	public ItemCollection loadConfiguration() {
		ItemCollection configItemCollection = null;
		String searchTerm = "(type:\"" + TYPE_CONFIGURATION + "\" AND txtname:\"" + NAME + "\")";

		Collection<ItemCollection> col;
		try {
			col = documentService.find(searchTerm, 2, 0);
		} catch (QueryException e) {
			logger.severe("loadConfiguration - invalid param: " + e.getMessage());
			throw new InvalidAccessException(InvalidAccessException.INVALID_ID, e.getMessage(), e);
		}

		if (col.size() > 1) {
			String message = "loadConfiguration - more than on timer configuration found! Check configuration (type:\"configuration\" txtname:\"org.imixs.workflow.scheduler\") ";
			logger.severe(message);
			throw new InvalidAccessException(InvalidAccessException.INVALID_ID, message);
		}

		if (col.size() == 1) {
			logger.fine("loading existing timer configuration...");
			configItemCollection = col.iterator().next();
		} else {
			logger.fine("creating new timer configuration...");
			// create default values
			configItemCollection = new ItemCollection();
			configItemCollection.replaceItemValue("type", TYPE_CONFIGURATION);
			configItemCollection.replaceItemValue("txtname", NAME);
			configItemCollection.replaceItemValue(WorkflowKernel.UNIQUEID, WorkflowKernel.generateUniqueID());
		}
		configItemCollection = updateTimerDetails(configItemCollection);
		return configItemCollection;
	}

	/**
	 * This method saves the timer configuration. The method ensures that the
	 * following properties are set to default.
	 * <ul>
	 * <li>type</li>
	 * <li>txtName</li>
	 * <li>$writeAccess</li>
	 * <li>$readAccess</li>
	 * </ul>
	 * 
	 * The method also updates the timer details of a running timer.
	 * 
	 * @return
	 * @throws AccessDeniedException
	 */
	public ItemCollection saveConfiguration(ItemCollection configItemCollection) throws AccessDeniedException {
		// update write and read access
		configItemCollection.replaceItemValue("type", TYPE_CONFIGURATION);
		configItemCollection.replaceItemValue("txtName", NAME);
		configItemCollection.replaceItemValue("$writeAccess", "org.imixs.ACCESSLEVEL.MANAGERACCESS");
		configItemCollection.replaceItemValue("$readAccess", "org.imixs.ACCESSLEVEL.MANAGERACCESS");

		// configItemCollection.replaceItemValue("$writeAccess", "");
		// configItemCollection.replaceItemValue("$readAccess", "");

		configItemCollection = updateTimerDetails(configItemCollection);
		// save entity
		configItemCollection = documentService.save(configItemCollection);

		return configItemCollection;
	}

	/**
	 * This Method starts the TimerService.
	 * 
	 * The Timer can be started based on a Calendar setting stored in the property
	 * txtConfiguration, or by interval based on the properties datStart, datStop,
	 * numIntervall.
	 * 
	 * 
	 * The method loads the configuration entity and evaluates the timer
	 * configuration. THe $UniqueID of the configuration entity is the id of the
	 * timer to be controlled.
	 * 
	 * $uniqueid - String - identifier for the Timer Service.
	 * 
	 * txtConfiguration - calendarBasedTimer configuration
	 * 
	 * datstart - Date Object
	 * 
	 * datstop - Date Object
	 * 
	 * numInterval - Integer Object (interval in seconds)
	 * 
	 * 
	 * The method throws an exception if the configuration entity contains invalid
	 * attributes or values.
	 * 
	 * After the timer was started the configuration is updated with the latest
	 * statusmessage
	 * 
	 * The method returns the current configuration
	 * 
	 * @throws AccessDeniedException
	 * @throws ParseException
	 */
	public ItemCollection start() throws AccessDeniedException, ParseException {
		ItemCollection configItemCollection = loadConfiguration();
		Timer timer = null;
		if (configItemCollection == null)
			return null;

		String id = configItemCollection.getUniqueID();

		// try to cancel an existing timer for this workflowInstance
		while (this.findTimer(id) != null) {
			this.findTimer(id).cancel();
		}

		String sConfiguation = configItemCollection.getItemValueString("txtConfiguration");

		if (!sConfiguation.isEmpty()) {
			// New timer will be started on calendar confiugration
			timer = createTimerOnCalendar(configItemCollection);
		} else {
			// update the interval based on hour/minute configuration
			int hours = configItemCollection.getItemValueInteger("hours");
			int minutes = configItemCollection.getItemValueInteger("minutes");
			long interval = (hours * 60 + minutes) * 60 * 1000;
			configItemCollection.replaceItemValue("numInterval", new Long(interval));

			timer = createTimerOnInterval(configItemCollection);
		}

		// start the timer and set a status message
		if (timer != null) {

			Calendar calNow = Calendar.getInstance();
			SimpleDateFormat dateFormatDE = new SimpleDateFormat("dd.MM.yy hh:mm:ss");
			String msg = "started at " + dateFormatDE.format(calNow.getTime()) + " by "
					+ ctx.getCallerPrincipal().getName();
			configItemCollection.replaceItemValue("statusmessage", msg);

			if (timer.isCalendarTimer()) {
				configItemCollection.replaceItemValue("Schedule", timer.getSchedule().toString());
			} else {
				configItemCollection.replaceItemValue("Schedule", "");

			}
			logger.info(configItemCollection.getItemValueString("txtName") + " started: " + id);
		}

		configItemCollection = saveConfiguration(configItemCollection);

		return configItemCollection;
	}

	/**
	 * Stops a running timer instance. After the timer was canceled the
	 * configuration will be updated.
	 * 
	 * @throws AccessDeniedException
	 * 
	 */
	public ItemCollection stop() throws AccessDeniedException {
		ItemCollection configItemCollection = loadConfiguration();

		String id = configItemCollection.getUniqueID();
		boolean found = false;
		while (this.findTimer(id) != null) {
			this.findTimer(id).cancel();
			found = true;
		}
		if (found) {
			Calendar calNow = Calendar.getInstance();
			SimpleDateFormat dateFormatDE = new SimpleDateFormat("dd.MM.yy hh:mm:ss");
			String msg = "stopped at " + dateFormatDE.format(calNow.getTime()) + " by "
					+ ctx.getCallerPrincipal().getName();
			configItemCollection.replaceItemValue("statusmessage", msg);
			logger.info(configItemCollection.getItemValueString("txtName") + " stopped: " + id);
		} else {
			configItemCollection.replaceItemValue("statusmessage", "");
		}
		configItemCollection = saveConfiguration(configItemCollection);

		return configItemCollection;
	}

	/**
	 * Returns true if the workflowSchedulerService was started
	 */
	public boolean isRunning() {
		try {
			ItemCollection configItemCollection = loadConfiguration();
			if (configItemCollection == null)
				return false;

			return (findTimer(configItemCollection.getUniqueID()) != null);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * This method process scheduled workitems. The method updates the property
	 * 'datLastRun'
	 * 
	 * Because of bug: https://java.net/jira/browse/GLASSFISH-20673 we check the
	 * imixsDayOfWeek
	 * 
	 * @param timer
	 * @throws AccessDeniedException
	 */
	@Timeout
	void runTimer(javax.ejb.Timer timer) throws AccessDeniedException {

		ItemCollection configItemCollection = loadConfiguration();
		logger.info(" started....");

	

		configItemCollection.replaceItemValue("datLastRun", new Date());

		/*
		 * Now we process all scheduled worktitems for each model
		 */
		iProcessWorkItems = 0;
		unprocessedIDs = new ArrayList<String>();
		
		/**
		 * find modification since last run
		 */
	
			String query = "SELECT document FROM Document AS document ";
			query += " WHERE document.modified > '2017-01-01'";
			query += " ORDER BY document.modified DESC";
			List<ItemCollection> result = documentService.getDocumentsByQuery(query, MAX_RESULT);
			if (result.size() >= 1) {
				
			} else {
				
			}
	
		
		
		
		logger.info("finished successfull");

		logger.info(iProcessWorkItems + " workitems processed");

		if (unprocessedIDs.size() > 0) {
			logger.warning(unprocessedIDs.size() + " workitems could be processed!");
			for (String aid : unprocessedIDs) {
				logger.warning("          " + aid);
			}

		}

		Date endDate = configItemCollection.getItemValueDate("datstop");
		String sTimerID = configItemCollection.getItemValueString("$uniqueid");

		// update statistic of last run
		configItemCollection.replaceItemValue("numWorkItemsProcessed", iProcessWorkItems);
		configItemCollection.replaceItemValue("numWorkItemsUnprocessed", unprocessedIDs.size());

		/*
		 * Check if Timer should be canceled now? - only by interval configuration. In
		 * case of calenderBasedTimer the timer will stop automatically.
		 */
		String sConfiguation = configItemCollection.getItemValueString("txtConfiguration");

		if (sConfiguation.isEmpty()) {

			Calendar calNow = Calendar.getInstance();
			if (endDate != null && calNow.getTime().after(endDate)) {
				timer.cancel();
				System.out.println("Timeout - sevice stopped: " + sTimerID);

				SimpleDateFormat dateFormatDE = new SimpleDateFormat("dd.MM.yy hh:mm:ss");
				String msg = "stopped at " + dateFormatDE.format(calNow.getTime()) + " by datstop="
						+ dateFormatDE.format(endDate);
				configItemCollection.replaceItemValue("statusmessage", msg);

			}
		}

		// save configuration
		configItemCollection = saveConfiguration(configItemCollection);

	}

	/**
	 * Create an interval timer whose first expiration occurs at a given point in
	 * time and whose subsequent expirations occur after a specified interval.
	 **/
	Timer createTimerOnInterval(ItemCollection configItemCollection) {
		// Create an interval timer
		Date startDate = configItemCollection.getItemValueDate("datstart");
		Date endDate = configItemCollection.getItemValueDate("datstop");
		long interval = configItemCollection.getItemValueInteger("numInterval");

		// set default start date?
		if (startDate == null) {
			// set start date to now
			startDate = new Date();
		}

		// check if endDate is before start date, than we do not start the
		// timer!
		if (endDate != null) {
			Calendar calStart = Calendar.getInstance();
			calStart.setTime(startDate);
			Calendar calEnd = Calendar.getInstance();
			calEnd.setTime(endDate);
			if (calStart.after(calEnd)) {
				logger.warning(configItemCollection.getItemValueString("txtName") + " stop-date (" + startDate
						+ ") is before start-date (" + endDate + "). Timer will not be started!");
				return null;
			}
		}
		Timer timer = null;
		// create timer object ($uniqueId)
		timer = timerService.createTimer(startDate, interval, configItemCollection.getUniqueID());
		return timer;

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
	 *   dayOfMonth=25–Last,1–5
	 *   month=
	 *   year=*
	 * </code>
	 * 
	 * @param sConfiguation
	 * @return
	 * @throws ParseException
	 */
	Timer createTimerOnCalendar(ItemCollection configItemCollection) throws ParseException {

		TimerConfig timerConfig = new TimerConfig();

		timerConfig.setInfo(configItemCollection.getUniqueID());
		ScheduleExpression scheduerExpression = new ScheduleExpression();

		@SuppressWarnings("unchecked")
		List<String> calendarConfiguation = (List<String>) configItemCollection.getItemValue("txtConfiguration");
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
	 * This method returns a timer for a corresponding id if such a timer object
	 * exists.
	 * 
	 * @param id
	 * @return Timer
	 * @throws Exception
	 */
	Timer findTimer(String id) {
		Timer timer = null;
		for (Object obj : timerService.getTimers()) {
			Timer atimer = (javax.ejb.Timer) obj;
			String timerID = atimer.getInfo().toString();
			if (id.equals(timerID)) {
				if (timer != null) {
					logger.severe("more then one timer with id " + id + " was found!");
				}
				timer = atimer;
			}
		}
		return timer;
	}

	/**
	 * Update the timer details of a running timer service. The method updates the
	 * properties netxtTimeout and timeRemaining and store them into the timer
	 * configuration.
	 * 
	 * @param configuration
	 */
	private ItemCollection updateTimerDetails(ItemCollection configuration) {
		if (configuration == null)
			return configuration;
		String id = configuration.getUniqueID();
		Timer timer;
		try {
			timer = this.findTimer(id);

			if (timer != null) {
				// load current timer details
				configuration.replaceItemValue("nextTimeout", timer.getNextTimeout());
				configuration.replaceItemValue("timeRemaining", timer.getTimeRemaining());
			} else {
				configuration.removeItem("nextTimeout");
				configuration.removeItem("timeRemaining");
			}
		} catch (Exception e) {
			logger.warning("unable to updateTimerDetails: " + e.getMessage());
			configuration.removeItem("nextTimeout");
			configuration.removeItem("timeRemaining");
		}
		return configuration;
	}

}
