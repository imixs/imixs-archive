package org.imixs.archive.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.enterprise.event.Observes;

import org.imixs.archive.lucene.FileParserService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.engine.DocumentEvent;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.exceptions.InvalidAccessException;
import org.imixs.workflow.exceptions.QueryException;

/**
 * This service component provides a mechanism to transfer the content of a
 * workitem into a snapshot workitem. Attached files will be linked from the
 * snapshot-workitem to the origin-workitem.
 * 
 * The snapshot process includes the following stages:
 * 
 * <ol>
 * <li>create a copy of the origin workitem instance
 * <li>compute a snapshot $uniqueId based on the origin workitem suffixed with a
 * timestamp
 * <li>change the type of the snapshot-workitem with the prefix 'archive-'
 * <li>If an old snapshot already exists, Files are compared to the current $
 * files and, if necessary, stored in the Snapshot applied
 * <li>remove the file content form the origin-workitem
 * <li>store the snapshot uniqeId into the origin-workitem as a reference
 * ($snapshotID)
 * <li>remove deprecated snapshots
 * </ol>
 * 
 * A snapshot workitem holds a reference to the origin workitem by its own
 * $uniqueId which is always the $uniqueId from the origin workitem suffixed
 * with a timestamp. During the snapshot creation the snapshot-uniquId is stored
 * into the origin workitem.
 * 
 * The SnapshotService implements the CDI Observer pattern provided from the
 * DocumentService.
 * 
 * 
 * <p>
 * Model entries are not part of the snapshot concept
 * 
 * <p>
 * Note: The SnapshotService replaces the BlobWorkitems mechanism which was
 * earlier part of the DMSPlugin from the imixs-marty project. The
 * SnapshotService provides a migration mechanism for old BlobWorkitems. The old
 * BlobWorkitems will not be deleted. If the DMSPlugin is still active and
 * documents from the type 'workitemlob' will be saved, the SnapshotService
 * throws a SnapshotException.
 * 
 * @version 1.0
 * @author rsoika
 */
@Stateless
public class SnapshotService {

	@Resource
	SessionContext ejbCtx;

	@EJB
	DocumentService documentService;

	@EJB
	FileParserService fileParserService;

	private static Logger logger = Logger.getLogger(SnapshotService.class.getName());

	public static String SNAPSHOTID = "$snapshotID";
	private static String TYPE_PRAFIX = "snapshot-";

	/**
	 * The snapshot-workitem is created immediately after the workitem was processed
	 * and before the workitem is saved.
	 */
	public void onSave(@Observes DocumentEvent documentEvent) {

		String type = documentEvent.getDocument().getType();

		if (documentEvent.getEventType() != DocumentEvent.ON_DOCUMENT_SAVE) {
			// skip
			return;
		}
		if (type.startsWith(TYPE_PRAFIX)) {
			// skip recursive call
			return;
		}

		if ("model".equals(type)) {
			// skip models
			return;
		}

		// throw SnapshotException if a deprecated workitemlob is saved....
		if ("workitemlob".equals(type)) {
			throw new SnapshotException(SnapshotException.INVALID_DATA,
					"deprecated workitemlob - verifiy models for DMSPlugin");
		}

		// 1.) create a copy of the current workitem
		logger.fine("creating new snapshot-workitem.... ");
		ItemCollection snapshot = (ItemCollection) documentEvent.getDocument().clone();

		// 2.) compute a snapshot $uniqueId containing a timestamp
		String snapshotUniqueID = documentEvent.getDocument().getUniqueID() + "-" + System.currentTimeMillis();
		logger.fine("snapshot-uniqueid=" + snapshotUniqueID);
		snapshot.replaceItemValue(WorkflowKernel.UNIQUEID, snapshotUniqueID);

		// 3. change the type with the prefix 'snapshot-'
		type = TYPE_PRAFIX + documentEvent.getDocument().getType();
		logger.fine("type=" + type);
		snapshot.replaceItemValue(WorkflowKernel.TYPE, type);

		// 4. If an old snapshot already exists, Files are compared to the current
		// $files and, if necessary, stored in the Snapshot applied
		ItemCollection lastSnapshot = findLastSnapshot(documentEvent.getDocument().getUniqueID());
		boolean missingContent = copyFilesFromItemCollection(lastSnapshot, snapshot);
		if (missingContent) {
			// we did not found all the content of files in the last snapshot, so we need to
			// lookup the deprecated BlobWorkitem
			ItemCollection blobWorkitem = loadBlobWorkitem(documentEvent.getDocument());
			if (blobWorkitem != null) {
				copyFilesFromItemCollection(blobWorkitem, snapshot);
			}
		}

		// 5. remove file content form the origin-workitem
		Map<String, List<Object>> files = snapshot.getFiles();
		if (files != null) {
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
					logger.fine("drop content for file '" + aFilename + "'");
					documentEvent.getDocument().addFile(empty, aFilename, contentType);
				}
			}
		}

		// 6. store the snapshot uniqeId into the origin-workitem ($snapshotID)
		documentEvent.getDocument().replaceItemValue(SNAPSHOTID, snapshot.getUniqueID());

		// 7. remove deprecated snapshots - note: this method should not be called in
		// HadoopArchivePlugin!
		removeDeprecatedSnaphosts(snapshot.getUniqueID());

		// save the snapshot immutable without indexing....
		snapshot.replaceItemValue(DocumentService.NOINDEX, true);
		snapshot.replaceItemValue(DocumentService.IMMUTABLE, true);

		// lucene: indexing file content... (experimental)
		// fileParserService.parse(snapshot);

		// so nochmal ein kleiner test ob wir überhaupt daten haben.....
		// Map<String, List<Object>> testfiles = snapshot.getFiles();

		documentService.save(snapshot);
	}

	/**
	 * This method returns the latest Snapshot-workitem for a given $UNIQUEID, or
	 * null if no snapshot-workitem exists.
	 * 
	 * The method queries the possible snapshot id-range for a given $UniqueId sorted
	 * by creation date descending and returns the first match.
	 * 
	 * @param uniqueid
	 * @return
	 */
	ItemCollection findLastSnapshot(String uniqueid) {
		if (uniqueid == null || uniqueid.isEmpty()) {
			throw new InvalidAccessException(DocumentService.INVALID_UNIQUEID, "undefined $uniqueid");
		}

		String query = "SELECT document FROM Document AS document ";
		query += " WHERE document.id > '" + uniqueid + "-' AND document.id < '" + uniqueid + "-9999999999999'";
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
			logger.fine("remove deprecated snapshots before snapshot: '" + snapshotID + "'....");
			for (ItemCollection oldSnapshot : result) {
				logger.fine("remove deprecated snapshot: " + oldSnapshot.getUniqueID());
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
		if (files != null) {
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
						logger.fine("copy file content from last snapshot: " + source.getUniqueID());
						target.addFile((byte[]) oldFile.get(1), fileName, (String) oldFile.get(0));
					} else {
						missingContent = true;
						logger.warning("Missing file content!");
					}
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
				itemcol = documentService.find(sQuery, 1, 0);
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