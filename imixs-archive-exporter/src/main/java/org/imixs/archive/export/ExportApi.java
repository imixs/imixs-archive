package org.imixs.archive.export;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * The rest service API endpoint
 */
@ApplicationPath("/api")
public class ExportApi extends Application {

    public static final String WORKFLOW_SERVICE_ENDPOINT = "workflow.service.endpoint";
    public static final String WORKFLOW_SERVICE_USER = "workflow.service.user";
    public static final String WORKFLOW_SERVICE_PASSWORD = "workflow.service.password";
    public static final String WORKFLOW_SERVICE_AUTHMETHOD = "workflow.service.authmethod";
    public static final String WORKFLOW_SYNC_INTERVAL = "workflow.sync.interval";
    public static final String WORKFLOW_SYNC_INITIALDELAY = "workflow.sync.initialdelay";
    public static final String WORKFLOW_SYNC_DEADLOCK = "workflow.sync.deadlock";

    public static final String EVENTLOG_TOPIC = "eventlog.topic";
    public static final String EVENTLOG_DEADLOCK = "eventlog.deadlock";

    public static final String EXPORT_PATH = "export.path";

    public static final String EXPORT_FTP_HOST = "export.ftp.host";
    public static final String EXPORT_FTP_PORT = "export.ftp.port";
    public static final String EXPORT_FTP_USER = "export.ftp.user";
    public static final String EXPORT_FTP_PASSWORD = "export.ftp.password";

    /**
     * Returns the $uniqueID from a $SnapshotID
     *
     * @param snapshotID
     * @return $uniqueid
     */
    public static String getUniqueIDFromSnapshotID(String snapshotID) {
        if (snapshotID != null && snapshotID.contains("-")) {
            return snapshotID.substring(0, snapshotID.lastIndexOf("-"));
        }
        return null;

    }

}
