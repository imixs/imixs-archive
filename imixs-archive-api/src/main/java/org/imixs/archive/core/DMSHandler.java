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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.logging.Logger;

import javax.xml.bind.DatatypeConverter;

import org.imixs.workflow.ItemCollection;

/**
 * The DMSHandler handles the item 'dms' which is holding meta information about
 * file attachments.
 * 
 * @version 1.0
 * @author rsoika
 */
public class DMSHandler {

	public final static String DMS_ITEM = "dms";
	public final static String DMS_FILE_NAMES = "dms_names"; // list of files
	public final static String DMS_FILE_COUNT = "dms_count"; // count of files
	public final static String CHECKSUM_ERROR = "CHECKSUM_ERROR";

	private static Logger logger = Logger.getLogger(DMSHandler.class.getName());

	/**
	 * This method updates the property 'dms' of the current workitem with the meta
	 * data of attached files or links.
	 * 
	 * This method creates new empty DMS entries in case the dms meta data for a
	 * file is missing.
	 * 
	 * 
	 * @param aWorkitem
	 * @param username
	 *            - optional username
	 * @throws NoSuchAlgorithmException
	 * 
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void updateDMSMetaData(ItemCollection workitem, String username) throws NoSuchAlgorithmException {
		// boolean updateBlob = false;

		List<Map> currentDmsList = workitem.getItemValue(DMS_ITEM);
		List<String> currentFileNames = workitem.getFileNames();
		Map<String, List<Object>> currentFileData = workitem.getFiles();

		// List<ItemCollection> currentDmsList = getDmsList(aWorkitem);

		// first we remove all DMS entries which did not have a matching
		// $File-Entry and are not from type link
		for (Iterator<Map> iterator = currentDmsList.iterator(); iterator.hasNext();) {
			Map dmsEntry = iterator.next();
			String sName = getStringValueFromMap(dmsEntry, "txtname");
			String sURL = getStringValueFromMap(dmsEntry, "url");
			if (sURL.isEmpty() && !currentFileNames.contains(sName)) {
				// Remove the current element from the iterator and the list.
				logger.fine("remove dms entry '" + sName + "'");
				iterator.remove();
			}
		}

		// now we test for each file entry if a dms meta data entry
		// exists. If not we create a new one...
		if (currentFileData != null) {
			for (Entry<String, List<Object>> entry : currentFileData.entrySet()) {
				String fileName = entry.getKey();
				List<?> fileData = entry.getValue();
				ItemCollection dmsEntry = getDMSEntry(fileName, workitem);

				if (dmsEntry == null) {
					dmsEntry = createDMSEntry(fileName, fileData, username);
					putDMSEntry(dmsEntry, workitem);
				} else {
					// dms entry exists. We update if new file content was added
					byte[] fileContent = (byte[]) fileData.get(1);
					if (fileContent != null && fileContent.length > 1) {
						String checksum = generateMD5(fileContent);
						if (!checksum.equals(dmsEntry.getItemValueString("md5checksum"))) {
							dmsEntry.replaceItemValue("md5checksum", checksum);
							dmsEntry.replaceItemValue("$created", new Date());
							dmsEntry.replaceItemValue("$editor", username);
							putDMSEntry(dmsEntry, workitem);
						}
					}

				}

			}
		}

		// add $filecount
		workitem.replaceItemValue(DMS_FILE_COUNT, workitem.getFileNames().size());
		// add $filenames
		workitem.replaceItemValue(DMS_FILE_NAMES, workitem.getFileNames());
	}

	/**
	 * This method returns the dms meta data of a specific file in the given
	 * workitem.
	 * 
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static ItemCollection getDMSEntry(String aFilename, ItemCollection workitem) {
		List<Map> vDMS = workitem.getItemValue(DMS_ITEM);
		// first we add all existing dms informations
		for (Map aMetadata : vDMS) {
			String sName = getStringValueFromMap(aMetadata, "txtname");
			if (aFilename.equals(sName)) {
				return new ItemCollection(aMetadata);
			}
		}
		// no matching meta data found!
		return null;
	}

	/**
	 * This method adds a single DMS Entry into the dms item of a given workitem.
	 * Old data will be replaced.
	 * 
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void putDMSEntry(ItemCollection dmsEntry, ItemCollection workitem) {
		List<Map> vDMS = workitem.getItemValue(DMS_ITEM);
		String filename = dmsEntry.getItemValueString("txtname");
		// remove the old entry
		List<Map> currentDmsList = workitem.getItemValue(DMS_ITEM);
		for (Iterator<Map> iterator = currentDmsList.iterator(); iterator.hasNext();) {
			Map dmsmap = iterator.next();
			String sName = getStringValueFromMap(dmsmap, "txtname");
			if (filename.equals(sName)) {
				iterator.remove();
			}
		}
		// add new
		vDMS.add(dmsEntry.getAllItems());
	}

	/**
	 * 
	 * This method creates a new DMS Meta entry. The method also generates a MD5
	 * checksum if a file content exists. .
	 * 
	 * @param fileName
	 * @param fileData
	 * @param username
	 * @return
	 * @throws NoSuchAlgorithmException
	 */
	private static ItemCollection createDMSEntry(String fileName, List<?> fileData, String username)
			throws NoSuchAlgorithmException {
		// no meta data exists.... create a new meta object
		ItemCollection dmsEntry = new ItemCollection();
		dmsEntry.replaceItemValue("txtname", fileName);
		dmsEntry.replaceItemValue("$created", new Date());
		dmsEntry.replaceItemValue("namcreator", username);// deprecated
		dmsEntry.replaceItemValue("$creator", username);
		dmsEntry.replaceItemValue("txtcomment", "");
		// compute md5 checksum
		byte[] fileContent = (byte[]) fileData.get(1);
		dmsEntry.replaceItemValue("md5checksum", generateMD5(fileContent));

		return dmsEntry;
	}

	/**
	 * Generates a MD5 from a byte array
	 * 
	 * @param b
	 * @return
	 * @throws NoSuchAlgorithmException
	 */
	private static String generateMD5(byte[] b) throws NoSuchAlgorithmException {
		byte[] hash_bytes = MessageDigest.getInstance("MD5").digest(b);
		return DatatypeConverter.printHexBinary(hash_bytes);
	}

	/**
	 * This is a helper method to check a string value of a map. This avoids the
	 * need to convert the map first into a ItemCollection which is performance
	 * critical.
	 * 
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "unused" })
	private static String getStringValueFromMap(Map<String, List<Object>> hash, String aName) {

		List<Object> v = null;

		if (aName == null) {
			return null;
		}
		aName = aName.toLowerCase().trim();

		Object obj = hash.get(aName);
		if (obj==null) {
			return "";
		}
		
		if (obj instanceof List) {

			List<Object> oList = (List<Object>)obj;
			if (oList == null)
				v = new Vector<Object>();
			else {
				v = oList;
				// scan vector for null values
				for (int i = 0; i < v.size(); i++) {
					if (v.get(i) == null)
						v.remove(i);
				}
			}

			if (v.size() == 0)
				return "";
			else {
				// verify if value is null
				Object o = v.get(0);
				if (o == null)
					return "";
				else
					return o.toString();
			}
		} else {
			//Value is not a list!
			logger.warning("getStringValueFromMap - wrong value object found '" + aName +"'");
			return obj.toString();
		}
	}

}