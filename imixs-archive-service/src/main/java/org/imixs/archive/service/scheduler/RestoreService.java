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
package org.imixs.archive.service.scheduler;

import java.util.Date;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.MessageService;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.archive.service.cassandra.DataService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.QueryException;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;

/**
 * The RestoreService restores the workflow data stored in the cassandra cluster
 * into the workflow system. The service class runns a TimerService based on the
 * given scheduler configuration.
 * <p>
 * The scheduler configuration contains a timeout intervall
 * 
 * 
 * 
 * @version 1.0
 * @author rsoika
 */

@Stateless
public class RestoreService {

	public final static String TIMER_ID = "IMIXS_ARCHIVE_RESTORE_TIMER";
	public final static long TIMER_INTERVAL_DURATION = 60000;

	private static Logger logger = Logger.getLogger(RestoreService.class.getName());

	@EJB
	DataService dataService;

	@EJB
	ClusterService clusterService;

	@EJB
	MessageService messageService;

	@Resource
	javax.ejb.TimerService timerService;

	/**
	 * Starts a new Timer for the scheduler defined by the Configuration.
	 * <p>
	 * The Timer can be started based on a Calendar setting stored in the property
	 * _scheduler_definition.
	 * <p>
	 * The item 'keyspace' of the configuration entity is the id of the timer to be
	 * controlled.
	 * <p>
	 * The method throws an exception if the configuration entity contains invalid
	 * attributes or values.
	 * <p>
	 * After the timer was started the configuration is updated with the latest
	 * statusmessage. The item _schedueler_enabled will be set to 'true'.
	 * <p>
	 * The method returns the updated configuration. The configuration will not be
	 * saved!
	 * 
	 * @param configuration
	 *            - scheduler configuration
	 * @return updated configuration
	 * @throws ArchiveException
	 */
	public void start(Date datFrom, Date datTo) throws ArchiveException {
		Timer timer = null;

		// try to cancel an existing timer for this workflowinstance
		timer = findTimer(TIMER_ID);
		if (timer != null) {
			try {
				timer.cancel();
				timer = null;
			} catch (Exception e) {
				messageService.logMessage("Failed to stop existing timer - " + e.getMessage());
				throw new ArchiveException(SyncService.class.getName(), ArchiveException.INVALID_WORKITEM,
						" failed to cancle existing timer!");
			}
		}

		logger.info("...starting scheduler service " + TIMER_ID + " ...");
		// New timer will be started imediatly
		ItemCollection timerInfo = new ItemCollection();
		timerInfo.setItemValue("syncpoint.from", datFrom);
		timerInfo.setItemValue("syncpoint.to", datTo);
		timer = timerService.createTimer(new Date(), TIMER_INTERVAL_DURATION,
				XMLDocumentAdapter.getDocument(timerInfo));

		// start and set statusmessage
		if (timer != null) {
			messageService.logMessage("Timer started.");
		}

	}

	/**
	 * This is the method which processes the timeout event depending on the running
	 * timer settings.
	 * <p>
	 * The method reads MAX_COUNT snapshot workitems to be restored into a imixs
	 * workflow instance.
	 * 
	 * 
	 * @param timer
	 * @throws Exception
	 * @throws QueryException
	 */
	@Timeout
	void onTimeout(javax.ejb.Timer timer) throws Exception {
		
		// get config info
		XMLDocument xmlDocument=(XMLDocument) timer.getInfo();
		ItemCollection config=XMLDocumentAdapter.putDocument(xmlDocument);
		
		// TODO ....
		
		
		// update timer info
		

	}

	/**
	 * This method returns a timer for a corresponding id if such a timer object
	 * exists.
	 * 
	 * @param id
	 * @return Timer
	 * @throws Exception
	 */
	private Timer findTimer(String id) {
		for (Object obj : timerService.getTimers()) {
			Timer timer = (javax.ejb.Timer) obj;
			if (id.equals(timer.getInfo())) {
				return timer;
			}
		}
		return null;
	}

}
