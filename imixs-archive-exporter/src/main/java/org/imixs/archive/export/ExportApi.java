package org.imixs.archive.export;

import java.io.ByteArrayOutputStream;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

@ApplicationPath("/api")
public class ExportApi extends Application {
    // rest service endpoint
    public static final String WORKFLOW_SERVICE_ENDPOINT = "workflow.service.endpoint";
    public static final String WORKFLOW_SERVICE_USER = "workflow.service.user";
    public static final String WORKFLOW_SERVICE_PASSWORD = "workflow.service.password";
    public static final String WORKFLOW_SERVICE_AUTHMETHOD = "workflow.service.authmethod";
    public static final String WORKFLOW_SYNC_INTERVAL = "workflow.sync.interval";
    public static final String WORKFLOW_SYNC_INITIALDELAY = "workflow.sync.initialdelay";
    public static final String WORKFLOW_SYNC_DEADLOCK = "workflow.sync.deadlock";

    public static final String ENV_EXPORT_FTP_HOST = "EXPORT_FTP_HOST";
    public static final String ENV_EXPORT_FTP_PATH = "EXPORT_FTP_PATH";
    public static final String ENV_EXPORT_FTP_PORT = "EXPORT_FTP_PORT";
    public static final String ENV_EXPORT_FTP_USER = "EXPORT_FTP_USER";
    public static final String ENV_EXPORT_FTP_PASSWORD = "EXPORT_FTP_PASSWORD";
    public static final String ENV_EXPORT_FILE_PATH = "EXPORT_FILE_PATH";

    public static final String EVENTLOG_TOPIC_EXPORT = "file.export";
    public static final String EXPORT_SYNC_DEADLOCK = "export.sync.deadlock";

    public final static String SNAPSHOT_RESOURCE = "snapshot/";

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

    /**
     * Converts a ItemCollection into a XMLDocument and returns the byte data.
     *
     * @param itemCol
     * @return
     * @throws ExportException
     */
    public static byte[] getRawData(ItemCollection itemCol) throws ExportException {
        byte[] data = null;
        // create byte array from XMLDocument...
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            JAXBContext context;
            context = JAXBContext.newInstance(XMLDocument.class);
            Marshaller m = context.createMarshaller();
            XMLDocument xmlDocument = XMLDocumentAdapter.getDocument(itemCol);
            m.marshal(xmlDocument, outputStream);
            data = outputStream.toByteArray();
        } catch (JAXBException e) {
            throw new ExportException(ExportException.INVALID_DOCUMENT_OBJECT, e.getMessage(), e);
        }

        return data;
    }
}
