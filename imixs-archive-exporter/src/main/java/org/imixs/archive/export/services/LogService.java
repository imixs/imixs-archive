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
package org.imixs.archive.export.services;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import jakarta.ejb.Singleton;

/**
 * The LogService is a singleton class to store log entries in a local log. The
 * LogService is used by the backend services to generate log entries and by the
 * frontend controller to display log entries.
 *
 * @version 1.0
 * @author rsoika
 */

@Singleton
public class LogService {

    private static Logger logger = Logger.getLogger(LogService.class.getName());

    private int maxSize = 30;

    private List<String> logTopics;
    public static final int LOG_INFO = 1;
    public static final int LOG_WARNING = 2;
    public static final int LOG_ERROR = 3;

    /**
     * Logs a new message to the message log
     *
     * @param message
     */
    private void add(int type, String message) {
        String pattern = " HH:mm:ss.SSSZ";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);

        // get the logger

        if (logTopics == null) {
            logTopics = new ArrayList<String>();
        }

        // check maxsize...
        while (logTopics.size() > maxSize) {
            logTopics.remove(0);
        }

        String entry = simpleDateFormat.format(new Date()) + " ";
        if (type == LOG_ERROR) {
            entry = entry + "[ERROR] ";
            logger.severe(message);
        } else if (type == LOG_WARNING) {
            entry = entry + "[WARNING] ";
            logger.warning(message);
        } else {
            entry = entry + "[INFO]    ";
            logger.info(message);

        }
        entry = entry + message;
        logTopics.add(entry);
    }

    public List<String> getLogEntries() {
        return logTopics;
    }

    public void info(String message) {
        add(LOG_INFO, message);
    }

    public void warning(String message) {
        add(LOG_WARNING, message);
    }

    public void severe(String message) {
        add(LOG_ERROR, message);
    }
}
