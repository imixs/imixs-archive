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
package org.imixs.archive.backup;

import java.util.Date;

import jakarta.ejb.Singleton;
import jakarta.ejb.Timer;

/**
 * The RestoreStatusHandler provides a non blocking way to control the status
 * flag for the RestoreService.
 *
 * @version 1.0
 * @author rsoika
 */
@Singleton
public class FullBackupStatusHandler {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_STOPPED = "STOPPED";
    public static final String STATUS_CANCELED = "CANCELED";

    private String status = "";
    private Timer timer = null;
    private Date syncPoint = null;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timer getTimer() {
        return timer;
    }

    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    public Date getSyncPoint() {
        return syncPoint;
    }

    public void setSyncPoint(Date syncPoint) {
        this.syncPoint = syncPoint;
    }

    /**
     * Returns the next timeout date for the current timer
     */
    public Date getNextTimeout() {
        if (timer != null) {
            try {
                // load current timer details
                return timer.getNextTimeout();
            } catch (Exception e) {
                // timer canceled
            }
        }
        return null;
    }

}
