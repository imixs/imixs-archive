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

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.MessageService;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.QueryException;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

/**
 * The RestoreService restores the workflow data stored in the cassandra cluster
 * into the workflow system. The service class runns in the background as a
 * TimerService.
 * <p>
 * The scheduler configuration is stored in the Metadata object of the cassandra
 * keyspace. The following attributes are defining the restore procedure:
 * <p>
 * <strong>restore.from</strong>: the eariest snapshot syncpoint to be restored
 * (can be 0)
 * <p>
 * <strong>restore.to</strong>: the latest snapshot syncpoint to be restored
 * <p>
 * <strong>restore.$sync_point</strong>: the current snapshot syncpoint. This
 * date is used to select snapshots by date in a cassandra partion.
 * <p>
 * <strong>restore.$sync_count</strong>: count of restored snapshots
 * <p>
 * <strong>restore.$sync_size</strong>: bytes of restored snapshot data
 * <p>
 * The timer is stoped after all snapshots in the restore ragnge (restore.from -
 * restore.to) are restored.
 * 
 * 
 * @version 1.0
 * @author rsoika
 */

@Stateless
public class RestoreService {

	public final static String TIMER_ID_RESTORESERVICE = "IMIXS_ARCHIVE_RESTORE_TIMER";
	public final static long TIMER_INTERVAL_DURATION = 60000;

	public final static String ITEM_RESTORE_FROM = "restore.from";
	public final static String ITEM_RESTORE_TO = "restore.to";
	public final static String ITEM_RESTORE_SYNCPOINT = "restore.$sync_point";
	public final static String ITEM_RESTORE_SYNCCOUNT = "restore.$sync_count";
	public final static String ITEM_RESTORE_SYNCSIZE = "restore.$sync_size";

	private static Logger logger = Logger.getLogger(RestoreService.class.getName());

	
	@EJB
	DataService dataService;

	@EJB
	ClusterService clusterService;

	@EJB
	MessageService messageService;

	@Resource
	javax.ejb.TimerService timerService;

	/**
	 * Starts a new restore process with a EJB TimerService
	 * <p>
	 * The Timer will be started imediatly with a intervall duration defined by the
	 * constante TIMER_INTERVAL_DURATION
	 * <p>
	 * The meta data for the restore process is stored in the metadata object.
	 * <p>
	 * The restore process selects snapshot data by date (SNAPSHOTS_BY_MODIFIED).
	 * The current date is stored in the meta data. The meta data is updated after
	 * each iteration.
	 * 
	 * @param datFrom
	 *            - syncpoint from
	 * @param datTo
	 *            - syncpoint to
	 * @throws ArchiveException
	 */
	public void start(Date datFrom, Date datTo) throws ArchiveException {
		Timer timer = null;

		// try to cancel an existing timer for this workflowinstance
		timer = findTimer();
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

		logger.info("...starting scheduler restore-service...");
		
		if (datTo==null) {
			// set now
			datTo=new Date();
		}
		
		// if datFrom is empty set 1.1.1970
		if (datFrom==null) {
			// set 1.1.1970
			datFrom=new Date(0);
		}
		

		// store information into metdata object
		Session session = null;
		Cluster cluster = null;
		try {
			// ...start sync
			cluster = clusterService.getCluster();
			session = clusterService.getArchiveSession(cluster);
			ItemCollection metaData = dataService.loadMetadata(session);

			metaData.setItemValue(ITEM_RESTORE_FROM, datFrom.getTime());
			metaData.setItemValue(ITEM_RESTORE_TO, datTo.getTime());

			metaData.setItemValue(ITEM_RESTORE_SYNCPOINT, datFrom.getTime());
			metaData.setItemValue(ITEM_RESTORE_SYNCCOUNT, 0);
			metaData.setItemValue(ITEM_RESTORE_SYNCSIZE, 0);

			// update metadata
			dataService.saveMetadata(metaData, session);

			timer = timerService.createTimer(new Date(), TIMER_INTERVAL_DURATION, TIMER_ID_RESTORESERVICE);

			// start and set statusmessage
			if (timer != null) {
				messageService.logMessage("Timer started.");
			}
		} catch (Exception e) {
			logger.warning("...Failed to update metadata: " + e.getMessage());

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
	 * This is the method which processes the timeout event depending on the running
	 * timer settings.
	 * <p>
	 * The method reads MAX_COUNT snapshot workitems to be restored into a imixs
	 * workflow instance.
	 * 
	 * 
	 * @param timer
	 * @throws Exception
	 * @throws QueryException
	 */
	@Timeout
	void onTimeout(javax.ejb.Timer timer) throws Exception {
		Session session = null;
		Cluster cluster = null;
		ItemCollection metadata = null;
		Calendar calSyncDate;
		Calendar calSyncDateTo;
		long syncpoint;
		try {
			// ...start sync
			cluster = clusterService.getCluster();
			session = clusterService.getArchiveSession(cluster);
			// read the metdata
			metadata = dataService.loadMetadata(session);
			// compute current restore day....
			syncpoint = metadata.getItemValueLong(ITEM_RESTORE_SYNCPOINT);
			calSyncDate = Calendar.getInstance();
			calSyncDate.setTime(new Date(syncpoint));

			// compute to date....
			long syncpointTo = metadata.getItemValueLong(ITEM_RESTORE_TO);
			calSyncDateTo = Calendar.getInstance();
			calSyncDateTo.setTime(new Date(syncpointTo));
			logger.info("......starting restore from " + calSyncDate.getTime() + " to " + calSyncDateTo.getTime());

			// we search for snapshotIDs until we found one or the syncdate is after the
			// snypoint.to
			while (true) {

				List<String> snapshotIDs = dataService.loadSnapshotsByDate(calSyncDate.getTime(), session);

				if (snapshotIDs.isEmpty()) {
					// adjust snyncdate for one day....
					calSyncDate.add(Calendar.DAY_OF_MONTH, 1);
					// test if we still behind the sync.to date...
					if (calSyncDate.after(calSyncDateTo)) {
						logger.info("...final syncdate " + calSyncDateTo.getTime() + " found!");
						break;
					}
				} else {
					logger.info("......restore snapshot data from " + calSyncDate.getTime());
					// print out the snapshotIDs we found
					for (String snapshotID : snapshotIDs) {

						logger.info("...now we need to verify the snapshot: " + snapshotID);
					}
					
					// cancel for testing.....
					break;
				}
			}

			logger.info("...restore finished at Syncpoint: "+calSyncDate.getTime());

			timer.cancel();

		} catch (Exception e) {
			logger.severe("Failed to restore data: " + e.getMessage());
			// cancle timer
			timer.cancel();
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
	 * This method returns a timer for a corresponding id if such a timer object
	 * exists.
	 * 
	 * @param id
	 * @return Timer
	 * @throws Exception
	 */
	public Timer findTimer() {
		for (Object obj : timerService.getTimers()) {
			Timer timer = (javax.ejb.Timer) obj;
			if (TIMER_ID_RESTORESERVICE.equals(timer.getInfo())) {
				return timer;
			}
		}
		return null;
	}

}
