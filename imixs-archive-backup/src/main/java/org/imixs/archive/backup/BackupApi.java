package org.imixs.archive.backup;

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
public class BackupApi extends Application {

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
     * @throws BackupException
     */
    public static byte[] getRawData(ItemCollection itemCol) throws BackupException {
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
            throw new BackupException(BackupException.INVALID_DOCUMENT_OBJECT, e.getMessage(), e);
        }

        return data;
    }
}
