package org.imixs.archive.service.util;

import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.impl.client.HttpClients;
import org.imixs.melman.RestAPIException;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.AuthorizationResponse;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

/**
 * This RequestFilter performs a keycloak authentication.
 * 
 * 
 * 
 * The client configuration is defined in a keycloak.json file as follows:
 * 
 * {
 * "realm": "imixs-office-workflow",
 * "auth-server-url" : "https://my.keycloak.host/",
 * "resource" : "the clientid",
 * "credentials": {
 * "secret": "xxx"
 * }
 * }
 * 
 * 
 * @author rsoika
 *
 */
public class KeycloakAuthenticator implements ClientRequestFilter {

    private String token = null;

    public static final String KEYCLOAK_ENDPOINT = "keycloak.endpoint";
    public static final String KEYCLOAK_REALM = "keycloak.realm";
    public static final String KEYCLOAK_CLIENTID = "keycloak.clientid";
    public static final String KEYCLOAK_SECRET = "keycloak.secret";

    private final static Logger logger = Logger.getLogger(KeycloakAuthenticator.class.getName());

    // Keycloak
    String keycloakServer;
    String keycloakRealm;
    String keycloakClientId;
    String keycloakSecret;

    /**
     * Creates a new FormAuthenticator based on a baseUri and a username, password.
     * The constructor post the user credentials to the endpoint /j_security_check
     * to receive a JSESSIONID.
     * 
     * @param _baseUri
     * @param username
     * @param password
     * @throws RestAPIException
     */
    public KeycloakAuthenticator(String _baseUri, String username, String password) throws RestAPIException {
        boolean debug = logger.isLoggable(Level.FINE);
        logger.info("init KeycloakAuthenticator... (v2)");
        readConfig();

        debug = true;
        if (debug) {
            logger.info("keycloak login: " + _baseUri);

        }

        // Access keycloak token
        final Configuration configuration = new Configuration(
                keycloakServer,
                keycloakRealm,
                keycloakClientId,
                Collections.singletonMap("secret", keycloakSecret),
                HttpClients.createDefault());

        // token = AuthzClient.create(configuration).obtainAccessToken(username,
        // password).getToken();

        // send the entitlement request to the server in order to
        // obtain an RPT with all permissions granted to the user
        AuthzClient authzClient = AuthzClient.create(configuration);
        AuthorizationRequest request = new AuthorizationRequest();
        AuthorizationResponse response = authzClient.authorization(username, password).authorize(request);
        token = response.getToken();

        logger.info("token=" + token);

        AccessToken accessToken;
        try {
            accessToken = new JWSInput(token).readJsonContent(AccessToken.class);
            logger.info("---email=" + accessToken.getEmail());

        } catch (JWSInputException cause) {
            throw new IllegalArgumentException("Failed to deserialize token", cause);
        }

    }

    public void readConfig() {
        logger.info("read config KeycloakAuthenticator...");
        keycloakServer = System.getenv("KEYCLOAK_ENDPOINT");
        keycloakRealm = System.getenv("KEYCLOAK_REALM");
        keycloakClientId = System.getenv("KEYCLOAK_CLIENTID");
        keycloakSecret = System.getenv("KEYCLOAK_SECRET");

        logger.info("Keycloak Server: " + keycloakServer);
        logger.info("Keycloak Realm: " + keycloakRealm);
        logger.info("Keycloak ClientId: " + keycloakClientId);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    /**
     * This filter method is called for each request. The method adds teh bearer
     * token into the header.
     * 
     */
    public void filter(ClientRequestContext requestContext) throws IOException {
        if (token != null && !"".equals(token)) {
            logger.info(" add authroization header....v2");
            // requestContext.getHeaders().add("Bearer", getToken());

            // Authorization: bearer $TOKEN
            // requestContext.getHeaders().add(HttpHeaders.AUTHORIZATION, getToken());

            requestContext.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer " + getToken());

        }
    }

}