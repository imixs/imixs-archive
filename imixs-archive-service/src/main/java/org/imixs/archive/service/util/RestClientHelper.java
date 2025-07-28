package org.imixs.archive.service.util;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.service.SyncService;
import org.imixs.melman.BasicAuthenticator;
import org.imixs.melman.CookieAuthenticator;
import org.imixs.melman.DocumentClient;
import org.imixs.melman.EventLogClient;
import org.imixs.melman.FormAuthenticator;
import org.imixs.melman.JWTAuthenticator;
import org.imixs.melman.RestAPIException;
import org.imixs.melman.WorkflowClient;

import jakarta.enterprise.context.ApplicationScoped;
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
@ApplicationScoped
public class RestClientHelper implements Serializable {

    private static Logger logger = Logger.getLogger(RestClientHelper.class.getName());

    private static final long serialVersionUID = 1L;

    @Inject
    @ConfigProperty(name = SyncService.ENV_WORKFLOW_SERVICE_ENDPOINT)
    Optional<String> instanceEndpoint;

    @Inject
    @ConfigProperty(name = SyncService.ENV_WORKFLOW_SERVICE_USER)
    Optional<String> instanceUser;

    @Inject
    @ConfigProperty(name = SyncService.ENV_WORKFLOW_SERVICE_PASSWORD)
    Optional<String> instancePassword;

    @Inject
    @ConfigProperty(name = SyncService.ENV_WORKFLOW_SERVICE_AUTHMETHOD)
    Optional<String> instanceAuthmethod;

    DocumentClient documentClient = null;
    EventLogClient eventLogClient = null;

    /**
     * This method creates a new DocumentClient instance.
     *
     * If an instance already exists, we return the existing instance.
     *
     * @return
     * @throws RestAPIException
     */
    public DocumentClient createDocumentClient() throws RestAPIException {

        // test if we have already an instance
        if (documentClient != null) {
            return documentClient;
        }
        logger.info("├── RestClientHelper create DocumentClient....");
        if (instanceEndpoint.isPresent()) {

            documentClient = new WorkflowClient(instanceEndpoint.get());
            String auttype = instanceAuthmethod.orElse("BASIC").toUpperCase();
            logger.info("│   ├── auth type=" + auttype);
            if ("BASIC".equals(auttype)) {
                BasicAuthenticator basicAuth = new BasicAuthenticator(instanceUser.orElse(""),
                        instancePassword.orElse(""));
                documentClient.registerClientRequestFilter(basicAuth);
            }

            if ("FORM".equals(auttype)) {
                logger.info("RestClientHelper create FormAuthenticator.... instance endpoint="
                        + instanceEndpoint.orElse(""));
                FormAuthenticator formAuth = new FormAuthenticator(instanceEndpoint.orElse(""), instanceUser.orElse(""),
                        instancePassword.orElse(""));
                documentClient.registerClientRequestFilter(formAuth);
            }

            if ("COOKIE".equals(auttype)) {
                Cookie cookie = new Cookie.Builder(instanceUser.orElse("")).path("/").value(instancePassword.orElse(""))
                        .build();
                CookieAuthenticator cookieAuth = new CookieAuthenticator(cookie);
                documentClient.registerClientRequestFilter(cookieAuth);
            }

            if ("JWT".equalsIgnoreCase(instancePassword.orElse(""))) {
                JWTAuthenticator jwtAuth = new JWTAuthenticator(instancePassword.orElse(""));
                documentClient.registerClientRequestFilter(jwtAuth);
            }

            if ("KEYCLOAK".equalsIgnoreCase(auttype)) {
                KeycloakAuthenticator keycloakAuth = new KeycloakAuthenticator(instanceEndpoint.orElse(""),
                        instanceUser.orElse(""),
                        instancePassword.orElse(""));
                documentClient.registerClientRequestFilter(keycloakAuth);
            }
        }

        return documentClient;

    }

    /**
     * Creates a EventLogClient form a given DocumentClient instance
     *
     * If an instance already exists, we return the existing instance.
     *
     * @param documentClient - a existing documentClient
     * @return - a eventLogClient instance
     */
    public EventLogClient createEventLogClient(DocumentClient documentClient) {

        // test if we have already an instance
        if (eventLogClient != null) {
            return eventLogClient;
        }
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

    /**
     * This method invalidates the rest clients
     */
    public void reset() {
        documentClient = null;
        eventLogClient = null;
    }

}