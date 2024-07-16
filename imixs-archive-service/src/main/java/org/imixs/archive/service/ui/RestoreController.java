package org.imixs.archive.service.ui;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.archive.service.restore.RestoreScheduler;
import org.imixs.archive.service.resync.ResyncService;
import org.imixs.archive.service.util.MessageService;
import org.imixs.workflow.ItemCollection;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Timer;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * CID Bean for the resync service.
 * <p>
 * The syncpoint is managed as a string in the format 2019-12-31T06:00
 * 
 * 
 * 
 * @author rsoika
 *
 */
@Named
@SessionScoped
public class RestoreController implements Serializable {

    public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    private static final long serialVersionUID = 1L;
    private static Logger logger = Logger.getLogger(RestoreController.class.getName());

    long restoreDateFrom;
    long restoreDateTo;
    String restoreSizeUnit = null;
    ItemCollection metaData = null;

    protected List<ItemCollection> options = null;

    @Inject
    ClusterService clusterService;

    @Inject
    DataService dataService;

    @Inject
    RestoreScheduler restoreService;

    @Inject
    MessageService messageService;

    public RestoreController() {
        super();
    }

    /**
     * This method initializes the default sync date
     * 
     */
    @PostConstruct
    void init() {
        reset();
    }

    /**
     * This method initializes the default sync date
     * 
     */
    public void reset() {
        try {
            // load metadata
            metaData = dataService.loadMetadata();
            // load options
            options = restoreService.getOptions(metaData);
        } catch (ArchiveException e) {
            logger.severe("Failed to load meta data!");
            e.printStackTrace();
        }
    }

    public String getRestoreFrom() {
        SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
        return dt.format(restoreDateFrom);

    }

    public void setRestoreFrom(String restorePoint) throws ParseException {
        if (restorePoint != null && !restorePoint.isEmpty()) {
            // update sync date...
            SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
            try {
                restoreDateFrom = dt.parse(restorePoint).getTime();
            } catch (ParseException e) {
                logger.severe("Unable to parse syncdate: " + e.getMessage());
            }
        }
    }

    public String getRestoreTo() {
        if (restoreDateTo == 0) {
            // default current time
            restoreDateTo = new Date().getTime();

            // NOTE:
            // Because the current syncPoint has milisecont precission, but we format the
            // restoreTo date in seconds only, we need to ajust the restoreTo timestamp per
            // 1 second! Otherwise the last snaspshot is typically excluded from the restore
            // because of its milisecond precission.
            restoreDateTo = restoreDateTo + 1000; // !!
        }
        SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
        return dt.format(restoreDateTo);
    }

    public void setRestoreTo(String restorePoint) {
        if (restorePoint != null && !restorePoint.isEmpty()) {
            // update sync date...
            SimpleDateFormat dt = new SimpleDateFormat(ISO_DATETIME_FORMAT);
            try {
                restoreDateTo = dt.parse(restorePoint).getTime();
            } catch (ParseException e) {
                logger.severe("Unable to parse syncdate: " + e.getMessage());
            }
        }
    }

    /**
     * returns the syncpoint of the current configuration
     * 
     * @return
     */
    public Date getRestoreSyncPoint() {
        long lsyncPoint;
        lsyncPoint = metaData.getItemValueLong(RestoreScheduler.ITEM_RESTORE_SYNCPOINT);
        Date syncPoint = new Date(lsyncPoint);
        return syncPoint;
    }

    public long getRestoreCount() {
        return metaData.getItemValueLong(RestoreScheduler.ITEM_RESTORE_SYNCCOUNT);
    }

    public long getRestoreErrors() {
        return metaData.getItemValueLong(RestoreScheduler.ITEM_RESTORE_SYNCERRORS);
    }

    public String getRestoreSize() {
        long l = metaData.getItemValueLong(RestoreScheduler.ITEM_RESTORE_SYNCSIZE);
        String result = messageService.userFriendlyBytes(l);
        String[] parts = result.split(" ");
        restoreSizeUnit = parts[1];
        return parts[0];
    }

    public String getRestoreSizeUnit() {
        return restoreSizeUnit;
    }

    /**
     * returns the syncpoint of the current configuration
     * 
     * @return
     */
    public long getSyncPoint() {
        return metaData.getItemValueLong(ResyncService.ITEM_SYNCPOINT);
    }

    /**
     * returns the syncpoint of the current configuration
     * 
     * @return
     */
    public String getSyncPointISO() {
        SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        Date date = new Date(getSyncPoint());
        return dt.format(date);
    }

    /**
     * This method starts a restore process
     * 
     * 
     */
    @SuppressWarnings("unchecked")
    public void startRestore() {
        try {
            logger.info("......init restore process: " + this.getRestoreFrom() + " to " + this.getRestoreTo());
            restoreService.setOptions(options, metaData);
            restoreService.start(restoreDateFrom, restoreDateTo,
                    metaData.getItemValue(RestoreScheduler.ITEM_RESTORE_OPTIONS));
        } catch (ArchiveException e) {
            logger.severe("failed to start restore process: " + e.getMessage());
        }

    }

    /**
     * This method cancels a current running sny process
     * 
     * @throws ArchiveException
     */
    public void stopRestore() {
        try {
            restoreService.cancel();
        } catch (ArchiveException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns true if a restore is running.
     * 
     * @return
     */
    public boolean isRunning() {
        Timer timer = restoreService.findTimer();
        return (timer != null);
    }

    public List<String> getMessages() {
        return messageService.getMessages(RestoreScheduler.MESSAGE_TOPIC);
    }

    /**
     * This methd returns a ItemCollection for each option
     * 
     * Example: <code>
     *   #{restoreController.options}
     * </code>
     * 
     * @return
     */
    public List<ItemCollection> getOptions() {
        return options;
    }

    public void setOptions(List<ItemCollection> options) {
        this.options = options;
    }

    /**
     * Adds a new filter option
     */
    public void addOption() {
        if (options == null) {
            options = new ArrayList<ItemCollection>();
        }

        ItemCollection itemCol = new ItemCollection();
        itemCol.replaceItemValue("type", "filter");
        options.add(itemCol);

    }

    /**
     * Removes an option by name
     * 
     * @param optionName
     */
    public void removeOption(String optionName) {
        if (options != null) {

            int iPos = 0;
            for (ItemCollection item : options) {
                if (optionName.equals(item.getItemValueString("name"))) {
                    options.remove(iPos);
                    break;
                }
                iPos++;
            }
        }
    }

}