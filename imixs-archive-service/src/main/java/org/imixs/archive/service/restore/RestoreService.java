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
package org.imixs.archive.service.restore;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.RemoteAPIService;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.archive.service.resync.ResyncService;
import org.imixs.archive.service.util.MessageService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.QueryException;
import org.imixs.workflow.xml.XMLDocumentAdapter;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.inject.Inject;

/**
 * The RestoreService restores the workflow data stored in the cassandra cluster
 * into a Imixs-Workflow instance. The service class runs in the background as a
 * TimerService.
 * <p>
 * The scheduler configuration is stored in the Metadata object of the cassandra
 * keyspace. The following attributes are defining the restore procedure:
 * <p>
 * <strong>restore.from</strong>: the earliest snapshot syncpoint to be restored
 * (can be 0)
 * <p>
 * <strong>restore.to</strong>: the latest snapshot syncpoint to be restored
 * <p>
 * <strong>restore.point</strong>: the current snapshot syncpoint. This date is
 * used to select snapshots by date in a cassandra partion.
 * <p>
 * <strong>restore.count</strong>: count of restored snapshots
 * <p>
 * <strong>restore.size</strong>: bytes of restored snapshot data
 * <p>
 * The timer is stopped after all snapshots in the restore time range
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
	public final static String ITEM_RESTORE_SYNCPOINT = "restore.point";
	public final static String ITEM_RESTORE_SYNCCOUNT = "restore.count";
	public final static String ITEM_RESTORE_SYNCERRORS = "restore.errors";
	public final static String ITEM_RESTORE_SYNCSIZE = "restore.size";
	public final static String ITEM_RESTORE_OPTIONS = "restore.options";

	public final static String MESSAGE_TOPIC = "restore";

	private static Logger logger = Logger.getLogger(RestoreService.class.getName());

	@Inject
	DataService dataService;

	@Inject
	ClusterService clusterService;

	@Inject
	MessageService messageService;

	@Inject
	RemoteAPIService remoteAPIService;

	@Resource
	jakarta.ejb.TimerService timerService;

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
	 * @param datFrom - syncpoint from
	 * @param datTo   - syncpoint to
	 * @param options - optional list of item map.
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
				messageService.logMessage(MESSAGE_TOPIC, "Failed to stop existing timer - " + e.getMessage());
				throw new ArchiveException(ResyncService.class.getName(), ArchiveException.INVALID_WORKITEM,
						" failed to cancle existing timer!");
			}
		}

		logger.info("...starting scheduler restore-service...");

		// store information into metdata object
		Session session = null;
		Cluster cluster = null;
		try {
			logger.info("...... restore from=" + dataService.getSyncPointISO(restoreFrom));
			logger.info("...... restore   to=" + dataService.getSyncPointISO(restoreTo));
			// ...start sync
			ItemCollection metaData = dataService.loadMetadata();
			metaData.setItemValue(ITEM_RESTORE_FROM, restoreFrom);
			metaData.setItemValue(ITEM_RESTORE_TO, restoreTo);
			metaData.setItemValue(ITEM_RESTORE_SYNCPOINT, restoreFrom);
			metaData.setItemValue(ITEM_RESTORE_SYNCCOUNT, 0);
			metaData.setItemValue(ITEM_RESTORE_SYNCSIZE, 0);
			metaData.setItemValue(ITEM_RESTORE_OPTIONS, options);

			// update metadata
			dataService.saveMetadata(metaData);
			timer = timerService.createTimer(new Date(), TIMER_INTERVAL_DURATION, TIMER_ID_RESTORESERVICE);

			// start and set statusmessage
			if (timer != null) {
				messageService.logMessage(MESSAGE_TOPIC, "Timer started.");
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
	 * Stops the current restore process
	 * 
	 * @throws ArchiveException
	 */
	public void cancel() throws ArchiveException {
		messageService.logMessage(MESSAGE_TOPIC, "... restore process canceled!");

		stop(findTimer());
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
	void onTimeout(jakarta.ejb.Timer timer) throws Exception {
		Session session = null;
		Cluster cluster = null;
		ItemCollection metadata = null;
		// Calendar calendarSyncPoint;
		long syncpoint;
		int restoreCount = 0;
		int restoreErrors = 0;
		long restoreSize = 0;
		long startTime = System.currentTimeMillis();

		try {
			// read the metdata
			metadata = dataService.loadMetadata();
			// read last restor stat....
			restoreCount = metadata.getItemValueInteger(ITEM_RESTORE_SYNCCOUNT);
			restoreSize = metadata.getItemValueInteger(ITEM_RESTORE_SYNCSIZE);
			restoreErrors = metadata.getItemValueInteger(ITEM_RESTORE_SYNCERRORS);

			// compute current restore day....
			syncpoint = metadata.getItemValueLong(ITEM_RESTORE_SYNCPOINT);
			List<ItemCollection> options = getOptions(metadata);
			LocalDateTime localDateSyncPoint = new Date(syncpoint).toInstant().atZone(ZoneId.systemDefault())
					.toLocalDateTime();
			// compute to date....
			long restoreFrom = metadata.getItemValueLong(ITEM_RESTORE_FROM);
			long restoreTo = metadata.getItemValueLong(ITEM_RESTORE_TO);
			LocalDateTime localDateRestoreTo = new Date(restoreTo).toInstant().atZone(ZoneId.systemDefault())
					.toLocalDateTime();
			logger.info("......restore:    from " + dataService.getSyncPointISO(restoreFrom) + " to "
					+ dataService.getSyncPointISO(restoreTo));
			logger.info("......restore.point:  " + dataService.getSyncPointISO(syncpoint));
			// we search for snapshotIDs until we found one or the syncdate is after the
			// restore.to point.
			while (localDateRestoreTo.isAfter(localDateSyncPoint)) {

				List<String> snapshotIDs = dataService.loadSnapshotsByDate(localDateSyncPoint.toLocalDate());
				// verify all snapshots of this day....
				if (!snapshotIDs.isEmpty()) {
					logger.info("......restore snapshot date " + localDateSyncPoint);
					// print out the snapshotIDs we found
					for (String snapshotID : snapshotIDs) {

						String latestSnapshot = findLatestSnapshotID(snapshotID, restoreFrom, restoreTo);
						// did we found a snapshot to restore?
						ItemCollection snapshot;
						String remoteSnapshotID = null;
						if (latestSnapshot != null) {
							// yes, lets see if this snapshot is already restored or synced?
							try {
								remoteSnapshotID = remoteAPIService
										.readSnapshotIDByUniqueID(dataService.getUniqueID(latestSnapshot));
							} catch (ArchiveException ae) {
								// expected if not found
							}
							if (remoteSnapshotID != null && latestSnapshot.equals(remoteSnapshotID)) {
								logger.finest(
										"......no need to restore - snapshot:" + latestSnapshot + " is up to date!");
							} else {
								// test the filter options
								if (matchFilterOptions(latestSnapshot, options)) {
									long _tmpSize = -1;
									try {
										logger.finest("......restoring: " + latestSnapshot);
										snapshot = dataService.loadSnapshot(latestSnapshot);
										_tmpSize = dataService.calculateSize(XMLDocumentAdapter.getDocument(snapshot));
										logger.finest("......size=: " + _tmpSize);

										remoteAPIService.restoreSnapshot(snapshot);

										restoreSize = restoreSize + _tmpSize;
										restoreCount++;
										snapshot = null;
									} catch (Exception e) {
										logger.severe("...Failed to restore '" + latestSnapshot + "' ("
												+ messageService.userFriendlyBytes(_tmpSize) + ") - " + e.getMessage());
										restoreErrors++;
									}
								}
							}
						} else {
							logger.warning(
									".... unexpected data situation:  no latest snapshot found, matching requested restore time range!");
						}
					}
				}

				// adjust snyncdate for one day....
				localDateSyncPoint = localDateSyncPoint.plusDays(1);
				// update metadata...
				Date date = Date.from(localDateSyncPoint.atZone(ZoneId.systemDefault()).toInstant());
				metadata.setItemValue(ITEM_RESTORE_SYNCPOINT, date.getTime());
				metadata.setItemValue(ITEM_RESTORE_SYNCCOUNT, restoreCount);
				metadata.setItemValue(ITEM_RESTORE_SYNCSIZE, restoreSize);
				metadata.setItemValue(ITEM_RESTORE_SYNCERRORS, restoreErrors);

				dataService.saveMetadata(metadata);
			}

			logger.info("...restore finished in: " + (System.currentTimeMillis() - startTime) + "ms");
			logger.info(".......final syncpoint: " + localDateSyncPoint);
			logger.info(".......total count:" + restoreCount);
			logger.info(".......total size:" + messageService.userFriendlyBytes(restoreSize));
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
	 * @param snapshotID  - a snapshotID to be analyzed
	 * @param restoreFrom - time range from
	 * @param restoreTo   - time range to
	 * @param session     - cassandra session
	 * @return latest snapshot ID within the time range or null if no snapshot
	 *         matches the timerange.
	 */
	String findLatestSnapshotID(String snapshotID, long restoreFrom, long restoreTo) {
		String latestSnapshot = null;
		String uniqueID = dataService.getUniqueID(snapshotID);

		String sql = DataService.STATEMENT_SELECT_SNAPSHOTS_BY_UNIQUEID;

		// set uniqueid
		sql = sql.replace("'?'", "'" + uniqueID + "'");

		// now add the date range...
		sql = sql + " AND snapshot>='" + uniqueID + "-" + restoreFrom + "'";
		sql = sql + " AND snapshot<='" + uniqueID + "-" + restoreTo + "'";

		// set reverse order
		sql = sql + " ORDER BY snapshot DESC";

		// set LIMIT = 1
		sql = sql + " LIMIT 1";

		logger.finest("......query latest snapshot by date: " + sql);
		ResultSet rs = clusterService.getSession().execute(sql);

		// take the first one...

		Iterator<Row> resultIter = rs.iterator();
		if (resultIter.hasNext()) {
			Row row = resultIter.next();
			latestSnapshot = row.getString(1);
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
	 * @param snapshotID - a snapshot id
	 * @param options    - a list of options
	 * @param session    - cassandra session
	 * @return true if no options are defined, or all options match the snapshot
	 *         data
	 * @throws ArchiveException
	 */
	boolean matchFilterOptions(String snapshotID, List<ItemCollection> options) throws ArchiveException {
		ItemCollection _tmp_snapshot_data = null;

		// do we have options to filter the snapshot?
		if (options == null || options.size() == 0) {
			// no options - match = true!
			return true;
		}

		// load snapshot data without document data and verify the options
		_tmp_snapshot_data = dataService.loadSnapshot(snapshotID, false);
		for (ItemCollection option : options) {
			String itemName = option.getItemValueString("name");
			Pattern regexPattern = Pattern.compile(option.getItemValueString("filter").trim());

			// did the filter match al values?
			List<String> valueList = _tmp_snapshot_data.getItemValueList(itemName, String.class);
			for (String value : valueList) {
				if (!regexPattern.matcher(value).find()) {
					logger.fine(" snapshot value '" + value + "' did not match filter option '" + regexPattern + "'");
					_tmp_snapshot_data = null;
					return false;
				}
			}
		}
		// all filter did match!
		_tmp_snapshot_data = null;
		return true;
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
			Timer timer = (jakarta.ejb.Timer) obj;
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
	 * @param options  - list of options
	 * @param metaData - metaData object
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
