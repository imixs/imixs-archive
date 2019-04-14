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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import org.imixs.workflow.xml.XMLDocumentAdapter;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

/**
 * The RestoreService restores the workflow data stored in the cassandra cluster
 * into the workflow system. The service class runs in the background as a
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
 * The timer is stoped after all snapshots in the restore timerange
 * (restore.from - restore.to) are restored.
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
	public final static String ITEM_RESTORE_SYNCERRORS = "restore.$sync_errors";
	public final static String ITEM_RESTORE_SYNCSIZE = "restore.$sync_size";
	public final static String ITEM_RESTORE_OPTIONS = "restore.options";

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
	 * @param options
	 *            - optional list of item map.
	 * @throws ArchiveException
	 */
	@SuppressWarnings("rawtypes")
	public void start(long restoreFrom, long restoreTo, List<Map> options) throws ArchiveException {
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

		// store information into metdata object
		Session session = null;
		Cluster cluster = null;
		try {
			// ...start sync
			cluster = clusterService.getCluster();
			session = clusterService.getArchiveSession(cluster);
			ItemCollection metaData = dataService.loadMetadata(session);
			metaData.setItemValue(ITEM_RESTORE_FROM, restoreFrom);
			metaData.setItemValue(ITEM_RESTORE_TO, restoreTo);
			metaData.setItemValue(ITEM_RESTORE_SYNCPOINT, restoreFrom);
			metaData.setItemValue(ITEM_RESTORE_SYNCCOUNT, 0);
			metaData.setItemValue(ITEM_RESTORE_SYNCSIZE, 0);
			metaData.setItemValue(ITEM_RESTORE_OPTIONS, options);

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
	 * This method processes the timeout event. The method reads all snapshotIDs per
	 * day between the restore time range restore.from - restore.to.
	 * <p>
	 * If no snapshotIDs were found for a day, the syncpoint will be adjusted for
	 * one day.
	 * <p>
	 * If snapshotIDs for a day exists, than the method tests if a snapshot is the
	 * latest one for the requested restore timerange. If so, than the snapshot will
	 * be resotored.
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
		Calendar calendarSyncPoint;
		long syncpoint;
		int restoreCount = 0;
		int restoreErrors=0;
		long restoreSize = 0;
		long startTime = System.currentTimeMillis();
		try {
			// ...start sync
			cluster = clusterService.getCluster();
			session = clusterService.getArchiveSession(cluster);
			// read the metdata
			metadata = dataService.loadMetadata(session);
			// compute current restore day....
			syncpoint = metadata.getItemValueLong(ITEM_RESTORE_SYNCPOINT);
			List<ItemCollection> options = getOptions(metadata);
			calendarSyncPoint = Calendar.getInstance();
			calendarSyncPoint.setTime(new Date(syncpoint));
			// reset hour, minute, second, ms
			calendarSyncPoint.set(Calendar.HOUR, 0);
			calendarSyncPoint.set(Calendar.MINUTE, 0);
			calendarSyncPoint.set(Calendar.SECOND, 0);
			calendarSyncPoint.set(Calendar.MILLISECOND, 0);
			// adjust one day - 1ms - so we have 1970-01-01 59:59:59:999
			calendarSyncPoint.add(Calendar.DAY_OF_MONTH, 1);
			calendarSyncPoint.add(Calendar.MILLISECOND, -1);

			// compute to date....
			long restoreFrom = metadata.getItemValueLong(ITEM_RESTORE_FROM);
			long restoreTo = metadata.getItemValueLong(ITEM_RESTORE_TO);

			logger.info("......starting restore:    from " + new Date(restoreFrom) + " to " + new Date(restoreTo));
			logger.info("......starting syncpoint:  " + calendarSyncPoint.getTime());

			// we search for snapshotIDs until we found one or the syncdate is after the
			// restore.to point.
			while (calendarSyncPoint.getTimeInMillis() < restoreTo) {

				List<String> snapshotIDs = dataService.loadSnapshotsByDate(calendarSyncPoint.getTime(), session);
				// verify all snapshots of this day....
				if (!snapshotIDs.isEmpty()) {
					logger.info("......analyze snapshotIDs from " + calendarSyncPoint.getTime());
					// print out the snapshotIDs we found
					for (String snapshotID : snapshotIDs) {

						String latestSnapshot = findLatestSnapshotID(snapshotID, restoreFrom, restoreTo, session);

						// did we found a snapshot to restore?
						ItemCollection snapshot;
						if (latestSnapshot != null) {
							// yes!
							// lets see if this snapshot is alredy restored or synced?
							String remoteSnapshotID = RemoteAPIService
									.readSnapshotIDByUniqueID(DataService.getUniqueID(latestSnapshot));
							if (latestSnapshot.equals(remoteSnapshotID)) {
								logger.info(" no need to restore - snapshot:" + latestSnapshot + " is up to date!");
							} else {
								// test the filter options
								if (matchFilterOptions(latestSnapshot, options, session)) {
									logger.info("......restoring: " + latestSnapshot);
									snapshot = dataService.loadSnapshot(latestSnapshot, session);
									RemoteAPIService.restoreSnapshot(snapshot);
									restoreSize = restoreSize
											+ DataService.calculateSize(XMLDocumentAdapter.getDocument(snapshot));
									restoreCount++;
								}
							}
						} else {
							logger.info(".... Stange  we found no latest snapthost matching our restore time range!");
						}

					}
				}

				// adjust snyncdate for one day....
				calendarSyncPoint.add(Calendar.DAY_OF_MONTH, 1);
				// update metadata...
				metadata.setItemValue(ITEM_RESTORE_SYNCPOINT, calendarSyncPoint.getTimeInMillis());
				metadata.setItemValue(ITEM_RESTORE_SYNCCOUNT, restoreCount);
				metadata.setItemValue(ITEM_RESTORE_SYNCSIZE, restoreSize);
				metadata.setItemValue(ITEM_RESTORE_SYNCERRORS, restoreErrors);
				
				
				dataService.saveMetadata(metadata, session);

			}

			logger.info("...restore finished in: " + (System.currentTimeMillis() - startTime) + "ms");
			logger.info(".......final syncpoint: " + calendarSyncPoint.getTime());
			logger.info(".......total count:" + restoreCount);
			logger.info(".......total size:" + MessageService.userFriendlyBytes(restoreSize));
			logger.info(".......total errors:" + restoreErrors);

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
	 * The method finds for a given SnapshotID the corresponding latest snapshotID
	 * within a time range. Therefor the method loads the complete list of
	 * snapshotIDs and compares each id with the given time range. The latest one
	 * from the list will be returned.
	 * 
	 * @param snapshotID
	 *            - a snapshotID to be analyzed
	 * @param restoreFrom
	 *            - time range from
	 * @param restoreTo
	 *            - time range to
	 * @param session
	 *            - cassandra session
	 * @return latest snapshot ID within the time range or null if no snapshot
	 *         matches the timerange.
	 */
	String findLatestSnapshotID(String snapshotID, long restoreFrom, long restoreTo, Session session) {
		String latestSnapshot = null;
		List<String> _tmpSnapshots = dataService.loadSnapshotsByUnqiueID(DataService.getUniqueID(snapshotID), session);

		// --- special debug logging....
		for (String _tmpSnapshotID : _tmpSnapshots) {
			logger.info(".......           :" + _tmpSnapshotID);
		}

		// find the latest snapshot within the restore time range.....

		for (String _tmpSnapshotID : _tmpSnapshots) {
			long _tmpSnapshotTime = DataService.getSnapshotTime(_tmpSnapshotID);
			// check restore time range
			if (_tmpSnapshotTime >= restoreFrom && _tmpSnapshotTime <= restoreTo) {

				if (_tmpSnapshotTime > DataService.getSnapshotTime(latestSnapshot)) {
					latestSnapshot = _tmpSnapshotID;
				}
			} else {
				// this snapshot is out of scope because it is not in range
				logger.info(".... skip snapshot (out of range): " + _tmpSnapshotID);
			}

		}
		return latestSnapshot;
	}

	/**
	 * This method tests if a set of given filter optiosn matches the item data of a
	 * snapshot. Therfore in case that options are defined the method loads a
	 * temporary snapshot data with out the documents and compares all filter
	 * options. If no options are defined, or all options match the temporary
	 * snapshot data , the method returns true.
	 * 
	 * @param snapshotID
	 *            - a snapshot id
	 * @param options
	 *            - a list of options
	 * @param session
	 *            - cassandra session
	 * @return true if no options are defined, or all options match the snapshot
	 *         data
	 * @throws ArchiveException
	 */
	boolean matchFilterOptions(String snapshotID, List<ItemCollection> options, Session session)
			throws ArchiveException {
		ItemCollection _tmp_snapshot_data = null;

		// do we have options to filter the snapshot?
		if (options == null || options.size() == 0) {
			// no options - match = true!
			return true;
		}

		// load snapshot data without document data and verify the options
		_tmp_snapshot_data = dataService.loadSnapshot(snapshotID, false, session);

		for (ItemCollection option : options) {
			String itemName = option.getItemValueString("name");
			String regex = option.getItemValueString("filter");

			// did the filter match al values?
			List<String> valueList = _tmp_snapshot_data.getItemValueList(itemName, String.class);
			for (String value : valueList) {
				if (!value.matches(regex)) {
					logger.info(" snapshot value '" + value + "' did not match filter option '" + regex + "'");
					return false;
				}
			}
		}
		// all filter did match!
		return true;
	}

	/**
	 * This method loads all snapshots for a given uniqueid.
	 * 
	 * If the latest snapshot in the list if before the given CalSyncDay and after
	 * the given Restore.From timepoint, the snapshotid will be returned.
	 * 
	 * @param uniqueID
	 *            - a given uniqueid to be analyzed
	 * @param calRestoreFrom
	 *            - the erliest point of time a restore is requested
	 * @param calSyncDay
	 *            - the current restore sync point.
	 * @param session
	 *            - archive session
	 * @return
	 */
	@Deprecated
	public String findRestoreSnapshot(String uniqueID, long restoreFrom, long restoreTo, long restoreSyncPoint,
			Session session) {
		logger.info("...verify uniqueID:" + uniqueID);
		// verify all snapshots for this uniqueid...
		List<String> _tmpSnapshots = dataService.loadSnapshotsByUnqiueID(uniqueID, session);
		// sort result in reverse order....
		_tmpSnapshots.sort(Comparator.reverseOrder()); // .naturalOrder());

		// special logging....
		for (String _tmpSnapshotID : _tmpSnapshots) {
			logger.info(".......           :" + _tmpSnapshotID);
		}

		// iterate over all snapshots found for the given UnqiueID....
		for (String _tmpSnapshotID : _tmpSnapshots) {
			long snapshotTime = DataService.getSnapshotTime(_tmpSnapshotID);
			// if the snapshotTime is in range.....
			if (snapshotTime >= restoreFrom && snapshotTime <= restoreSyncPoint) {
				// ... but after the current restoreSyncPoint....
				if (snapshotTime > restoreSyncPoint) {
					// ... than we skip this snapshot (it will be restored later...)
					logger.info("---skipp: " + _tmpSnapshotID);
					break;
				} else {
					// this is the one we need testore!
					logger.info(" => restore : " + _tmpSnapshotID + " from: " + new Date(snapshotTime));
					return _tmpSnapshotID;
				}
			}
		}

		// no match
		return null;
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

	/**
	 * returns the optional embedded Map List of the options, stored in the metadata
	 * object.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public List<ItemCollection> getOptions(ItemCollection metaData) {
		// convert current list of options into a list of ItemCollection elements
		ArrayList<ItemCollection> result = new ArrayList<ItemCollection>();
		List<Object> mapOrderItems = metaData.getItemValue(RestoreService.ITEM_RESTORE_OPTIONS);
		for (Object mapOderItem : mapOrderItems) {
			if (mapOderItem instanceof Map) {
				ItemCollection itemCol = new ItemCollection((Map) mapOderItem);
				result.add(itemCol);
			}
		}
		return result;
	}

	/**
	 * Convert a List of ItemCollections back into a List of Map elements
	 * 
	 * @param options
	 *            - list of options
	 * @param metaData
	 *            - metaData object
	 */
	@SuppressWarnings({ "rawtypes" })
	public void setOptions(List<ItemCollection> options, ItemCollection metaData) {
		List<Map> mapOrderItems = new ArrayList<Map>();
		// convert the option ItemCollection elements into a List of Map
		if (options != null) {
			logger.fine("Convert option items into Map...");
			// iterate over all items..
			for (ItemCollection orderItem : options) {
				mapOrderItems.add(orderItem.getAllItems());
			}
			metaData.replaceItemValue(RestoreService.ITEM_RESTORE_OPTIONS, mapOrderItems);
		}
	}

}
