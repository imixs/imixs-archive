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
import java.util.Date;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.NoMoreTimeoutsException;
import javax.ejb.NoSuchObjectLocalException;
import javax.ejb.ScheduleExpression;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;

import org.imixs.workflow.ItemCollection;
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

	@Resource
	SessionContext ctx;

	@Resource
	javax.ejb.TimerService timerService;

	@EJB
	SyncService syncService;
	
	@EJB
	ClusterService clusterService;

	private static Logger logger = Logger.getLogger(SchedulerService.class.getName());

	/**
	 * This Method starts the TimerService. If a timer with the id was already
	 * running, than the method will stop this timer instance before.
	 * 
	 * The Timer can be started based on a Calendar setting stored in the property
	 * 'pollingInterval'
	 * 
	 * The method throws an exception if the configuration entity contains invalid
	 * attributes or values.
	 * 
	 * @throws ParseException
	 */
	public boolean start(ItemCollection configItemCollection) {
		if (configItemCollection == null) {
			logger.warning("...invalid configuraiton object");
			return false;
		}

		String id = configItemCollection.getItemValueString("keyspace");
		logger.info("...starting scheduler for archive '" + id + "'");

		// try to cancel an existing timer for this workflowinstance
		while (this.findTimer(id) != null) {
			this.findTimer(id).cancel();
		}

		// New timer will be started on calendar confiugration
		try {
			Timer timer = createTimerOnCalendar(configItemCollection);
			return (timer != null);
		} catch (ParseException e) {
			logger.severe("starting scheduler for '" + id + "' failed: " + e.getMessage());
		}

		return false;
	}

	/**
	 * Cancels a running timer instance.
	 * 
	 * @param true if timer was found and successfull canceled.
	 * 
	 */
	public boolean stop(ItemCollection config) {
		String id = config.getItemValueString("keyspace");
		logger.info("...stopping timer with id '" + id + "'");
		boolean found = false;
		while (this.findTimer(id) != null) {
			this.findTimer(id).cancel();
			found = true;
		}
		logger.warning("No running timer with id '" + id + "' found.");
		return found;
	}

	/**
	 * This method returns a timer for a corresponding id if such a timer object
	 * exists.
	 * 
	 * @param id
	 * @return Timer
	 * @throws Exception
	 */
	private Timer findTimer(String id) {
		for (Object obj : timerService.getTimers()) {
			Timer timer = (javax.ejb.Timer) obj;

			if (timer.getInfo() instanceof XMLDocument) {
				XMLDocument xmlItemCollection = (XMLDocument) timer.getInfo();
				ItemCollection adescription = XMLDocumentAdapter.putDocument(xmlItemCollection);
				if (id.equals(adescription.getItemValueString("keyspace"))) {
					return timer;
				}
			}
		}
		return null;
	}

	/**
	 * Returns returns the number of milliseconds that will elapse before the next
	 * scheduled timer expiration for a given keyspace.
	 * 
	 * The method returns -1 if no timer exists.
	 * 
	 * @param id
	 * @return
	 */
	public long getTimeRemaining(String id) {
		Timer timer = findTimer(id);

		if (timer != null) {
			try {
				long l = timer.getTimeRemaining();

				if (l > 0) {
					logger.finest("...... timer '" + id + " - times remaining: " + l + "ms");
				}
				return l;
			} catch (IllegalStateException | NoSuchObjectLocalException | NoMoreTimeoutsException e) {
				logger.warning("Error get timer status: " + e.getMessage());
			}
		}

		return -1;
	}

	/**
	 * This method pulls the archive data into the cassandra keyspace
	 * 
	 * 
	 * @param timer
	 * @throws Exception
	 * @throws QueryException
	 */
	@Timeout
	public void pullData(javax.ejb.Timer timer) throws Exception {

		// Startzeit ermitteln
		long lProfiler = System.currentTimeMillis();

		logger.info("starting import....");

		XMLDocument xmlItemCollection = (XMLDocument) timer.getInfo();
		ItemCollection configuration = XMLDocumentAdapter.putDocument(xmlItemCollection);
		String keyspace = configuration.getItemValueString("keyspace");

		
		try {
		XMLDocument xmlDocument = syncService.readSyncData(configuration);
		
		if (xmlDocument != null) {
			ItemCollection snapshot=XMLDocumentAdapter.putDocument(xmlDocument);
			logger.info("......new snapshot found: " + snapshot.getUniqueID());
			clusterService.saveDocument(snapshot, configuration.getItemValueString(keyspace));

			// update stats....
			
			
			logger.info("...Data found - new Syncpoint=");
			
			int syncs=configuration.getItemValue("_sync_count", Integer.class);
			syncs++;
			configuration.setItemValue("_sync_count", syncs);
			
			
		}

		
		} catch (ImixsArchiveException e) {
			
			int errors=configuration.getItemValue("_error_count", Integer.class);
			errors++;
			configuration.setItemValue("_error_count", errors);
			
			if (ImixsArchiveException.SYNC_ERROR.equals(e.getErrorCode())) {
				 errors=configuration.getItemValue("_error_count_Sync", Integer.class);
				errors++;
				configuration.setItemValue("_error_count_Sync", errors);
			}
			
			if (ImixsArchiveException.INVALID_DOCUMENT_OBJECT.equals(e.getErrorCode())) {
				 errors=configuration.getItemValue("_error_count_Object", Integer.class);
				errors++;
				configuration.setItemValue("_error_count_Object", errors);
			}
			
			
			
		}
		
		// save the updated configuration object
		clusterService.saveConfiguration(configuration);
		
		logger.info("import finished in " + (System.currentTimeMillis() - lProfiler) + "ms");
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
	 * If no configuration is found than no timer will be created and the method
	 * returns null.
	 * 
	 * 
	 * @param sConfiguation
	 * @return
	 * @throws ParseException
	 */
	Timer createTimerOnCalendar(ItemCollection configItemCollection) throws ParseException {
		boolean found = false;
		TimerConfig timerConfig = new TimerConfig();

		String id = configItemCollection.getItemValueString("keyspace");
		logger.info("...validating timer settings for id '" + id + "'...");
		XMLDocument xmlConfigItem = null;
		try {
			xmlConfigItem = XMLDocumentAdapter.getDocument(configItemCollection);
		} catch (Exception e) {
			logger.severe("Unable to serialize confitItemCollection into a XML object");
			e.printStackTrace();
			return null;
		}

		timerConfig.setInfo(xmlConfigItem);
		ScheduleExpression scheduerExpression = new ScheduleExpression();

		String pollingData = configItemCollection.getItemValueString("pollingInterval");
		String calendarConfiguation[] = pollingData.split("\\r?\\n");
		// try to parse the configuration list....
		for (String confgEntry : calendarConfiguation) {

			if (confgEntry.startsWith("second=")) {
				scheduerExpression.second(confgEntry.substring(confgEntry.indexOf('=') + 1));
				found = true;
			}
			if (confgEntry.startsWith("minute=")) {
				scheduerExpression.minute(confgEntry.substring(confgEntry.indexOf('=') + 1));
				found = true;
			}
			if (confgEntry.startsWith("hour=")) {
				scheduerExpression.hour(confgEntry.substring(confgEntry.indexOf('=') + 1));
				found = true;
			}
			if (confgEntry.startsWith("dayOfWeek=")) {
				scheduerExpression.dayOfWeek(confgEntry.substring(confgEntry.indexOf('=') + 1));
				found = true;
			}
			if (confgEntry.startsWith("dayOfMonth=")) {
				scheduerExpression.dayOfMonth(confgEntry.substring(confgEntry.indexOf('=') + 1));
				found = true;
			}
			if (confgEntry.startsWith("month=")) {
				scheduerExpression.month(confgEntry.substring(confgEntry.indexOf('=') + 1));
				found = true;
			}
			if (confgEntry.startsWith("year=")) {
				scheduerExpression.year(confgEntry.substring(confgEntry.indexOf('=') + 1));
				found = true;
			}
			if (confgEntry.startsWith("timezone=")) {
				scheduerExpression.timezone(confgEntry.substring(confgEntry.indexOf('=') + 1));
				found = true;
			}

			/* Start date */
			if (confgEntry.startsWith("start=")) {
				SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
				Date convertedDate = dateFormat.parse(confgEntry.substring(confgEntry.indexOf('=') + 1));
				scheduerExpression.start(convertedDate);
				found = true;

			}

			/* End date */
			if (confgEntry.startsWith("end=")) {
				SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
				Date convertedDate = dateFormat.parse(confgEntry.substring(confgEntry.indexOf('=') + 1));
				scheduerExpression.end(convertedDate);
				found = true;

			}

		}

		if (found) {

			// log timer settings
			logger.finest("...scheudler settings for timer '" + id + "':");
			logger.info("...... second=" + scheduerExpression.getSecond());
			logger.info("...... minute=" + scheduerExpression.getMinute());
			logger.info("...... hour=" + scheduerExpression.getHour());
			logger.info("...... dayOfWeek=" + scheduerExpression.getDayOfWeek());
			logger.info("...... dayOfMonth=" + scheduerExpression.getDayOfMonth());
			logger.info("...... year=" + scheduerExpression.getYear());

			Timer timer = timerService.createCalendarTimer(scheduerExpression, timerConfig);

			logger.info("...timer for id '" + id + "' started...");
			return timer;
		} else {
			logger.info("...no valid timer settings for id '" + id + "' defined.");
			return null;
		}

	}

}
