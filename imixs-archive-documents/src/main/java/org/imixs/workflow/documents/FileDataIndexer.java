/*  
 *  Imixs-Workflow 
 *  
 *  Copyright (C) 2001-2020 Imixs Software Solutions GmbH,  
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
 *      https://www.imixs.org
 *      https://github.com/imixs/imixs-workflow
 *  
 *  Contributors:  
 *      Imixs Software Solutions GmbH - Project Management
 *      Ralph Soika - Software Developer
 */

package org.imixs.workflow.documents;

import java.util.List;
import java.util.logging.Logger;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Observes;

import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.index.IndexEvent;

/**
 * The FileDataIndexer is a CDI bean reacting on IndexEvents and extends the
 * text content of a document with the optional 'content' attribute of attached
 * files. The additional text content to be indexed is expected in the custom
 * attribute 'content' for each fileData object.
 * 
 * @author rsoika
 */
@RequestScoped 
public class FileDataIndexer {
    private static Logger logger = Logger.getLogger(FileDataIndexer.class.getName());

    /**
     * Update the textContent based on the optional content attribute of the
     * FileData.
     * 
     * @param event - the index event
     */
    public void onEvent(@Observes IndexEvent event) {
        List<FileData> currentFileData = event.getDocument().getFileData();
        if (currentFileData == null || currentFileData.size() == 0) {
            // no changes
            return;
        }

        String textContent = event.getTextContent() + " ";
        // now we test for each file entry if a new content was uploaded....
        for (FileData fileData : currentFileData) {
            logger.finest("......add text content for file " + fileData.getName());

            ItemCollection customAtributes = new ItemCollection(fileData.getAttributes());
            // collect all optional custom content (need to be provided by client)
            textContent = textContent + customAtributes.getItemValueString("text");
            // add a lucene word break here!
            textContent = textContent + " ";
        }

        // now we update the textContent.....
        event.setTextContent(textContent);

    }
}
