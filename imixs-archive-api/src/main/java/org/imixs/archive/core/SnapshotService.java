package org.imixs.archive.core;

import java.security.NoSuchAlgorithmException;
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

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.engine.DocumentEvent;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.engine.PropertyService;
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
 * <li>change the type of the snapshot-workitem with the prefix 'snapshot-'
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
 * with a timestamp.
 * <p>
 * 
 * <pre>
 * 7009e427-7078-4492-af78-0a1145a736df-[SNAPSHOT TIMESTAMP]
 * </pre>
 * <p>
 * During the snapshot creation the snapshot-uniquId is stored into the origin
 * workitem.
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
	PropertyService propertyService;

	private static Logger logger = Logger.getLogger(SnapshotService.class.getName());

	public static String SNAPSHOTID = "$snapshotid";
	public static String TYPE_PRAFIX = "snapshot-";
	public static String PROPERTY_SNAPSHOT_HISTORY = "snapshot.history";
	public static String PROPERTY_SNAPSHOT_PROTECTFILECONTENT = "snapshot.protectFileContent";

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
					"deprecated workitemlob - SnapshotService can not be combined with deprecated version of marty (3.1).");
		}

		// 0.) update the dms information
		try {
			DMSHandler.updateDMSMetaData(documentEvent.getDocument(), ejbCtx.getCallerPrincipal().getName());
		} catch (NoSuchAlgorithmException | IllegalStateException e) {
			throw new SnapshotException(SnapshotException.INVALID_DATA,
					"Update DMS Meta Data failed: " + e.getMessage(), e);
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
		logger.fine("new document type = " + type);
		snapshot.replaceItemValue(WorkflowKernel.TYPE, type);

		boolean protectContent = Boolean.parseBoolean(
				propertyService.getProperties().getProperty(PROPERTY_SNAPSHOT_PROTECTFILECONTENT, "false"));

		// 4. If an old snapshot already exists, Files are compared to the current
		// $files and, if necessary, stored in the Snapshot applied
		ItemCollection lastSnapshot = findLastSnapshot(documentEvent.getDocument().getUniqueID());
		boolean missingContent = false;
		if (lastSnapshot == null) {
			missingContent = true;
		} else {
			missingContent = copyFilesFromItemCollection(lastSnapshot, snapshot, documentEvent.getDocument(),
					protectContent);
		}
		if (missingContent) {
			// we did not found all the content of files in the last snapshot, so we need to
			// lookup the deprecated BlobWorkitem
			ItemCollection blobWorkitem = loadBlobWorkitem(documentEvent.getDocument());
			if (blobWorkitem != null) {
				logger.info("migrating file content from blobWorkitem for document '"
						+ documentEvent.getDocument().getUniqueID() + "' ....");
				copyFilesFromItemCollection(blobWorkitem, snapshot, documentEvent.getDocument(), protectContent);
			}
		}

		// 5. remove file content form the origin-workitem
		Map<String, List<Object>> files = documentEvent.getDocument().getFiles();
		if (files != null) {
			// empty data...
			byte[] empty = {};
			for (Entry<String, List<Object>> entry : files.entrySet()) {
				String aFilename = entry.getKey();
				List<?> file = entry.getValue();
				// remove content if size > 1....
				String contentType = (String) file.get(0);
				byte[] fileContent = (byte[]) file.get(1);
				if (fileContent != null && fileContent.length > 0) {
					// update the file name with empty data
					logger.fine("drop content for file '" + aFilename + "'");
					documentEvent.getDocument().addFile(empty, aFilename, contentType);
				}
			}
		}

		// 6. store the snapshot uniqeId into the origin-workitem ($snapshotID)
		documentEvent.getDocument().replaceItemValue(SNAPSHOTID, snapshot.getUniqueID());

		// 7. save the snapshot immutable and without indexing....
		snapshot.replaceItemValue(DocumentService.NOINDEX, true);
		snapshot.replaceItemValue(DocumentService.IMMUTABLE, true);

		documentService.save(snapshot);

		// 8. remove deprecated snapshots
		cleanSnaphostHistory(snapshot.getUniqueID());

	}

	/**
	 * This method returns the latest Snapshot-workitem for a given $UNIQUEID, or
	 * null if no snapshot-workitem exists.
	 * 
	 * The method queries the possible snapshot id-range for a given $UniqueId
	 * sorted by creation date descending and returns the first match.
	 * 
	 * @param uniqueid
	 * @return
	 */
	ItemCollection findLastSnapshot(String uniqueid) {
		if (uniqueid == null || uniqueid.isEmpty()) {
			throw new SnapshotException(DocumentService.INVALID_UNIQUEID, "undefined $uniqueid");
		}

		String query = "SELECT document FROM Document AS document WHERE document.id > '" + uniqueid
				+ "-' AND document.id < '" + uniqueid + "-9999999999999' ORDER BY document.id DESC";
		List<ItemCollection> result = documentService.getDocumentsByQuery(query, 1);
		if (result.size() >= 1) {
			return result.get(0);
		} else {
			return null;
		}
	}

	/**
	 * This method removes all snapshots older than the defined by the imixs
	 * property 'snapshot.hystory'. If the snapshot.history is set to '0' no
	 * snapshot-workitems will be removed.
	 */
	void cleanSnaphostHistory(String snapshotID) {

		if (snapshotID == null || snapshotID.isEmpty()) {
			throw new SnapshotException(DocumentService.INVALID_UNIQUEID, "invalid " + SNAPSHOTID);
		}

		// get snapshot-history
		int iSnapshotHistory = 1;
		try {
			iSnapshotHistory = Integer
					.parseInt(propertyService.getProperties().getProperty(PROPERTY_SNAPSHOT_HISTORY, "1"));
		} catch (NumberFormatException nfe) {
			throw new SnapshotException(DocumentService.INVALID_PARAMETER,
					"imixs.properties '" + PROPERTY_SNAPSHOT_HISTORY + "' must be a integer value.");
		}

		logger.fine(PROPERTY_SNAPSHOT_HISTORY + " = " + iSnapshotHistory);

		// skip if history = 0
		if (iSnapshotHistory == 0) {
			return;
		}

		logger.fine("cleanSnaphostHistory for $snapshotid: " + snapshotID);
		String snapshtIDPfafix = snapshotID.substring(0, snapshotID.lastIndexOf('-'));
		String query = "SELECT document FROM Document AS document WHERE document.id > '" + snapshtIDPfafix
				+ "-' AND document.id < '" + snapshotID + "' ORDER BY document.id ASC";

		List<ItemCollection> result = documentService.getDocumentsByQuery(query);
		while (result.size() >= iSnapshotHistory) {
			ItemCollection oldSnapshot = result.get(0);
			logger.fine("remove deprecated snapshot: " + oldSnapshot.getUniqueID());
			documentService.remove(oldSnapshot);
			result.remove(0);
		}
	}

	/**
	 * This helper method transfers the $files content from a source workitem into a
	 * target workitem if no content for the same file exists in the target
	 * workitem.
	 * 
	 * The method returns true if a content was missing in the source workitem.
	 * 
	 * 
	 * If 'protectContent' is set to 'true' than in case a file with the same name
	 * already exits, will be 'archived' with a time-stamp-sufix
	 * 
	 * e.g.: 'ejb_obj.gif' => 'ejb_obj-1514410113556.gif'
	 * 
	 * @param source
	 * @param target
	 * @return
	 */
	private boolean copyFilesFromItemCollection(ItemCollection source, ItemCollection target, ItemCollection origin,
			boolean protectContent) {
		boolean missingContent = false;

		Map<String, List<Object>> files = target.getFiles();
		if (files != null) {
			for (Map.Entry<String, List<Object>> entry : files.entrySet()) {
				String fileName = entry.getKey();
				List<Object> file = entry.getValue();
				// test if the content of the file is empty. In this case we copy
				// the content from the last snapshot (source)
				byte[] content = (byte[]) file.get(1);
				if (content.length == 0 || content.length <= 2) { // <= 2 migration issue from blob-workitem (size can
																	// be 1 byte)
					// fetch the old content from source...
					if (source != null) {
						List<Object> oldFile = source.getFile(fileName);
						if (oldFile != null) {
							logger.fine("copy file content '" + fileName + "' from: " + source.getUniqueID());
							target.addFile((byte[]) oldFile.get(1), fileName, (String) oldFile.get(0));
						} else {
							missingContent = true;
							logger.warning("Missing file content!");
						}
					} else {
						missingContent = true;
						logger.warning("Missing file content!");
					}
				} else {
					// in case 'protect content' is set to 'true' we protect existing content of
					// files with the same name, but extend the name of the old file with a sufix
					if (protectContent) {
						List<Object> oldFile = source.getFile(fileName);
						if (oldFile != null) {
							// we need to sufix the last file with the same name here to protect the
							// content.

							// compare MD5Checksum
							ItemCollection dmsColOrigin = DMSHandler.getDMSEntry(fileName, origin);
							ItemCollection dmsColSource = DMSHandler.getDMSEntry(fileName, source);
							if (!dmsColOrigin.getItemValueString("md5checksum")
									.equals(dmsColSource.getItemValueString("md5checksum"))) {

								String protectedFileName = null;
								int iFileDot = fileName.lastIndexOf('.');
								if (iFileDot > 0) {
									// create a unique filename...
									protectedFileName = fileName.substring(0, iFileDot) + "-"
											+ System.currentTimeMillis() + fileName.substring(iFileDot);
								} else {
									protectedFileName = fileName + "-" + System.currentTimeMillis();
								}
								target.addFile((byte[]) oldFile.get(1), protectedFileName, (String) oldFile.get(0));

								// add filename with empty data to origin
								byte[] empty = {};
								origin.addFile(empty, protectedFileName, (String) oldFile.get(0));
								// update the DMS entry
								dmsColSource.replaceItemValue("txtname", protectedFileName);
								//dmsColOrigin.replaceItemValue("md5checksum"
								DMSHandler.putDMSEntry(dmsColSource, target);
								DMSHandler.putDMSEntry(dmsColSource, origin);
							}
						}
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