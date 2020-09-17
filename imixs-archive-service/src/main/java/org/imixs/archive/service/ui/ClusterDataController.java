package org.imixs.archive.service.ui;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.archive.service.resync.ResyncService;
import org.imixs.archive.service.util.MessageService;
import org.imixs.workflow.ItemCollection;

/**
 * CID Bean provide cluster configuration.
 * 
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class ClusterDataController implements Serializable {

    private static final long serialVersionUID = 1L;

    private static Logger logger = Logger.getLogger(ClusterDataController.class.getName());

    String syncSizeUnit = null;
    ItemCollection metaData = null;

    @Inject
    ClusterService clusterService;

    @Inject
    DataService dataService;

    @Inject
    ResyncService syncService;

    @Inject
    MessageService messageService;

    @Inject
    @ConfigProperty(name = ClusterService.ENV_ARCHIVE_CLUSTER_CONTACTPOINTS)
    Optional<String> contactPoint;

    @Inject
    @ConfigProperty(name = ClusterService.ENV_ARCHIVE_CLUSTER_KEYSPACE)
    Optional<String> keySpace;

    @Inject
    @ConfigProperty(name = ClusterService.ENV_ARCHIVE_CLUSTER_REPLICATION_FACTOR, defaultValue = "1")
    String repFactor;

    @Inject
    @ConfigProperty(name = ClusterService.ENV_ARCHIVE_CLUSTER_REPLICATION_CLASS, defaultValue = "SimpleStrategy")
    String repClass;

    @Inject
    @ConfigProperty(name = ClusterService.ENV_WORKFLOW_SERVICE_ENDPOINT)
    Optional<String> workflowServiceEndpoint;

    public ClusterDataController() {
        super();
    }

    /**
     * This method initializes a cluster and session obejct.
     * 
     * @throws ArchiveException
     * @see {@link ClusterDataController#close()}
     */
    @PostConstruct
    void init() {
        // load metadata
        try {
            metaData = dataService.loadMetadata();
        } catch (ArchiveException e) {
            logger.severe("Failed to load meta data!");
            e.printStackTrace();
        }
    }

    /**
     * Returns true if a connection to the specified keySpace was successful
     * 
     * @return true if session was successfull established.
     */
    public boolean isConnected() {
        return (clusterService.getSession() != null);
    }

    /**
     * This method starts a restore process
     * 
     * 
     */
    public void start() {
        try {
            syncService.start();
        } catch (ArchiveException e) {
            e.printStackTrace();
        }
    }

    public boolean isRunning() {
        return syncService.isRunning();
    }

    /**
     * This method cancles the current sync
     * 
     * 
     * @throws ArchiveException
     */
    public void cancel() {
        try {
            syncService.cancel();
        } catch (ArchiveException e) {
            e.printStackTrace();
        }

    }

    /**
     * returns the last reSync point of the current metaData object
     * 
     * @return
     */
    public Date getSyncPoint() {
        long lsyncPoint = metaData.getItemValueLong(ResyncService.ITEM_SYNCPOINT);
        Date syncPoint = new Date(lsyncPoint);
        return syncPoint;
    }

    public long getSyncCount() {
        return metaData.getItemValueLong(ResyncService.ITEM_SYNCCOUNT);
    }

    public String getSyncSize() {
        long l = metaData.getItemValueLong(ResyncService.ITEM_SYNCSIZE);
        String result = messageService.userFriendlyBytes(l);

        String[] parts = result.split(" ");
        syncSizeUnit = parts[1];
        return parts[0];
    }

    public String getSyncSizeUnit() {
        return syncSizeUnit;
    }

    public String getContactPoints() {
        return contactPoint.get();
    }

    public String getKeySpace() {
        return keySpace.get();
    }

    public String getReplicationFactor() {
        return repFactor;

    }

    public String getReplicationClass() {
        return repClass;
    }

    public String getServiceEndpoint() {
        return workflowServiceEndpoint.get();
    }

    /**
     * Returns the message list in reverse order.
     * 
     * @return
     */
    public List<String> getMessages() {
        List<String> messageLog = messageService.getMessages(ResyncService.MESSAGE_TOPIC);
        // revrese order (use cloned list)
        List<String> result = new ArrayList<String>();
        for (String message : messageLog) {
            result.add(message);
        }
        Collections.reverse(result);
        return result;
    }
}