/*******************************************************************************
 *  Imixs Workflow 
 *  Copyright (C) 2001, 2011 Imixs Software Solutions GmbH,  
 *  http://www.imixs.com
 *  
 *  This program is free software; you can redistribute it and/or 
 *  modify it under the terms of the GNU General Public License 
 *  as published by the Free Software Foundation; either version 2 
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful, 
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 *  General Public License for more details.
 *  
 *  You can receive a copy of the GNU General Public
 *  License at http://www.gnu.org/licenses/gpl.html
 *  
 *  Project: 
 *  	http://www.imixs.org
 *  	http://java.net/projects/imixs-workflow
 *  
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika - Software Developer
 *******************************************************************************/

package org.imixs.archive.plugins;

import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import org.imixs.archive.core.SnapshotService;
import org.imixs.workflow.ItemCollection;

/**
 * This plugin extends the behavior of the Imixs-Engine Split&Join Plugin. The
 * plugin can copy file contend specified by the item '$file' from a snapshot
 * into a target workitem.
 * 
 * @author Ralph Soika
 * @version 1.0
 * @see http://www.imixs.org/doc/engine/plugins/splitandjoinplugin.html
 * 
 */
public class SplitAndJoinPlugin extends org.imixs.workflow.engine.plugins.SplitAndJoinPlugin {

	private static Logger logger = Logger.getLogger(SplitAndJoinPlugin.class.getName());

	/**
	 * This Method copies the fields defined in 'items' into the targetWorkitem.
	 * 
	 * The method overrides the behavior of the origin method and copies also
	 * the file content of files stored in the item "$file" form the
	 * corresponding snapshotItem.
	 * 
	 */
	@Override
	protected void copyItemList(String items, ItemCollection source, ItemCollection target) {

		// call default behavior
		super.copyItemList(items, source, target);
		StringTokenizer st = new StringTokenizer(items, ",");
		while (st.hasMoreTokens()) {
			if (st.nextToken().toLowerCase().trim().equals("$file")) {

				logger.finest("......copy $file content fromsnapshot...");
				// load source snapshot
				String snapshotID = source.getItemValueString(SnapshotService.SNAPSHOTID);
				ItemCollection sourceSnapshot = this.getWorkflowService().getWorkItem(snapshotID);
				if (sourceSnapshot != null) {
					Map<String, List<Object>> files = sourceSnapshot.getFiles();
					if (files != null) {
						for (String filename : files.keySet()) {
							target.addFileData(sourceSnapshot.getFileData(filename));
						}
					}
				} else {
					logger.warning("unable to load snapshot workitem '" + snapshotID + "'. Can't copy $file content!");
				}
			}
		}

	}

}
