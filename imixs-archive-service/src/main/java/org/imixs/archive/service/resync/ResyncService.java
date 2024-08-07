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
package org.imixs.archive.service.resync;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.RemoteAPIService;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.archive.service.util.MessageService;
import org.imixs.archive.service.util.RestClientHelper;
import org.imixs.melman.DocumentClient;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.QueryException;
import org.imixs.workflow.xml.XMLDataCollection;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.inject.Inject;

/**
 * The SyncService synchronizes the data of a Imixs-Worklfow instance with the
 * cassandra cluster. The synchonrization can be started by the method sync().
 * The SyncService is implemented as a SingleActionTimer so the sync can run in
 * backgroud.
 * <p>
 * The service automatically starts during deployment. When all the data is
 * synchronized (syncpoint == last snapshot), the service terminates.
 * <p>
 * To access the data form the Imixs-Workflow instance uses RemoteAPIService.
 * 
 * @version 1.0
 * @author rsoika
 */

@Stateless
public class ResyncService {

    public final static String TIMER_ID_SYNCSERVICE = "IMIXS_ARCHIVE_RESYNC_TIMER";

    public final static String ITEM_SYNCPOINT = "sync.point";
    public final static String ITEM_SYNCCOUNT = "sync.count";
    public final static String ITEM_SYNCSIZE = "sync.size";
    public final static String DEFAULT_SCHEDULER_DEFINITION = "hour=*";

    public final static String MESSAGE_TOPIC = "sync";
    private final static int MAX_COUNT = 500;

    @Resource
    jakarta.ejb.TimerService timerService;

    @Inject
    DataService dataService;

    @Inject
    ClusterService clusterService;

    @Inject
    MessageService messageService;

    @Inject
    RemoteAPIService remoteAPIService;

    @Inject
    ResyncStatusHandler syncStatusHandler;

    @Inject
    RestClientHelper restClientHelper;

    private static Logger logger = Logger.getLogger(ResyncService.class.getName());

    /**
     * This method initializes a new timer for the sync ....
     * <p>
     * The method also verifies the existence of the archive keyspace by loading the
     * archive session object.
     * 
     * @throws ArchiveException
     */
    public void start() throws ArchiveException {
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

        if (clusterService.getSession() != null) {
            logger.finest("...starting scheduler sync-service ...");
            TimerConfig timerConfig = new TimerConfig();

            timerConfig.setInfo(TIMER_ID_SYNCSERVICE);
            // New timer will start imediatly
            timer = timerService.createSingleActionTimer(0, timerConfig);
            // start and set statusmessage
            if (timer != null) {
                messageService.logMessage(MESSAGE_TOPIC, "Timer started.");
            }
        } else {
            logger.warning("...Failed to initalize imixs-archive keyspace!");
        }

    }

    /**
     * Stops the current sync
     * 
     * @throws ArchiveException
     */
    public void cancel() throws ArchiveException {
        syncStatusHandler.setStatus(ResyncStatusHandler.STAUS_CANCELED);
        messageService.logMessage(MESSAGE_TOPIC, "... sync canceled!");

        stop(findTimer());
    }

    /**
     * returns true if the service is running
     * 
     * @return
     */
    public boolean isRunning() {
        return (findTimer() != null);
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
            Timer timer = (jakarta.ejb.Timer) obj;
            if (TIMER_ID_SYNCSERVICE.equals(timer.getInfo())) {
                return timer;
            }
        }
        return null;
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
    void onTimeout(jakarta.ejb.Timer timer) throws Exception {
        long syncPoint = 0;
        int syncUpdates = 0;
        int syncBlockRead = 0;
        int syncTotalRead = 0;
        long totalCount = 0;
        long totalSize = 0;
        ItemCollection metaData = null;
        String lastUniqueID = null;

        // start time....
        long lProfiler = System.currentTimeMillis();

        try {
            // init rest clients....
            DocumentClient documentClient = restClientHelper.createDocumentClient();
            // load metadata and get last syncpoint
            metaData = dataService.loadMetadata();
            syncPoint = metaData.getItemValueLong(ITEM_SYNCPOINT);
            totalCount = metaData.getItemValueLong(ITEM_SYNCCOUNT);
            totalSize = metaData.getItemValueLong(ITEM_SYNCSIZE);

            // ...start sync
            logger.info("...start synchronizing at syncPoint " + new Date(syncPoint) + "...");

            // Daylight Saving Time Correction
            // issue #53
            Date now = new Date();
            if (syncPoint > now.getTime()) {
                logger.warning("...current syncpoint (" + syncPoint + ") is in the future! Adjust Syncpoint to now ("
                        + now.getTime() + ")....");
                syncPoint = now.getTime();
            }

            while (true) {
                long lReadTime = System.currentTimeMillis();
                long lTotalTime = System.currentTimeMillis();
                XMLDataCollection xmlDataCollection = remoteAPIService.readSyncData(syncPoint, documentClient);
                if (xmlDataCollection != null) {
                    logger.info("...found " + xmlDataCollection.getDocument().length + " snapshots at syncpoint "
                            + new Date(syncPoint) + " in " + (System.currentTimeMillis() - lReadTime) + "ms");
                    List<XMLDocument> snapshotList = Arrays.asList(xmlDataCollection.getDocument());
                    for (XMLDocument xmlDocument : snapshotList) {
                        long lSyncTime = System.currentTimeMillis();
                        ItemCollection snapshot = XMLDocumentAdapter.putDocument(xmlDocument);

                        // update snypoint
                        Date syncpointdate = snapshot.getItemValueDate("$modified");
                        syncPoint = syncpointdate.getTime();
                        logger.fine("......data found - new syncpoint=" + syncPoint);
                        // verify if this snapshot is already stored - if so, we do not overwrite
                        // the origin data
                        if (!dataService.existSnapshot(snapshot.getUniqueID())) {
                            // store data into archive
                            try {
                                lastUniqueID = snapshot.getUniqueID();
                                dataService.saveSnapshot(snapshot);
                                syncUpdates++;
                                totalCount++;
                                totalSize = totalSize + dataService.calculateSize(xmlDocument);
                            } catch (RuntimeException e) {
                                logger.warning("Failed to resync snapshot id '" + snapshot.getUniqueID() + "' - error: "
                                        + e.getMessage());
                                // we continue....
                            }
                            logger.info(
                                    "...snapshot '" + snapshot.getUniqueID() + "' written in  "
                                            + (System.currentTimeMillis() - lSyncTime) + "ms");
                        } else {
                            // This is because in case of a restore, the same snapshot takes a new $modified
                            // item. And we do not want to re-import the snapshot in the next sync cycle.
                            // see issue #40
                            logger.info(
                                    "...snapshot '" + snapshot.getUniqueID() + "' already exits - verification took "
                                            + (System.currentTimeMillis() - lSyncTime) + "ms");
                        }
                        syncBlockRead++;
                        syncTotalRead++;

                        // update metadata
                        metaData.setItemValue(ITEM_SYNCPOINT, syncPoint);
                        metaData.setItemValue(ITEM_SYNCCOUNT, totalCount);
                        metaData.setItemValue(ITEM_SYNCSIZE, totalSize);
                        lastUniqueID = "0";
                        dataService.saveMetadata(metaData);
                        logger.info(
                                "...snapshot '" + snapshot.getUniqueID() + "' synchronized in "
                                        + (System.currentTimeMillis() - lTotalTime) + "ms");

                        if (syncStatusHandler.getStatus() == ResyncStatusHandler.STAUS_CANCELED) {
                            break;
                        }
                    }

                    // print log message if data was synced
                    if (syncBlockRead >= MAX_COUNT) {
                        messageService.logMessage(MESSAGE_TOPIC,
                                "... " + syncTotalRead + " snapshots verified (" + syncUpdates + " updates) in: "
                                        + formatDuration((System.currentTimeMillis()) - lProfiler)
                                        + " , next syncpoint "
                                        + new Date(syncPoint));
                        // reset count
                        syncBlockRead = 0;
                    }

                    if (syncStatusHandler.getStatus() == ResyncStatusHandler.STAUS_CANCELED) {
                        break;
                    }

                } else {
                    // no more syncpoints
                    logger.finest("......no more data found for syncpoint: " + syncPoint);
                    break;
                }
            }

            messageService.logMessage(MESSAGE_TOPIC,
                    "...no more data found at syncpoint " + new Date(syncPoint) + " -> finishing synchroization.");
            stop(timer);

        } catch (ArchiveException | RuntimeException e) {
            // print the stack trace
            e.printStackTrace();
            messageService.logMessage(MESSAGE_TOPIC, "sync failed "
                    + ("0".equals(lastUniqueID) ? " (failed to save metadata)" : "(last uniqueid=" + lastUniqueID + ")")
                    + " : " + e.getMessage());

            stop(timer);
        }
    }

    private static String formatDuration(long durationInMillis) {
        long durationInSeconds = durationInMillis / 1000;
        long durationInMinutes = durationInSeconds / 60;
        long durationInHours = durationInMinutes / 60;

        String formattedDuration;

        if (durationInHours > 0) {
            formattedDuration = String.format("%d hours, %d minutes und %d seconds",
                    durationInHours,
                    durationInMinutes % 60,
                    durationInSeconds % 60);
        } else if (durationInMinutes > 0) {
            formattedDuration = String.format("%d minutes and %d seconds",
                    durationInMinutes,
                    durationInSeconds % 60);
        } else {
            formattedDuration = String.format("%d seconds", durationInSeconds);
        }

        return formattedDuration;
    }
}
