package org.imixs.archive.util;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.export.ExportApi;
import org.imixs.melman.BasicAuthenticator;
import org.imixs.melman.CookieAuthenticator;
import org.imixs.melman.DocumentClient;
import org.imixs.melman.EventLogClient;
import org.imixs.melman.FormAuthenticator;
import org.imixs.melman.JWTAuthenticator;
import org.imixs.melman.RestAPIException;
import org.imixs.melman.WorkflowClient;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.Cookie;

/**
 * The RestClientHelper provides helper method to create a DocumentRestClient
 * and a EventLogRestClient.
 *
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class RestClientHelper implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    @ConfigProperty(name = ExportApi.WORKFLOW_SERVICE_ENDPOINT)
    Optional<String> instanceEndpoint;

    @Inject
    @ConfigProperty(name = ExportApi.WORKFLOW_SERVICE_USER)
    Optional<String> instanceUser;

    @Inject
    @ConfigProperty(name = ExportApi.WORKFLOW_SERVICE_PASSWORD)
    Optional<String> instancePassword;

    @Inject
    @ConfigProperty(name = ExportApi.WORKFLOW_SERVICE_AUTHMETHOD)
    Optional<String> instanceAuthmethod;

    /**
     * This method creates a new WorkflowClient instance.
     *
     * @return
     * @throws RestAPIException
     */
    public DocumentClient getDocumentClient() throws RestAPIException {

        DocumentClient documentClient = null;
        if (instanceEndpoint.isPresent()) {

            documentClient = new WorkflowClient(instanceEndpoint.get());
            String auttype = instanceAuthmethod.orElse("BASIC").toUpperCase();
            if ("BASIC".equals(auttype)) {
                // Create a authenticator
                BasicAuthenticator basicAuth = new BasicAuthenticator(instanceUser.orElse(""),
                        instancePassword.orElse(""));
                // register the authenticator
                documentClient.registerClientRequestFilter(basicAuth);
            }
            if ("FORM".equals(auttype)) {
                // Create a authenticator
                FormAuthenticator formAuth = new FormAuthenticator(instanceEndpoint.orElse(""), instanceUser.orElse(""),
                        instancePassword.orElse(""));
                // register the authenticator
                documentClient.registerClientRequestFilter(formAuth);

            }
            if ("COOKIE".equals(auttype)) {
                Cookie cookie = new Cookie(instanceUser.orElse(""), instancePassword.orElse(""));
                CookieAuthenticator cookieAuth = new CookieAuthenticator(cookie);
                documentClient.registerClientRequestFilter(cookieAuth);
            }
            if ("JWT".equalsIgnoreCase(instancePassword.orElse(""))) {
                JWTAuthenticator jwtAuht = new JWTAuthenticator(instancePassword.orElse(""));
                documentClient.registerClientRequestFilter(jwtAuht);
            }
        }

        return documentClient;

    }

    /**
     * Creates a EventLogClient form a given DocumentClient instance
     *
     * @param workflowClient - a existing worklfowClient
     * @return - a eventLogClient instance
     */
    public EventLogClient getEventLogClient(DocumentClient documentClient) {
        if (documentClient != null) {
            EventLogClient client = new EventLogClient(documentClient.getBaseURI());
            // register all filters from workfow client
            List<ClientRequestFilter> filterList = documentClient.getRequestFilterList();
            for (ClientRequestFilter filter : filterList) {
                client.registerClientRequestFilter(filter);
            }
            return client;
        } else {
            // no existing workflow client define!
            return null;
        }
    }

}