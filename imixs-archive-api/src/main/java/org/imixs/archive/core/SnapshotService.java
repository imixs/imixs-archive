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

import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.EJBTransactionRolledbackException;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.enterprise.event.Observes;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.engine.DocumentEvent;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.engine.PropertyService;
import org.imixs.workflow.exceptions.AccessDeniedException;

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
	public static String PROPERTY_SNAPSHOT_WORKITEMLOB_SUPPORT = "snapshot.workitemlob_suport";
	public static String PROPERTY_SNAPSHOT_HISTORY = "snapshot.history";
	public static String PROPERTY_SNAPSHOT_OVERWRITEFILECONTENT = "snapshot.overwriteFileContent";

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
			// in case of snapshot.workitemlob_suport=true
			// we allow saving those workitems. This is need in migration mode only
			Boolean allowWorkitemLob = Boolean.parseBoolean(
					propertyService.getProperties().getProperty(PROPERTY_SNAPSHOT_WORKITEMLOB_SUPPORT, "false"));
			if (allowWorkitemLob == true) {
				// needed only for migration
				// issue #16
				return;
			}
			// otherwise we throw a exception !
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

		boolean overwriteFileContent = Boolean.parseBoolean(
				propertyService.getProperties().getProperty(PROPERTY_SNAPSHOT_OVERWRITEFILECONTENT, "false"));

		// 4. If an old snapshot already exists, File content is taken from the last
		// snapshot.
		// It is important here, that we fetch the last snapshot by the property
		// "snapshotID". This is because in case that in one transaction multiple saves
		// are performed on the same workitem, a SQL query in JPA does not work and
		// returns an empty result as long as the transaction is not closed. But the
		// method docmentService.load() does fetch a newly saved document by its primary
		// key within the same transaction.
		ItemCollection lastSnapshot = documentService.load(documentEvent.getDocument().getItemValueString(SNAPSHOTID));

		// in case that we have no snapshot but a UNIQUEIDSOURCE we can lookup here the
		// snapshot from the origin version
		if (lastSnapshot == null
				&& !documentEvent.getDocument().getItemValueString(WorkflowKernel.UNIQUEIDSOURCE).isEmpty()) {
			logger.fine("lookup last snapshot from origin version: '"
					+ documentEvent.getDocument().getItemValueString(WorkflowKernel.UNIQUEIDSOURCE) + "'");
			lastSnapshot = documentService.load(documentEvent.getDocument().getItemValueString(SNAPSHOTID));
		}

		// in case that we have still no snapshot but a UNIQUEIDSOURCE we can lookup
		// here the snapshot from the origin version
		if (lastSnapshot == null && !documentEvent.getDocument().getItemValueString("$blobworkitem").isEmpty()) {
			logger.fine("lookup last blobworkitem: '" + documentEvent.getDocument().getItemValueString("$blobworkitem")
					+ "'");
			// try to load the blobWorkitem
			lastSnapshot = documentService.load(documentEvent.getDocument().getItemValueString("$blobworkitem"));
			if (lastSnapshot != null) {
				logger.info("migrating file content from deprecated blobWorkitem '"
						+ documentEvent.getDocument().getUniqueID() + "' ....");
			}
		}

		if (lastSnapshot != null) {
			// copy content from the last found snapshot....
			copyFilesFromItemCollection(lastSnapshot, snapshot, documentEvent.getDocument(), overwriteFileContent);
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
	 * This method returns all existing Snapshot-workitems for a given $UNIQUEID.
	 * 
	 * The method queries the possible snapshot id-range for a given $UniqueId
	 * sorted by creation date descending.
	 * 
	 * @param uniqueid
	 * @return
	 */
	public List<ItemCollection> findAllSnapshots(String uniqueid) {
		if (uniqueid == null || uniqueid.isEmpty()) {
			throw new SnapshotException(DocumentService.INVALID_UNIQUEID, "undefined $uniqueid");
		}
		String query = "SELECT document FROM Document AS document WHERE document.id > '" + uniqueid
				+ "-' AND document.id < '" + uniqueid + "-9999999999999' ORDER BY document.id DESC";
		return documentService.getDocumentsByQuery(query, 999);
	}

	/**
	 * This method removes all snapshots older than defined by the imixs property
	 * 'snapshot.hystory'. If the snapshot.history is set to '0' no
	 * snapshot-workitems will be removed.
	 * 
	 * This method protects snapshots from a split-event which are assigned to the
	 * current workitem but belong to the origin version!
	 * 
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

		// we do not want to delete snapshots which belong to the origin workitem of a
		// spit event. With the following query thus snapshots will not be selected.
		logger.fine("cleanSnaphostHistory for $snapshotid: " + snapshotID);
		String snapshtIDPfafix = snapshotID.substring(0, snapshotID.lastIndexOf('-'));
		String query = "SELECT document FROM Document AS document WHERE document.id > '" + snapshtIDPfafix
				+ "-' AND document.id < '" + snapshotID + "' ORDER BY document.id ASC";

		List<ItemCollection> result = documentService.getDocumentsByQuery(query);
		while (result.size() >= iSnapshotHistory) {
			ItemCollection oldSnapshot = result.get(0);
			logger.fine("remove deprecated snapshot: " + oldSnapshot.getUniqueID());
			try {
				documentService.remove(oldSnapshot);
			} catch (EJBTransactionRolledbackException | AccessDeniedException e) {
				logger.warning("remove deprecated snapshot '" + snapshotID + "' failed: " + e.getMessage());
			}
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
			boolean overwriteFileContent) {
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
					if (!overwriteFileContent) {
						List<Object> oldFile = source.getFile(fileName);
						if (oldFile != null) {
							// we need to sufix the last file with the same name here to protect the
							// content.

							// compare MD5Checksum
							ItemCollection dmsColOrigin = DMSHandler.getDMSEntry(fileName, origin);
							ItemCollection dmsColSource = DMSHandler.getDMSEntry(fileName, source);
							if ((dmsColOrigin != null && dmsColSource != null)
									&& (!dmsColOrigin.getItemValueString("md5checksum")
											.equals(dmsColSource.getItemValueString("md5checksum")))) {

								Date fileDate = dmsColSource.getItemValueDate("$created");
								if (fileDate == null) {
									fileDate = new Date();
								}

								TimeZone tz = TimeZone.getTimeZone("UTC");
								DateFormat df = new SimpleDateFormat("[yyyy-MM-dd'T'HH:mm:ss.SSS'Z']"); // Quoted "Z" to
																										// indicate UTC,
																										// no timezone
																										// offset
								df.setTimeZone(tz);
								String sTimeStamp = df.format(fileDate);

								String protectedFileName = null;
								int iFileDot = fileName.lastIndexOf('.');
								if (iFileDot > 0) {
									// create a unique filename...
									protectedFileName = fileName.substring(0, iFileDot) + "-" + sTimeStamp
											+ fileName.substring(iFileDot);
								} else {
									protectedFileName = fileName + "-" + sTimeStamp;
								}
								target.addFile((byte[]) oldFile.get(1), protectedFileName, (String) oldFile.get(0));

								// add filename with empty data to origin
								byte[] empty = {};
								origin.addFile(empty, protectedFileName, (String) oldFile.get(0));
								// update the DMS entry
								dmsColSource.replaceItemValue("txtname", protectedFileName);
								// dmsColOrigin.replaceItemValue("md5checksum"
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

}