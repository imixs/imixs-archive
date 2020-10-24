package org.imixs.archive.service.ui;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.archive.service.resync.ResyncService;
import org.imixs.archive.service.util.MessageService;
import org.imixs.workflow.ItemCollection;

/**
 * CID Bean for the resync service.
 * <p>
 * The new syncpoint is managed as a string in the format 2019-12-31T06:00
 * 
 * 
 * 
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class ResyncController implements Serializable {

    public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    private static final long serialVersionUID = 1L;
    private static Logger logger = Logger.getLogger(ResyncController.class.getName());

    ItemCollection metaData = null;
    String newSyncPoint = null;

    @Inject
    ClusterService clusterService;

    @Inject
    ResyncService resyncService;

    @Inject
    DataService dataService;

    @Inject
    MessageService messageService;

    public ResyncController() {
        super();
    }

    /**
     * This method initializes the default sync date
     * 
     * @throws ArchiveException
     */
    @PostConstruct
    void init() {
        try {
            // load metadata
            metaData = dataService.loadMetadata();
        } catch (ArchiveException e) {
            logger.severe("Failed to load meta data!");
            e.printStackTrace();
        }
    }

    /**
     * Returns the newSyncPoint and computes the default value
     * 
     * @return
     */
    public String getNewSyncPoint() {
        if (newSyncPoint == null) {
            // compute default
            SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
            newSyncPoint = dt.format(getSyncPoint());

        }
        return newSyncPoint;
    }

    public void setNewSyncPoint(String newSyncPoint) {
        this.newSyncPoint = newSyncPoint;
    }

    /**
     * returns the syncpoint of the current configuration
     * 
     * @return
     */
    public Date getSyncPoint() {
        long lsyncPoint = metaData.getItemValueLong(ResyncService.ITEM_SYNCPOINT);
        Date syncPoint = new Date(lsyncPoint);
        return syncPoint;
    }

    /**
     * This method updates the current synpoint
     * 
     * @throws ArchiveException
     */
    public void updateSyncPoint() {
        try {
            // update sync date...
            SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
            Date syncDate = dt.parse(newSyncPoint);
            logger.info("......updateing syncpoint=" + this.newSyncPoint);
            metaData.setItemValue(ResyncService.ITEM_SYNCPOINT, syncDate.getTime());
            dataService.saveMetadata(metaData);

            // restart sync?
            if (!resyncService.isRunning()) {
                resyncService.start();
            }

        } catch (ArchiveException | ParseException e) {
            logger.severe("failed to set new syncpoint: " + e.getMessage());
        }

    }
    
    /**
     * Returns true if the sync service is actually running
     * @return
     */
    public boolean isRunning() {
        return resyncService.isRunning();
    }

    /**
     * This method cancels a current running sny process
     * 
     * @throws ArchiveException
     */
    public void cancel() {
        try {
            resyncService.cancel();
        } catch (ArchiveException e) {
            e.printStackTrace();
        }

    }


}