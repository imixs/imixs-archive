package org.imixs.workflow.archive.hadoop;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.SessionContext;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.engine.ObserverPlugin;
import org.imixs.workflow.engine.plugins.AbstractPlugin;
import org.imixs.workflow.exceptions.InvalidAccessException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.exceptions.QueryException;

/**
 * This ArchiveLocalPlugin provides a mechanism to archive the content of a
 * workitem into a snapshot workitem. Attached files will be linked from the
 * snapshot-workitem to the origin-workitem.
 * 
 * The snapshot process includes the following stages:
 * 
 * <ol>
 * <li>create a copy of the current workitem
 * <li>compute a snapshot $uniqueId containing a timestamp
 * <li>change the type with the prefix 'archive-'
 * <li>If an old snapshot already exists, Files are compared to the current $
 * files and, if necessary, stored in the Snapshot applied
 * <li>remove file content form the origin-workitem
 * <li>store the snapshot uniqeId into the origin-workitem ($snapshotID)
 * <li>remove deprecated snapshots
 * </ol>
 * 
 * The Plugin implements the ObserverPlugin interface
 * 
 * <p>
 * Note: The ArchiveLocalPlugin replaces the DMSPlugin from the imixs-marty
 * project and provides a migration mechanism for old BlobWorkitems. The old
 * BlobWorkitems will not be deleted.
 * 
 * @version 1.0
 * @author rsoika
 */
public class ArchiveLocalPlugin extends AbstractPlugin implements ObserverPlugin {

	@EJB
	DocumentService documentService;

	@Resource
	SessionContext ejbCtx;

	private static Logger logger = Logger.getLogger(ArchiveLocalPlugin.class.getName());

	/**
	 * The run method
	 * 
	 * @param ctx
	 * @return
	 * @throws Exception
	 */
	@Override
	public ItemCollection run(ItemCollection document, ItemCollection event) throws PluginException {
		// default
		logger.info("default run.... ");
		return document;
	}

	@Override
	public ItemCollection afterRegistration(ItemCollection workitem) throws PluginException {
		logger.info("callback afterRegistration ");
		return workitem;
	}

	@Override
	public ItemCollection beforeProcess(ItemCollection workitem) throws PluginException {
		logger.info("callback beforeProcess ");
		return workitem;
	}

	/**
	 * The snapshot-workitem is created immediately after the workitem was processed
	 * and before the workitem is saved.
	 */
	@Override
	public ItemCollection afterProcess(ItemCollection workitem) throws PluginException {

		// 1.) create a copy of the current workitem
		logger.info("create snapshot-workitem.... ");
		ItemCollection snapshot = (ItemCollection) workitem.clone();

		// 2.) compute a snapshot $uniqueId containing a timestamp
		String snapshotUniqueID = workitem.getUniqueID() + "-" + System.currentTimeMillis();
		logger.info("snapshot-uniqueid=" + snapshotUniqueID);
		snapshot.replaceItemValue(WorkflowKernel.UNIQUEID, snapshotUniqueID);

		// 3. change the type with the prefix 'archive-'
		String type = "archive-" + workitem.getType();
		logger.info("snapshot-type=" + type);
		snapshot.replaceItemValue(WorkflowKernel.TYPE, type);

		// 4. If an old snapshot already exists, Files are compared to the current
		// $files and, if necessary, stored in the Snapshot applied
		ItemCollection lastSnapshot = findLastSnapshot(workitem.getUniqueID());
		boolean missingContent = copyFilesFromItemCollection(lastSnapshot, snapshot);
		if (missingContent) {
			// we did not found all the content of files in the last snapshot, so we need to
			// lookup the deprecated BlobWorkitem
			ItemCollection blobWorkitem = loadBlobWorkitem(snapshot);
			if (blobWorkitem != null) {
				copyFilesFromItemCollection(blobWorkitem, snapshot);
			}
		}

		// 5. remove file content form the origin-workitem
		Map<String, List<Object>> files = snapshot.getFiles();
		// empty data...
		byte[] empty = { 0 };
		for (Entry<String, List<Object>> entry : files.entrySet()) {
			String aFilename = entry.getKey();
			List<?> file = entry.getValue();
			// remove content....
			String contentType = (String) file.get(0);
			byte[] fileContent = (byte[]) file.get(1);
			if (fileContent != null && fileContent.length > 1) {
				// add the file name (with empty data)
				logger.info("drop content for file '" + aFilename + "'");
				workitem.addFile(empty, aFilename, contentType);
			}
		}

		// 6. store the snapshot uniqeId into the origin-workitem ($snapshotID)
		workitem.replaceItemValue("$snapshotID", snapshot.getUniqueID());

		// 7. remove deprecated snapshots - note: this method should not be called in
		// HadoopArchivePlugin!
		removeDeprecatedSnaphosts(snapshot.getUniqueID());

		// save the snapshot....
		documentService.save(snapshot);

		return workitem;
	}

	/**
	 * This method returns the latest Snapshot-workitem for a given $UNIQUEID, or
	 * null if no snapshot-workitem exists.
	 * 
	 * @param uniqueid
	 * @return
	 */
	ItemCollection findLastSnapshot(String uniqueid) {
		if (uniqueid == null || uniqueid.isEmpty()) {
			throw new InvalidAccessException(DocumentService.INVALID_UNIQUEID, "undefined $uniqueid");
		}

		String query = "SELECT document FROM Document AS document ";
		query += " WHERE document.id > '" + uniqueid + "-'";
		query += " ORDER BY document.created DESC";
		List<ItemCollection> result = documentService.getDocumentsByQuery(query, 1);

		if (result.size() >= 1) {
			return result.get(0);
		} else {
			return null;
		}
	}

	/**
	 * This method removes all snapshots older than the given current snapshot id
	 */
	void removeDeprecatedSnaphosts(String snapshotID) {

		if (snapshotID == null || snapshotID.isEmpty()) {
			throw new InvalidAccessException(DocumentService.INVALID_UNIQUEID, "undefined $snapshotID");
		}

		String snapshtIDPfafix = snapshotID.substring(0, snapshotID.lastIndexOf('-'));
		String query = "SELECT document FROM Document AS document ";
		query += " WHERE document.id > '" + snapshtIDPfafix + "-' AND document.id < '" + snapshotID + "'";
		List<ItemCollection> result = documentService.getDocumentsByQuery(query);

		if (result.size() > 0) {
			logger.info("remove deprecated snapshots before snapshot: '" + snapshotID + "'....");
			for (ItemCollection oldSnapshot : result) {
				logger.info("remove deprecated snapshot: " + oldSnapshot.getUniqueID());
				documentService.remove(oldSnapshot);
			}
		}

	}

	/**
	 * This helper method transfers the $files content from a source workitem into a
	 * target workitem if no content for the same file exists in the source
	 * workitem.
	 * 
	 * The method returns true if a content was missin in the source workitem.
	 * 
	 * @param source
	 * @param target
	 * @return
	 */
	private boolean copyFilesFromItemCollection(ItemCollection source, ItemCollection target) {
		boolean missingContent = false;
		Map<String, List<Object>> files = target.getFiles();
		for (Map.Entry<String, List<Object>> entry : files.entrySet()) {
			String fileName = entry.getKey();
			List<Object> file = entry.getValue();
			// test if the content of the file is empty. In this case it makes sense to copy
			// the already archived conent from the last snapshot
			byte[] content = (byte[]) file.get(1);
			if (content.length <= 1) {
				// fetch the old content from the last snapshot...
				List<Object> oldFile = source.getFile(fileName);
				if (oldFile != null) {
					logger.info("copy file content from last snapshot: " + source.getUniqueID());
					target.addFile((byte[]) oldFile.get(1), fileName, (String) oldFile.get(0));
				} else {
					missingContent = true;
					logger.warning("Missing file content!");
				}
			}
		}
		return missingContent;

	}

	/**
	 * This method loads the BlobWorkitem for a given parent WorkItem. The
	 * BlobWorkitem is identified by the $unqiueidRef.
	 * <p>
	 * Note: This method is only used to migrate deprecated blobworkitems.
	 * 
	 * @param parentWorkitem
	 *            - the corresponding parent workitem, or null if not blobworkitem
	 *            is found
	 * @version 1.0
	 */
	@Deprecated
	private ItemCollection loadBlobWorkitem(ItemCollection parentWorkitem) {

		// is parentWorkitem defined?
		if (parentWorkitem == null) {
			logger.warning("Unable to load blobWorkitem from parent workitem == null!");
			return null;
		}

		// try to load the blobWorkitem with the parentWorktiem reference....
		String sUniqueID = parentWorkitem.getUniqueID();
		if (!"".equals(sUniqueID)) {
			// search entity...
			String sQuery = "(type:\"workitemlob\" AND $uniqueidref:\"" + sUniqueID + "\")";

			Collection<ItemCollection> itemcol = null;
			try {
				itemcol = getWorkflowService().getDocumentService().find(sQuery, 1, 0);
			} catch (QueryException e) {
				logger.severe("loadBlobWorkitem - invalid query: " + e.getMessage());
			}
			// if blobWorkItem was found return...
			if (itemcol != null && itemcol.size() > 0) {
				return itemcol.iterator().next();
			} else {
				// no blobworkitem found!
				return null;
			}

		} else {
			// no blobworkitem defined!
			return null;
		}

	}

}