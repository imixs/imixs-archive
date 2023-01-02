package org.imixs.archive.backup;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.melman.BasicAuthenticator;
import org.imixs.melman.CookieAuthenticator;
import org.imixs.melman.EventLogClient;
import org.imixs.melman.FormAuthenticator;
import org.imixs.melman.JWTAuthenticator;
import org.imixs.melman.ModelClient;
import org.imixs.melman.RestAPIException;
import org.imixs.melman.WorkflowClient;
import org.imixs.workflow.ItemCollection;

import jakarta.ejb.EJB;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.Cookie;

/**
 * The ConnectionController stores the connection data to an Imixs Workflow
 * Instance Endpoint
 * <p>
 * The endpoint defines the URL to the rest API. The key is the userID or cookie
 * used for authentication. The token is the user password or cookie value for
 * authentication. The type defines the login type.
 * <p>
 * The method <code>connect</code> can be used to establish a test connection
 * indicating if the Rest API of the corresponding workflow instance is working.
 * The method also starts a JSF conversation scope.
 *
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class ConnectionController implements Serializable {

    private static final long serialVersionUID = 7027147503119012594L;

    @Inject
    @ConfigProperty(name = "imixs.instance.endpoint")
    Optional<String> instanceEndpoint;

    @Inject
    @ConfigProperty(name = "imixs.archive.endpoint")
    Optional<String> archiveEndpoint;

    @Inject
    @ConfigProperty(name = BackupApi.ENV_BACKUP_FTP_HOST)
    Optional<String> ftpServer;

    @Inject
    @ConfigProperty(name = BackupApi.ENV_BACKUP_FTP_PATH)
    Optional<String> ftpPath;

    @Inject
    @ConfigProperty(name = BackupApi.ENV_BACKUP_FTP_PORT, defaultValue = "21")
    int ftpPort;

    // timeout interval in ms
    @Inject
    @ConfigProperty(name = BackupApi.WORKFLOW_SYNC_INTERVAL, defaultValue = "1000")
    long interval;

    private static Logger logger = Logger.getLogger(ConnectionController.class.getName());

    private String endpoint;
    private String key;
    private String token;
    private String type = "FORM";
    private String errorMessage = "";

    private ItemCollection indexSchema = null;
    private WorkflowClient workflowClient = null;

    @Inject
    BackupService backupService;

    @Inject
    LogController logController;

    public boolean isConnected() {
        String status = backupService.getStatus();
        return "scheduled".equals(status) || "running".equals(status);
    }

    public String getStatus() {
        return backupService.getStatus();
    }

    public Date getNextTimeout() {
        return backupService.getNextTimeout();
    }

    public String getFtpServer() {
        return ftpServer.orElse("");
    }

    public String getFtpPath() {
        return ftpPath.orElse("");
    }

    public int getFtpPort() {
        return ftpPort;
    }

    public String getInstanceEndpoint() {
        return instanceEndpoint.orElse("");
    }

    public String getArchiveEndpoint() {
        return archiveEndpoint.orElse("");
    }

    public long getInterval() {
        return interval;
    }

    /**
     * Starts a new conversation. The method creates a new worklowClient instance
     * and loads the index schema to test if a connection the the Rest API endpoint
     * can be established.
     */
    public void start() {
        try {
            backupService.startScheduler();
        } catch (BackupException e) {
            logController.warning("failed to start scheduler: " + e.getMessage());
        }

        logger.info("...conecting: " + endpoint);

    }

    /**
     * Closes the current conversation and reset the current connection. A
     * conversation is automatically started by the method connect(). You can call
     * the disconnect() method in a actionListener on any JSF navigation action.
     */
    public void disconnect() {

    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Returns the lucene index schema
     *
     * @return
     */
    public ItemCollection getIndexSchema() {
        return indexSchema;
    }

    /**
     * This method creates a WorkflowRest Client and caches the instance
     *
     * @return
     */
    public WorkflowClient getWorkflowClient() {
        if (workflowClient == null && getEndpoint() != null) {
            // Init the workflowClient with a basis URL
            workflowClient = new WorkflowClient(getEndpoint());
            if ("BASIC".equals(getType())) {
                // Create a authenticator
                BasicAuthenticator basicAuth = new BasicAuthenticator(getKey(), getToken());
                // register the authenticator
                workflowClient.registerClientRequestFilter(basicAuth);
            }
            if ("FORM".equals(getType())) {
                // Create a authenticator
                FormAuthenticator formAuth = new FormAuthenticator(getEndpoint(), getKey(), getToken());
                // register the authenticator
                workflowClient.registerClientRequestFilter(formAuth);

            }
            if ("COOKIE".equals(getType())) {
                logger.info("..set authentication cookie: name=" + getKey() + " value=....");
                Cookie cookie = new Cookie(getKey(), getToken());
                CookieAuthenticator cookieAuth = new CookieAuthenticator(cookie);
                workflowClient.registerClientRequestFilter(cookieAuth);
            }
            if ("JWT".equalsIgnoreCase(getType())) {
                JWTAuthenticator jwtAuht = new JWTAuthenticator(getToken());
                workflowClient.registerClientRequestFilter(jwtAuht);
            }
        }

        return workflowClient;

    }

    /**
     * Creates a EventLogClient form the worklfowClients authorization filter
     *
     * @return
     */
    public EventLogClient getEventLogClient() {

        EventLogClient client = new EventLogClient(getWorkflowClient().getBaseURI());
        // register all filters from workfow client
        List<ClientRequestFilter> filterList = getWorkflowClient().getRequestFilterList();
        for (ClientRequestFilter filter : filterList) {
            client.registerClientRequestFilter(filter);
        }
        return client;

    }

    /**
     * Creates a EventLogClient form the worklfowClients authorization filter
     *
     * @return
     */
    public ModelClient getModelClient() {

        ModelClient client = new ModelClient(getWorkflowClient().getBaseURI());
        // register all filters from workfow client
        List<ClientRequestFilter> filterList = getWorkflowClient().getRequestFilterList();
        for (ClientRequestFilter filter : filterList) {
            client.registerClientRequestFilter(filter);
        }
        return client;

    }

    /**
     * This method test if the api endpoint documents/configuration is reachable.
     * The endpoint represents the Lucene IndexSchema. If the configuration could
     * not be loaded than something with the Rest API endpoint is wrong.
     *
     * @return true if api call was successful
     */
    private ItemCollection loadIndexSchema() {
        List<ItemCollection> result;
        WorkflowClient workflowClient = getWorkflowClient();
        if (workflowClient != null) {
            try {
                // we do not expect a result
                workflowClient.setPageSize(1);
                // documents/configuration
                result = workflowClient.getCustomResource("documents/configuration");
                // a valid result object contains the item lucence.fulltextFieldList'
                if (result != null && result.size() > 0) {
                    errorMessage = "";
                    return result.get(0);
                } else {
                    indexSchema = null;
                    errorMessage = "Unable to connect to endpoint!";
                    logger.severe(errorMessage);
                    return null;
                }
            } catch (RestAPIException e) {
                errorMessage = "Unable to connect to endpoint!";
                logger.severe("Unable to connect to endpoint: " + e.getMessage());
            }
        }
        return null;
    }

}