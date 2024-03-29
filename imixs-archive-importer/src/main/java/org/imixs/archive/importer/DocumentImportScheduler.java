/*******************************************************************************
 *  Imixs Workflow Technology
 *  Copyright (C) 2001, 2008 Imixs Software Solutions GmbH,  
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
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika
 *******************************************************************************/
package org.imixs.archive.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.scheduler.Scheduler;
import org.imixs.workflow.engine.scheduler.SchedulerException;
import org.imixs.workflow.engine.scheduler.SchedulerService;
import org.imixs.workflow.exceptions.QueryException;

import jakarta.ejb.EJB;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

/**
 * The DocumentImportScheduler iterates over all source definition and sends a
 * DocumentImportEvent to process the source by an external service
 * implementation.
 * 
 * @see SchedulerService
 * @author rsoika
 * 
 */
public class DocumentImportScheduler implements Scheduler {

    public static final String DOCUMENT_IMPORTER_NAME = "DOCUMENT_IMPORTER";
  
    @EJB
    DocumentImportService documentImportService;
    
    @Inject
    protected Event<DocumentImportEvent> importEvents;

    private static Logger logger = Logger.getLogger(DocumentImportScheduler.class.getName());

    /**
     * This method iterates over all source definitions defined by the scheduler
     * configuration. For each source a DocumentImportEvent is send. An observer can
     * process the source. If a event returns PROCESSING_RESULT_COMPLETED = 1 than
     * no more events will be fired for this source.
     * 
     * 
     * @param timer
     * @throws SchedulerException
     * @throws QueryException
     */
    @SuppressWarnings("rawtypes")
    public ItemCollection run(ItemCollection configuration) throws SchedulerException {

        if (importEvents != null) {
            // load all sources
            List<ItemCollection> sources = documentImportService.loadSourcesFromConfiguration(configuration);
            if (sources.size() > 0) {
                documentImportService.logMessage("Document import starting - " + sources.size() + " sources found...", configuration);
                for (ItemCollection source : sources) {
                    // Finally fire the DocumentImportEvent. This allows CDI Observers to process
                    // the import
                    DocumentImportEvent importEvent = new DocumentImportEvent(source);
                    importEvents.fire(importEvent);
                    // append all messages....
                    configuration.appendItemValue(Scheduler.ITEM_LOGMESSAGE, importEvent.getMessages());
                    if (importEvent.getResult() == DocumentImportEvent.PROCESSING_ERROR) {
                        logger.severe("...Document Import Error");
                    }
                }
                
                // update sources (a CDI bean may have added new data....)
                // convert the option ItemCollection elements into a List of Map
                List<Map> mapItemList = new ArrayList<Map>();
                logger.fine("Convert option items into Map...");
                for (ItemCollection orderItem : sources) {
                    mapItemList.add(orderItem.getAllItems());
                }
                configuration.replaceItemValue(DocumentImportService.ITEM_SOURCES, mapItemList);
            
                
                documentImportService.logMessage("Document import completed.", configuration);
            } else {
                documentImportService.logMessage("No sources defined.", configuration);
                return configuration;
            }
        }
        return configuration;
    }

   
}
