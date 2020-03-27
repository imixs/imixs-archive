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
import java.util.TimeZone;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RunAs;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.engine.DocumentEvent;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.engine.EventLogService;
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
 * <p>
 * The SnapshotService implements the CDI Observer pattern provided from the
 * DocumentService.
 * <p>
 * Model entries are not part of the snapshot concept
 * <p>
 * Note: The SnapshotService replaces the BlobWorkitems mechanism which was
 * earlier part of the DMSPlugin from the imixs-marty project. The
 * SnapshotService provides a migration mechanism for old BlobWorkitems. The old
 * BlobWorkitems will not be deleted. If the DMSPlugin is still active and
 * documents from the type 'workitemlob' will be saved, the SnapshotService
 * throws a SnapshotException.
 * <p>
 * <strong>ArchivePushService</strong><br />
 * Since version 2.0 the SnapshotService can be connected to the
 * ImixsArchiveService. If the environment variable 'ARCHIVE_SERVICE_ENDPONT' is
 * set, than the snapshot service creates a EventLog entry each time a snapshot
 * was generated. The ArchivePushService sanns for the EventLog entires in an
 * asynchronious way and push the snaphots directyl into the Archive
 * (Cassandra).
 * 
 * 
 * 
 * @version 2.0
 * @author rsoika
 */
@Stateless
@LocalBean
@DeclareRoles({ "org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@RunAs("org.imixs.ACCESSLEVEL.MANAGERACCESS")
public class SnapshotService {

	public static final String REGEX_URL_PATTERN = "^(http|https|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";

	public static final String SNAPSHOTID = "$snapshotid";
	public static final String TYPE_PRAFIX = "snapshot-";
	public static final String NOSNAPSHOT = "$nosnapshot"; // ignore snapshots
	public static final String SKIPSNAPSHOT = "$skipsnapshot"; // skip snapshot creation

	public final static String ITEM_DMS_FILE_NAMES = "dms_names"; // list of files
	public final static String ITEM_DMS_FILE_COUNT = "dms_count"; // count of files
	public final static String ITEM_DMS = "dms"; // optional custom content to be indexed by lucene

	public static final String PROPERTY_SNAPSHOT_WORKITEMLOB_SUPPORT = "snapshot.workitemlob_suport";
	public static final String PROPERTY_SNAPSHOT_HISTORY = "snapshot.history";
	public static final String PROPERTY_SNAPSHOT_OVERWRITEFILECONTENT = "snapshot.overwriteFileContent";

	// rest service endpoint
	public static final String ARCHIVE_SERVICE_ENDPOINT = "archive.service.endpoint";
	public static final String ARCHIVE_SERVICE_INTERVAL = "archive.service.interval";
	public static final String ARCHIVE_SERVICE_USER = "archive.service.user";
	public static final String ARCHIVE_SERVICE_PASSWORD = "archive.service.password";
	public static final String ARCHIVE_SERVICE_AUTHMETHOD = "archive.service.authmethod";

	public static final String EVENTLOG_TOPIC_ADD = "snapshot.add";
	public static final String EVENTLOG_TOPIC_REMOVE = "snapshot.remove";

	public final static String ITEM_MD5_CHECKSUM = "md5checksum";

	@Resource
	SessionContext ejbCtx;

	@EJB
	DocumentService documentService;

	@EJB
	EventLogService eventLogService;

	@Inject
	@ConfigProperty(name = PROPERTY_SNAPSHOT_WORKITEMLOB_SUPPORT, defaultValue = "false")
	boolean allowWorkitemLob;

	@Inject
	@ConfigProperty(name = PROPERTY_SNAPSHOT_OVERWRITEFILECONTENT, defaultValue = "false")
	boolean overwriteFileContent;

	@Inject
	@ConfigProperty(name = PROPERTY_SNAPSHOT_HISTORY, defaultValue = "1")
	int iSnapshotHistory;

	@Inject
	@ConfigProperty(name = ARCHIVE_SERVICE_ENDPOINT, defaultValue = "")
	String archiveServiceEndpoint;

	private static Logger logger = Logger.getLogger(SnapshotService.class.getName());

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

		if (documentEvent.getDocument().getItemValueBoolean(NOSNAPSHOT)) {
			// skip if NOSNAPSHOT is true
			return;
		}

		if (documentEvent.getDocument().getItemValueBoolean(SKIPSNAPSHOT)) {
			// skip snaphsot creation and clear flag immediately.
			documentEvent.getDocument().removeItem(SKIPSNAPSHOT);
			return;
		}

		// throw SnapshotException if a deprecated workitemlob is saved....
		if ("workitemlob".equals(type)) {
			// in case of snapshot.workitemlob_suport=true
			// we allow saving those workitems. This is need in migration mode only
			// Boolean allowWorkitemLob = Boolean.parseBoolean(
			// propertyService.getProperties().getProperty(PROPERTY_SNAPSHOT_WORKITEMLOB_SUPPORT,
			// "false"));
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
			updateCustomAttributes(documentEvent.getDocument(), ejbCtx.getCallerPrincipal().getName());
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

		// boolean overwriteFileContent = Boolean.parseBoolean(
		// propertyService.getProperties().getProperty(PROPERTY_SNAPSHOT_OVERWRITEFILECONTENT,
		// "false"));

		// 4. If an old snapshot already exists, File content is taken from the last
		// snapshot.
		// It is important here, that we fetch the last snapshot by the property
		// "snapshotID". This is because in case that in one transaction multiple saves
		// are performed on the same workitem, a SQL query in JPA does not work and
		// returns an empty result as long as the transaction is not closed. But the
		// method docmentService.load() does fetch a newly saved document by its primary
		// key within the same transaction.
		ItemCollection lastSnapshot = documentService.load(documentEvent.getDocument().getItemValueString(SNAPSHOTID));
		boolean isBlobWorkitem = false; // support deprecated $blobWorkitems...

		// in case that we have no snapshot but a UNIQUEIDSOURCE we can lookup here the
		// snapshot from the origin version
		if (lastSnapshot == null
				&& !documentEvent.getDocument().getItemValueString(WorkflowKernel.UNIQUEIDSOURCE).isEmpty()) {
			logger.fine("lookup last snapshot from origin version: '"
					+ documentEvent.getDocument().getItemValueString(WorkflowKernel.UNIQUEIDSOURCE) + "'");
			lastSnapshot = documentService.load(documentEvent.getDocument().getItemValueString(SNAPSHOTID));
		}

		// in case that we have still no snapshot but a $blobWorkitem we can lookup
		// here the deprecated $blobWorkitem
		if (lastSnapshot == null && !documentEvent.getDocument().getItemValueString("$blobworkitem").isEmpty()) {
			logger.fine("lookup last blobworkitem: '" + documentEvent.getDocument().getItemValueString("$blobworkitem")
					+ "'");
			// try to load the blobWorkitem
			lastSnapshot = documentService.load(documentEvent.getDocument().getItemValueString("$blobworkitem"));
			if (lastSnapshot != null) {
				logger.info("migrating file content from deprecated blobWorkitem '"
						+ documentEvent.getDocument().getUniqueID() + "' ....");
				isBlobWorkitem = true; // support deprecated $blobWorkitems...
			}
		}

		if (lastSnapshot != null) {
			// copy content from the last found snapshot....
			copyFilesFromItemCollection(lastSnapshot, snapshot, documentEvent.getDocument(), overwriteFileContent,
					isBlobWorkitem);
		}

		// 5. remove file content form the origin-workitem
		List<FileData> files = documentEvent.getDocument().getFileData();
		// empty data...
		byte[] empty = {};
		for (FileData fileData : files) {
			if (fileData.getContent() != null && fileData.getContent().length > 0) {
				// update the file name with empty data
				logger.fine("drop content for file '" + fileData.getName() + "'");
				documentEvent.getDocument().addFileData(
						new FileData(fileData.getName(), empty, fileData.getContentType(), fileData.getAttributes()));
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

		// 9. write event log entry...
		if (archiveServiceEndpoint != null && !archiveServiceEndpoint.isEmpty()) {
			logger.finest("......create event log entry " + EVENTLOG_TOPIC_ADD);
			eventLogService.createEvent(EVENTLOG_TOPIC_ADD, snapshot.getUniqueID());
		}
	}

	/**
	 * All existing snapshot-workitems will be deleted when the workitem is removed.
	 */
	public void onDelete(@Observes DocumentEvent documentEvent) {

		String type = documentEvent.getDocument().getType();

		if (documentEvent.getEventType() != DocumentEvent.ON_DOCUMENT_DELETE) {
			// skip
			return;
		}
		if (type.startsWith(TYPE_PRAFIX)) {
			// skip recursive call
			return;
		}

		// verify and delete deprecated $blobwWrkitem
		if (!documentEvent.getDocument().getItemValueString("$blobworkitem").isEmpty()) {
			// try to delete the blobWorkitem
			ItemCollection lobWorkitem = documentService
					.load(documentEvent.getDocument().getItemValueString("$blobworkitem"));
			if (lobWorkitem != null) {
				logger.info("delete deprecated blobworkitem: '"
						+ documentEvent.getDocument().getItemValueString("$blobworkitem") + "'");
				documentService.remove(lobWorkitem);
			}
		}

		// 1.) find all snapshots
		List<ItemCollection> snappshotList = findAllSnapshots(documentEvent.getDocument().getUniqueID());

		// 2.) delete all snapshots
		for (ItemCollection snapshot : snappshotList) {
			documentService.remove(snapshot);
		}

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
	 * This method returns the fileData from a snapshot by a given origin workItem
	 * uniqueid.
	 * 
	 * @param uniqueid
	 * @param file
	 *            - file name
	 * @return FileData object for the given filename.
	 */
	public FileData getWorkItemFile(String uniqueid, String file) {
		ItemCollection workItem;
		String snapshotID;

		// load workitem
		workItem = documentService.load(uniqueid);
		// test if we have a $snapshotid
		snapshotID = workItem.getItemValueString("$snapshotid");

		ItemCollection snapshot = documentService.load(snapshotID);
		if (snapshot != null) {
			return snapshot.getFileData(file);
		}

		return null;
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
				
				// test if exists in current transaction
				// see issue #78
				if (documentService.load(oldSnapshot.getUniqueID())!=null) {
					documentService.remove(oldSnapshot);	
				} else {
					logger.fine("......snapshot '" + oldSnapshot.getUniqueID() + "' can't be deleted in this transaction context.");
				}
			} catch (AccessDeniedException  e) {
				logger.warning("remove deprecated snapshot '" + oldSnapshot.getUniqueID() + "' failed, snapshot context='" + snapshotID + "' - failure: " + e.getMessage());
            }
			result.remove(0);
		}
	}

	/**
	 * This helper method copies the $files content from a source workitem (last
	 * snapshot) into a target workitem (current snapshot) if no content for the
	 * same file exists in the target workitem.
	 * <p>
	 * If 'overwriteFileContent' is set to 'false' than in case a file with the same
	 * name already exits, will be 'archived' with a time-stamp-sufix:
	 * <p>
	 * e.g.: 'ejb_obj.gif' => 'ejb_obj-1514410113556.gif'
	 * <p>
	 * This allows the 'versioning' of file content.
	 * 
	 * @param source
	 * @param target
	 */
	private void copyFilesFromItemCollection(ItemCollection source, ItemCollection target, ItemCollection origin,
			boolean overwriteFileContent, boolean blobWorkitem) {

		List<FileData> files = target.getFileData();

		for (FileData fileData : files) {
			String fileName = fileData.getName();
			// test if the content of the file is empty. In this case we copy
			// the content from the last snapshot (source)
			byte[] content = fileData.getContent();
			if (content.length == 0 || (blobWorkitem && content.length <= 2)) { // <= 2 migration issue from
																				// blob-workitem (size can be 1 byte)
				// fetch the old content from source...
				if (source != null) {
					FileData oldFileData = source.getFileData(fileName);
					if (oldFileData != null) {
						logger.fine("copy file content '" + fileName + "' from: " + source.getUniqueID());
						target.addFileData(new FileData(fileName, oldFileData.getContent(),
								oldFileData.getContentType(), oldFileData.getAttributes()));
					} else {
						// if the file data is a link/url we did not find content
						if (fileName.matches(REGEX_URL_PATTERN)) {
							// In case of an URL we do not need to copy the file content
							logger.fine("URL - no file content for " + fileName);
						} else {
							logger.warning("Missing file content!");
						}
					}
				} else {
					logger.warning("Missing file content!");
				}
			} else {
				// in case 'overwriteFileContent' is set to 'false' we protect existing content
				// of
				// files with the same name, but extend the name of the old file with a sufix
				if (!overwriteFileContent) {
					FileData oldFileData = source.getFileData(fileName);
					if (oldFileData != null) {
						// we need to sufix the last file with the same name here to protect the
						// content.
						ItemCollection dmsColOrigin = new ItemCollection(fileData.getAttributes());
						ItemCollection dmsColOld = new ItemCollection(oldFileData.getAttributes());
						// compare MD5Checksum
						if ((dmsColOrigin != null && dmsColOld != null)
								&& (!dmsColOrigin.getItemValueString(ITEM_MD5_CHECKSUM)
										.equals(dmsColOld.getItemValueString(ITEM_MD5_CHECKSUM)))) {

							// compute timestamp from $modifeid or $created date if available....
							// Note: $modified will be set by method updateCustomAttributes()
							Date fileDate = dmsColOld.getItemValueDate("$modified");
							if (fileDate == null) {
								fileDate = dmsColOld.getItemValueDate("$created");
							}
							if (fileDate == null) {
								// should not happen that the $created does not exist (just for backward
								// compatibility)
								fileDate = new Date();
							}
							TimeZone tz = TimeZone.getTimeZone("UTC");
							DateFormat df = new SimpleDateFormat("[yyyy-MM-dd'T'HH:mm:ss.SSS'Z']"); // Quoted "Z" to
																									// indicate UTC,
																									// no timezone
																									// offset
							df.setTimeZone(tz);
							// construct new unique filename....
							String sTimeStamp = df.format(fileDate);
							String protectedFileName = null;
							int iFileDot = fileName.lastIndexOf('.');
							if (iFileDot > 0) {
								protectedFileName = fileName.substring(0, iFileDot) + "-" + sTimeStamp
										+ fileName.substring(iFileDot);
							} else {
								protectedFileName = fileName + "-" + sTimeStamp;
							}

							// add filename with empty data to origin
							byte[] empty = {};
							dmsColOld.replaceItemValue("txtname", protectedFileName);
							target.addFileData(new FileData(protectedFileName, oldFileData.getContent(),
									oldFileData.getContentType(), dmsColOld.getAllItems()));
							origin.addFileData(new FileData(protectedFileName, empty, oldFileData.getContentType(),
									dmsColOld.getAllItems()));
						}
					}
				}
			}
		}

	}

	/**
	 * This method updates the property customAttributes for each file of the
	 * current workitem with the meta data of attached files or links.
	 * <p>
	 * In addition the method generates the items 'dms_count' and 'dms_names' with
	 * the number of attachments and a list of all filenames.
	 * <p>
	 * Also the item 'dms' is filled with all optional content stored in the custom
	 * Attributes. This allows a client to add for example OCR data to a workitem.
	 * 
	 * @param workitem
	 *            - target workitem
	 * @param username
	 *            - optional username
	 * @throws NoSuchAlgorithmException
	 * 
	 */
	private void updateCustomAttributes(ItemCollection workitem, String username) throws NoSuchAlgorithmException {

		List<FileData> currentFileData = workitem.getFileData();
		String customContent = "";
		// now we test for each file entry if a new content was uploaded....
		for (FileData fileData : currentFileData) {

			ItemCollection customAtributes = new ItemCollection(fileData.getAttributes());

			// if we have a content than we compute the MD5 checksum and the size of the
			// file
			if (fileData.getContent() != null && fileData.getContent().length > 1) {
				String oldChecksum = customAtributes.getItemValueString(ITEM_MD5_CHECKSUM);

				String newChecksum = fileData.generateMD5();
				customAtributes.replaceItemValue(ITEM_MD5_CHECKSUM, newChecksum);
				customAtributes.replaceItemValue("size", fileData.getContent().length);
				customAtributes.replaceItemValue("txtname", fileData.getName());

				// take the item §lastEventDate to mark the timestamp of creation or modified.  
				Date eventDate = workitem.getItemValueDate(WorkflowKernel.LASTEVENTDATE);
				if (eventDate == null) {
					// fall back if no $lastEventDate exists (for non-workflow documents)
					eventDate = new Date();
				}
				// verify if newChecksum has changed...
				if (!oldChecksum.isEmpty() && !oldChecksum.equals(newChecksum)) {
					// add modification data
					customAtributes.replaceItemValue("$modified", eventDate);
					customAtributes.replaceItemValue("$editor", username);
				} else {

					customAtributes.replaceItemValue("$created", eventDate);
					customAtributes.replaceItemValue("$creator", username);
					customAtributes.replaceItemValue("namcreator", username);
				}
				// update custom attributes....
				fileData.setAttributes(customAtributes.getAllItems());
				workitem.addFileData(fileData);

			}
			// collect all optional custom content (need to be provided by client)
			customContent = customContent + customAtributes.getItemValueString("content");
			// add a lucene word break here!
			customContent = customContent + " ";
		}

		// add $filecount
		workitem.replaceItemValue(ITEM_DMS_FILE_COUNT, workitem.getFileNames().size());
		// add $filenames
		workitem.replaceItemValue(ITEM_DMS_FILE_NAMES, workitem.getFileNames());
		// add custom content to be indexed by lucene
		workitem.replaceItemValue(ITEM_DMS, customContent);
	}

}