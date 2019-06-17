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
package org.imixs.archive.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;

/**
 * This service provides a message log which can be used to monitor the timer
 * status. With the method logMessage() a message can be added to a given topic.
 * The Method getMessages() returns all messages for a topic.
 * <p>
 * The MAX_COUNT specifies the maximum count of messages stored in one message
 * log.
 * 
 * @author rsoika
 * @version 2.0
 */

@Singleton
public class MessageService {

	private final static int MAX_COUNT = 32;

	private Map<String, List<String>> messageLog;

	// private List<String> messages;

	private static Logger logger = Logger.getLogger(MessageService.class.getName());

	/**
	 * PostContruct event - loads the imixs.properties.
	 */
	@PostConstruct
	void init() {
		// initialize cache...
		messageLog = new HashMap<String, List<String>>();
		// messages=new ArrayList<String>();
	}

	public List<String> getMessages(String topic) {
		List<String> messages = messageLog.get(topic);
		if (messages == null) {
			messages = new ArrayList<String>();
		}
		return messages;
	}

	

	/**
	 * logs a new message into the message log
	 * 
	 * @param message
	 */
	public void logMessage(String topic,String message) {
		logger.info(topic + " => " + message);
		SimpleDateFormat dateFormatDE = new SimpleDateFormat("dd.MM.yy hh:mm:ss");
		message = dateFormatDE.format(new Date()) + " : " + message;
		List<String> messages=getMessages(topic);
		messages.add(message);
		// cut if max_count exeeded
		while (messages.size() > MAX_COUNT) {
			messages.remove(0);
		}
		// put message log
		messageLog.put(topic, messages);
	}

	/**
	 * Computes the file size into a user friendly format
	 * 
	 * @param size
	 * @return
	 */
	public String userFriendlyBytes(long bytes) {
		boolean si = true;
		int unit = si ? 1000 : 1024;
		if (bytes < unit)
			return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

}
