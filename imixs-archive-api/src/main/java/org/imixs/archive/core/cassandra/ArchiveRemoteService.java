package org.imixs.archive.core.cassandra;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.core.SnapshotService;
import org.imixs.melman.BasicAuthenticator;
import org.imixs.melman.DocumentClient;
import org.imixs.melman.FormAuthenticator;
import org.imixs.melman.RestAPIException;
import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;

/**
 * The ArchiveRemoteService pushes a Snapshot into a remote cassandra archive.
 * The service uses an asynchronious mechanism based on the Imixs EventLog. The
 * service also provides a method to load a file from the remote archvie.
 * <p>
 * The service connects to the Imixs-Archive Service by a Rest Client to push
 * and read data.
 * <p>
 * The service is triggered by the ArchivePushService on a scheduled basis.
 * 
 * @version 1.1
 * @author ralph.soika@imixs.com
 */
@Stateless
@LocalBean
public class ArchiveRemoteService {

    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_ENDPOINT, defaultValue = "")
    String archiveServiceEndpoint;

    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_USER, defaultValue = "")
    String archiveServiceUser;

    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_PASSWORD, defaultValue = "")
    String archiveServicePassword;

    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_AUTHMETHOD, defaultValue = "")
    String archiveServiceAuthMethod;

    private static Logger logger = Logger.getLogger(ArchiveRemoteService.class.getName());

    /**
     * This method loads the file content for a given md5 checksum directly from the
     * cassandra archive using the resource
     * <p>
     * <code>/archive/md5/{md5}</code>
     * <p>
     * To activate this mechansim the environment variable ARCHIVE_SERVICE_ENDPOINT
     * must be set to a valid endpoint.
     * 
     * @param fileData - fileData object providing the MD5 checksum
     * @throws RestAPIException
     */
    public byte[] loadFileFromArchive(FileData fileData) throws RestAPIException {

        if (fileData == null) {
            return null;
        }

        boolean debug = logger.isLoggable(Level.FINE);

        // first we lookup the FileData object
        if (fileData != null) {
            ItemCollection dmsData = new ItemCollection(fileData.getAttributes());
            String md5 = dmsData.getItemValueString(SnapshotService.ITEM_MD5_CHECKSUM);

            if (!md5.isEmpty()) {

                try {
                    DocumentClient documentClient = initDocumentClient();
                    Client rsClient = documentClient.newClient();
                    String url = archiveServiceEndpoint + "/archive/md5/" + md5;
                    Response reponse = rsClient.target(url).request(MediaType.APPLICATION_OCTET_STREAM).get();

                    // InputStream is = reponse.readEntity(InputStream.class);
                    byte[] fileContent = reponse.readEntity(byte[].class);
                    if (fileContent != null && fileContent.length > 0) {
                        if (debug) {
                            logger.finest("......md5 data object found");
                        }
                        return fileContent;
                    }

                } catch (ProcessingException e) {
                    String message = null;
                    if (e.getCause() != null) {
                        message = e.getCause().getMessage();
                    } else {
                        message = e.getMessage();
                    }
                    throw new RestAPIException(DocumentClient.class.getSimpleName(),
                            RestAPIException.RESPONSE_PROCESSING_EXCEPTION,
                            "error load file by MD5 checksum -> " + message, e);
                }
            } else {
                return null;
            }

        }

        return null;

    }

    /**
     * Helper method to initalize a Melman Workflow Client based on the current
     * archive configuration.
     */
    public DocumentClient initDocumentClient() {
        boolean debug = logger.isLoggable(Level.FINE);
        if (debug) {
            logger.finest("...... ARCHIVE_SERVICE_ENDPOINT = " + archiveServiceEndpoint);
        }
        DocumentClient documentClient = new DocumentClient(archiveServiceEndpoint);
        // Test authentication method
        if ("Form".equalsIgnoreCase(archiveServiceAuthMethod)) {
            // default basic authenticator
            FormAuthenticator formAuth = new FormAuthenticator(archiveServiceEndpoint, archiveServiceUser,
                    archiveServicePassword);
            // register the authenticator
            documentClient.registerClientRequestFilter(formAuth);
        } else {
            // default basic authenticator
            BasicAuthenticator basicAuth = new BasicAuthenticator(archiveServiceUser, archiveServicePassword);
            // register the authenticator
            documentClient.registerClientRequestFilter(basicAuth);
        }
        return documentClient;
    }
}
