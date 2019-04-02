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
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.MessageService;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.QueryException;
import org.imixs.workflow.services.rest.BasicAuthenticator;
import org.imixs.workflow.services.rest.FormAuthenticator;
import org.imixs.workflow.services.rest.RestAPIException;
import org.imixs.workflow.services.rest.RestClient;
import org.imixs.workflow.xml.XMLDataCollection;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

/**
 * The SyncService synchronizes the workflow data with the data stored in the
 * cassandra cluster. The service class runns a TimerService based on the given
 * scheduler configuration.
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
public class SyncService {

	public final static String ITEM_SYNCPOINT = "$sync_point";
	public final static String ITEM_SYNCCOUNT = "$sync_count";
	public final static String DEFAULT_SCHEDULER_DEFINITION = "hour=*";
	public final static String SNAPSHOT_SYNCPOINT_RESOURCE = "snapshot/syncpoint/";
	public final static String SNAPSHOT_RESOURCE = "snapshot/";
	public final static String DOCUMENTS_RESOURCE = "documents/";

	private final static int MAX_COUNT = 100;

	@Resource
	javax.ejb.TimerService timerService;

	@EJB
	DataService dataService;

	@EJB
	ClusterService clusterService;

	@EJB
	MessageService messageService;

	private static Logger logger = Logger.getLogger(SyncService.class.getName());

	/**
	 * This method initializes the scheduler.
	 * <p>
	 * The method also verifies the existence of the archive keyspace by loading the
	 * archive session object.
	 * 
	 * @throws ArchiveException
	 */
	public boolean startScheduler() throws ArchiveException {
		Session session = null;
		Cluster cluster = null;
		try {
			logger.info("...init imixsarchive keyspace ...");
			cluster = clusterService.getCluster();
			session = clusterService.getArchiveSession(cluster);
			if (session != null) {
				// start archive schedulers....
				logger.info("...starting schedulers...");
				start();
				return true;
			} else {
				logger.warning("...Failed to initalize imixs-archive keyspace!");
				return false;
			}
		} catch (Exception e) {
			logger.warning("...Failed to initalize imixsarchive keyspace: " + e.getMessage());
			return false;

		} finally {
			// close session and cluster object
			if (session != null) {
				session.close();
			}
			if (cluster != null) {
				cluster.close();
			}
		}
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

		String id = ClusterService.getEnv(ClusterService.ENV_ARCHIVE_CLUSTER_KEYSPACE, null);
		// try to cancel an existing timer for this workflowinstance
		timer = findTimer(id);
		if (timer != null) {
			try {
				timer.cancel();
				timer = null;
			} catch (Exception e) {
				messageService.logMessage("Failed to stop existing timer - " + e.getMessage());
				throw new ArchiveException(SyncService.class.getName(), ArchiveException.INVALID_WORKITEM,
						" failed to cancle existing timer!");
			}
		}

		try {
			logger.info("...starting scheduler service " + id + " ...");
			// New timer will be started on calendar confiugration
			timer = createTimerOnCalendar();

			// start and set statusmessage
			if (timer != null) {
				messageService.logMessage("Timer started.");
			}

		} catch (ParseException e) {
			throw new ArchiveException(SyncService.class.getName(), ArchiveException.INVALID_WORKITEM,
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
				messageService.logMessage("Failed to stop timer - " + e.getMessage());
			}
			// update status message
			messageService.logMessage("Timer stopped. ");
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
	private Timer findTimer(String id) {
		for (Object obj : timerService.getTimers()) {
			Timer timer = (javax.ejb.Timer) obj;
			if (id.equals(timer.getInfo())) {
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
		long syncPoint = 0;
		int count = 0;
		long totalCount = 0;
		Session session = null;
		Cluster cluster = null;
		ItemCollection metaData = null;

		// start time....
		long lProfiler = System.currentTimeMillis();
		String keyspaceID = timer.getInfo().toString();

		try {
			// ...start sync
			logger.fine("...sync: '" + keyspaceID + "....");

			cluster = clusterService.getCluster();
			session = clusterService.getArchiveSession(cluster);

			// load metadata and get last syncpoint
			metaData = dataService.loadMetadata(session);
			syncPoint = metaData.getItemValueLong(ITEM_SYNCPOINT);
			totalCount = metaData.getItemValueLong(ITEM_SYNCCOUNT);

			while (count < MAX_COUNT) {

				XMLDataCollection xmlDataCollection = readSyncData(syncPoint);

				if (xmlDataCollection != null) {
					List<XMLDocument> snapshotList = Arrays.asList(xmlDataCollection.getDocument());

					for (XMLDocument xmlDocument : snapshotList) {

						ItemCollection snapshot = XMLDocumentAdapter.putDocument(xmlDocument);

						// update snypoint
						Date syncpointdate = snapshot.getItemValueDate("$modified");
						syncPoint = syncpointdate.getTime();
						logger.fine("...data found - new syncpoint=" + syncPoint);

						// verify if this snapshot is not already stored - if so, we do not overwrite
						// the origin data
						if (!dataService.existSnapshot(snapshot.getUniqueID(), session)) {
							// store data into archive
							dataService.saveSnapshot(snapshot, session);
							count++;
							totalCount++;
						} else {
							// This is because in case of a restore, the same snapshot takes a new $modified
							// item. And we do not want to re-import the snapshot in the next sync cycle.
							// see issue #40
							logger.info("...snapshot '" + snapshot.getUniqueID() + "' alredy exits....");
						}

						// update metadata
						metaData.setItemValue(ITEM_SYNCPOINT, syncPoint);
						metaData.setItemValue(ITEM_SYNCCOUNT, totalCount);
						dataService.saveMetadata(metaData, session);
					}

				} else {
					// no more syncpoints
					logger.finest("......no more data found for syncpoint: " + syncPoint);
					break;
				}
			}

			// print log message if data was synced
			if (count > 0) {
				messageService.logMessage(
						count + " snapshots synchronized in: " + ((System.currentTimeMillis()) - lProfiler) + " ms");
			} else {
				// just a message on the log
				logger.fine("...sync: '" + keyspaceID + " finished in: " + ((System.currentTimeMillis()) - lProfiler)
						+ " ms");
			}

		} catch (ArchiveException | RuntimeException e) {
			// in case of an exception we did not cancel the Timer service
			if (logger.isLoggable(Level.FINEST)) {
				e.printStackTrace();
			}

			messageService.logMessage("Scheduler failed: " + e.getMessage());

			stop(timer);
		} finally {
			// close session and cluster object
			if (session != null) {
				session.close();
			}
			if (cluster != null) {
				cluster.close();
			}
		}
	}

	/**
	 * This method read sync data. The method returns the first workitem from the
	 * given syncpoint. If no data is available the method returns null.
	 * 
	 * 
	 * @return an XMLDataCollection instance representing the data to sync or null
	 *         if no data form the given syncpoint is available.
	 * @throws ArchiveException
	 * 
	 */
	XMLDataCollection readSyncData(long syncPoint) throws ArchiveException {
		XMLDataCollection result = null;
		// load next document

		RestClient workflowClient = initWorkflowClient();
		String url = SNAPSHOT_SYNCPOINT_RESOURCE + syncPoint;
		logger.finest("...... read data: " + url + "....");

		try {
			result = workflowClient.getXMLDataCollection(url);
		} catch (RestAPIException e) {
			String errorMessage = "...failed to readSyncData : " + e.getMessage();
			messageService.logMessage(errorMessage);
			throw new ArchiveException(ArchiveException.SYNC_ERROR, errorMessage, e);
		}

		if (result != null && result.getDocument().length > 0) {
			return result;
		}
		return null;
	}
	
	
	
	/**
	 * This method read the current snapshot id for a given UnqiueID. 
	 * This information can be used to verify the sync satus of a singel process instance.
	 * 
	 * @return the current snapshotid 
	 * @throws ArchiveException
	 * 
	 */
	public String readSnapshotIDByUniqueID(String uniqueid) throws ArchiveException {
		String result = null;
		// load single document
		RestClient workflowClient = initWorkflowClient();
		String url = DOCUMENTS_RESOURCE + uniqueid + "?items=$snapshotid";
		logger.finest("...... read snapshotid: " + url + "....");

		try {
			XMLDataCollection xmlDocument = workflowClient.getXMLDataCollection(url);
			if (xmlDocument!=null && xmlDocument.getDocument().length>0) {
				ItemCollection document=XMLDocumentAdapter.putDocument(xmlDocument.getDocument()[0]);
				result=document.getItemValueString("$snapshotid");
			}
			
		} catch (RestAPIException e) {
			String errorMessage = "...failed to readSyncData : " + e.getMessage();
			messageService.logMessage(errorMessage);
			throw new ArchiveException(ArchiveException.SYNC_ERROR, errorMessage, e);
		}

	
		return result;
	}
	
	
	
	public void restoreSnapshot(ItemCollection snapshot) throws ArchiveException {
		RestClient restClient = initWorkflowClient();
		String url = SNAPSHOT_RESOURCE;
		logger.finest("...... post data: " + url + "....");
		try {
			  restClient.postDocument(url, snapshot);
		} catch (RestAPIException e) {
			String errorMessage = "...failed to restoreSnapshot: " + e.getMessage();
			messageService.logMessage(errorMessage);
			throw new ArchiveException(ArchiveException.SYNC_ERROR, errorMessage, e);
		}

	}

	/**
	 * Helper method to initalize a Melman Workflow Client based on the current
	 * archive configuration.
	 */
	RestClient initWorkflowClient() {
		String url = ClusterService.getEnv(ClusterService.ENV_WORKFLOW_SERVICE_ENDPOINT, null);
		String autMethod = ClusterService.getEnv(ClusterService.ENV_WORKFLOW_SERVICE_AUTHMETHOD, null);
		String user = ClusterService.getEnv(ClusterService.ENV_WORKFLOW_SERVICE_USER, null);
		String password = ClusterService.getEnv(ClusterService.ENV_WORKFLOW_SERVICE_PASSWORD, null);

		logger.finest("...... WORKFLOW_SERVICE_ENDPOINT = " + url);

		RestClient workflowClient = new RestClient(url);

		// Test authentication method
		if ("Form".equalsIgnoreCase(autMethod)) {
			// default basic authenticator
			FormAuthenticator formAuth = new FormAuthenticator(url, user, password);
			// register the authenticator
			workflowClient.registerRequestFilter(formAuth);

		} else {
			// default basic authenticator
			BasicAuthenticator basicAuth = new BasicAuthenticator(user, password);
			// register the authenticator
			workflowClient.registerRequestFilter(basicAuth);
		}
		return workflowClient;
	}
}
