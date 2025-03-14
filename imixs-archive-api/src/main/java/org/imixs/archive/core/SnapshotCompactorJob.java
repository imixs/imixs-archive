/*******************************************************************************
 * Imixs-Workflow Archive 
 * Copyright (C) 2001-2018 Imixs Software Solutions GmbH,  
 * http://www.imixs.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * 
 *
 * Project: 
 * 	http://www.imixs.org
 *
 * Contributors:  
 * 	Imixs Software Solutions GmbH - initial API and implementation
 * 	Ralph Soika - Software Developer
 *******************************************************************************/

package org.imixs.archive.core;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.imixs.archive.core.cassandra.ArchiveRemoteService;
import org.imixs.melman.RestAPIException;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.exceptions.AccessDeniedException;

import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.EJB;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;

/**
 * The SnapshotCompactorJob deletes snapshot entities in a separate transaction.
 * The service is used by the SnapshotCompactorService class.
 * 
 * @version 1.0
 * @author rsoika
 */
@Stateless
@LocalBean
@DeclareRoles({ "org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@RunAs("org.imixs.ACCESSLEVEL.MANAGERACCESS")
public class SnapshotCompactorJob {

    private static Logger logger = Logger.getLogger(SnapshotCompactorService.class.getName());

    @EJB
    DocumentService documentService;

    @EJB
    ArchiveRemoteService archiveRemoteService;

    /**
     * Start a new transaction to isolate multiple iterations.
     * 
     * @param period
     * @param batchSize
     * @param doDelete
     */
    @TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
    public ItemCollection processSnapshotBatchWithFinding(int period, int batchSize, boolean doDelete) {
        if (period < 1) {
            throw new SnapshotException(SnapshotCompactorService.SNAPSHOT_COMPACTOR_ERROR,
                    "Grace period must not be below 1 year!");
        }

        ItemCollection metaData = new ItemCollection();

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -period);
        Date gracePeriodDate = calendar.getTime();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String compactDate = dateFormat.format(gracePeriodDate);
        logger.info("│   ├── Compact Date < " + compactDate);

        // process in one Transaktion
        List<ItemCollection> snapshots = findAllSnapshotsByGracePeriod(compactDate, batchSize);
        metaData.setItemValue("snapshots.processed", snapshots.size());
        int deletions = processSnapshotBatch(snapshots, doDelete);
        metaData.setItemValue("snapshots.deleted", deletions);
        return metaData;
    }

    /**
     * Process a smaller batch of snapshots
     */
    // @TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
    private int processSnapshotBatch(List<ItemCollection> snapshots, boolean doDelete) {
        int deletions = 0;
        for (ItemCollection snapshot : snapshots) {

            String id = snapshot.getUniqueID();
            boolean snapshotExists = false;
            try {
                List<ItemCollection> remoteSnapshot = archiveRemoteService.loadSnapshotFromArchive(id);
                snapshotExists = (remoteSnapshot != null && remoteSnapshot.size() > 0);

                if (snapshotExists) {
                    logger.info("│   │   ├── Snapshot " + id + " exists in archive and will be deleted now...");
                    if (doDelete) {
                        documentService.remove(snapshot);
                        deletions++;
                    }
                } else {
                    logger.warning("│   │   ├── Snapshot " + id
                            + " not found in archive! Snapshot data will be refreshed!");
                    String originId = id.substring(id.lastIndexOf("-") + 1);
                    ItemCollection originWorkitem = documentService.load(originId);
                    if (originWorkitem == null) {
                        logger.severe("│   │   ├── Fatal Error - origin workitem '" + originId + "' does not exist!");
                    } else {
                        // force snapshot creation by saving the origin data...
                        documentService.save(originWorkitem);
                        // short delay (5s)....
                        Thread.sleep(5000);
                    }
                }
            } catch (AccessDeniedException | RestAPIException | InterruptedException e) {
                logger.warning("│   │   ├── Failed to process snapshot " + id + " : " + e.getMessage());
            }
        }
        return deletions;
    }

    /**
     * This method returns all existing Snapshot-workitems after a given grace
     * period.
     * 
     * The method selects only archive, archivedeleted and workitemdeleted.
     * 
     * 
     * @param uniqueid
     * @return
     */
    // @TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
    private List<ItemCollection> findAllSnapshotsByGracePeriod(String compactDate, int maxCount) {

        String query = "SELECT document FROM Document AS document WHERE "
                + "(document.type = 'snapshot-workitemarchive' OR document.type = 'snapshot-workitemarchivedeleted'  OR document.type = 'snapshot-workitemdeleted' )"
                + "  AND document.modified <'" + compactDate
                + "' ORDER BY document.modified DESC";
        return documentService.getDocumentsByQuery(query, maxCount);
    }

}