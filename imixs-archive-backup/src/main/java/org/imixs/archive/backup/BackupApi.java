package org.imixs.archive.backup;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/api")
public class BackupApi extends Application {
    // rest service endpoint
    public static final String WORKFLOW_SERVICE_ENDPOINT = "workflow.service.endpoint";
    public static final String WORKFLOW_SERVICE_USER = "workflow.service.user";
    public static final String WORKFLOW_SERVICE_PASSWORD = "workflow.service.password";
    public static final String WORKFLOW_SERVICE_AUTHMETHOD = "workflow.service.authmethod";
    public static final String WORKFLOW_SYNC_INTERVAL = "workflow.sync.interval";
    public static final String WORKFLOW_SYNC_INITIALDELAY = "workflow.sync.initialdelay";
    public static final String WORKFLOW_SYNC_DEADLOCK = "workflow.sync.deadlock";

    public final static String DEFAULT_SCHEDULER_DEFINITION = "hour=*";

    public static final String ENV_BACKUP_SCHEDULER_DEFINITION = "BACKUP_SCHEDULER_DEFINITION";

    public static final String ENV_BACKUP_FTP_HOST = "BACKUP_FTP_HOST";
    public static final String ENV_BACKUP_FTP_PATH = "BACKUP_FTP_PATH";
    public static final String ENV_BACKUP_FTP_PORT = "BACKUP_FTP_PORT";
    public static final String ENV_BACKUP_FTP_USER = "BACKUP_FTP_USER";
    public static final String ENV_BACKUP_FTP_PASSWORD = "BACKUP_FTP_PASSWORD";

    public static final String EVENTLOG_TOPIC_BACKUP = "snapshot.backup";
    public static final String BACKUP_SYNC_DEADLOCK = "backup.sync.deadlock";
}
