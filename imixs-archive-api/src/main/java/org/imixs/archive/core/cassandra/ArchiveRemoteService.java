package org.imixs.archive.core.cassandra;

import java.util.Optional;
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
 * The ArchiveRemoteService provides a method to load a file form a remote
 * cassandra archive.
 * <p>
 * The service is called by Imixs-Office-Workflow to load documents from the
 * cassandra archive.
 * 
 * @version 1.2
 * @author ralph.soika@imixs.com
 */
@Stateless
@LocalBean
public class ArchiveRemoteService {

    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_ENDPOINT)
    Optional<String> archiveServiceEndpoint;

    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_USER)
    Optional<String> archiveServiceUser;

    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_PASSWORD)
    Optional<String> archiveServicePassword;

    @Inject
    @ConfigProperty(name = SnapshotService.ARCHIVE_SERVICE_AUTHMETHOD)
    Optional<String> archiveServiceAuthMethod;

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

        if (!archiveServiceEndpoint.isPresent() || archiveServiceEndpoint.get().isEmpty()) {
            logger.warning("missing archive service endpoint - verify configuration!");
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
                    if (documentClient == null) {
                        logger.warning("Unable to initalize document client!");
                    } else {
                        Client rsClient = documentClient.newClient();
                        String url = archiveServiceEndpoint.get() + "/archive/md5/" + md5;
                        Response response = null;
                        byte[] fileContent = null;
                        try {
                            response = rsClient.target(url).request(MediaType.APPLICATION_OCTET_STREAM).get();
                            // verify response code
                            if (response.getStatus() >= 200 && response.getStatus() <= 299) {
                                fileContent = response.readEntity(byte[].class);
                                if (fileContent != null && fileContent.length > 0) {
                                    if (debug) {
                                        logger.finest("......md5 data object found");
                                    }
                                }
                            }
                        } finally {
                            // explicit close client!
                            if (response != null) {
                                response.close();
                            }
                            rsClient.close();

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
    private DocumentClient initDocumentClient() {
        boolean debug = logger.isLoggable(Level.FINE);
        DocumentClient documentClient = new DocumentClient(archiveServiceEndpoint.get());
        // test if authentication is needed?
        if (archiveServiceAuthMethod.isPresent()) {
            // Test authentication method
            if ("form".equalsIgnoreCase(archiveServiceAuthMethod.get())) {
                if (debug) {
                    logger.finest("......Form Based authentication");
                }
                // form authenticator
                FormAuthenticator formAuth = new FormAuthenticator(archiveServiceEndpoint.get(),
                        archiveServiceUser.get(), archiveServicePassword.get());
                // register the authenticator
                documentClient.registerClientRequestFilter(formAuth);
            }
            if ("basic".equalsIgnoreCase(archiveServiceAuthMethod.get())) {
                if (debug) {
                    logger.finest("......Basic authentication");
                }
                // basic authenticator
                BasicAuthenticator basicAuth = new BasicAuthenticator(archiveServiceUser.get(),
                        archiveServicePassword.get());
                // register the authenticator
                documentClient.registerClientRequestFilter(basicAuth);
            }
        }

        return documentClient;
    }
}
