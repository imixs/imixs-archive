package org.imixs.archive.service;

import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.service.util.RestClientHelper;
import org.imixs.melman.DocumentClient;
import org.imixs.melman.EventLogClient;
import org.imixs.melman.RestAPIException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

/**
 * The SyncScheduler starts a TimerService to pull new snapshot events from the
 * workflow instance and push the snapshot data into the cassandra cluster
 *
 * @see SyncService
 * @author ralph.soika@imixs.com
 * @version 3.0
 */
@Startup
@Singleton
@LocalBean
@DeclareRoles({ "org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@RunAs("org.imixs.ACCESSLEVEL.MANAGERACCESS")
public class SyncScheduler {

    // timeout interval in ms
    @Inject
    @ConfigProperty(name = ImixsArchiveApp.WORKFLOW_SYNC_INTERVAL, defaultValue = "5000")
    long interval;

    @Inject
    @ConfigProperty(name = ImixsArchiveApp.WORKFLOW_SYNC_INITIALDELAY, defaultValue = "30000")
    long initialDelay;

    @Inject
    @ConfigProperty(name = ImixsArchiveApp.WORKFLOW_SERVICE_ENDPOINT)
    Optional<String> workflowServiceEndpoint;

    @Inject
    @ConfigProperty(name = ImixsArchiveApp.WORKFLOW_SERVICE_AUTHMETHOD)
    Optional<String> workflowServiceAuthMethod;

    @Inject
    @ConfigProperty(name = ImixsArchiveApp.WORKFLOW_SERVICE_USER)
    Optional<String> workflowServiceUser;

    @Inject
    @ConfigProperty(name = ImixsArchiveApp.WORKFLOW_SERVICE_PASSWORD)
    Optional<String> workflowServicePassword;

    @Inject
    SyncService archiveSyncService;

    @Resource
    jakarta.ejb.TimerService timerService;

    @Inject
    RestClientHelper restClientHelper;

    private static Logger logger = Logger.getLogger(SyncScheduler.class.getName());

    /**
     * Initialize ManagedScheduledExecutorService
     */
    @PostConstruct
    public void init() {
        if (workflowServiceEndpoint.isPresent()) {
            logger.info("Starting Archive SyncScheduler - initalDelay=" + initialDelay + "ms  inverval=" + interval
                    + "ms ....");
            // Registering a non-persistent Timer Service.
            final TimerConfig timerConfig = new TimerConfig();
            timerConfig.setInfo(""); // empty info string indicates no JSESSIONID!
            timerConfig.setPersistent(false);
            timerService.createIntervalTimer(initialDelay, interval, timerConfig);
        }
    }

    /**
     * This method is called by the TimerService.
     * <p>
     * In case of a form based authentication, the method tries to login to the
     * workflow instance first and stores the JSESSIONID into the timer config. If a
     * JSESSIONID already exist, the id is reused. This is to avoid multiple login
     * session to the workflow instance.
     * <p>
     * In case of a Basic authentication no explicit login is performed (because
     * implicit basic authentication is sufficient).
     * 
     * @throws ArchiveException
     */
    @Timeout
    public void run(Timer timer) {
        DocumentClient documentClient = null;
        EventLogClient eventLogClient = null;

        logger.fine("--- run timeout.... timerInfo= " + timer.getInfo());
        try {

            // init rest clients....
            documentClient = restClientHelper.createDocumentClient();
            eventLogClient = restClientHelper.createEventLogClient(documentClient);

            // // Test the authentication method and create a corresponding Authenticator
            // if ("Form".equalsIgnoreCase(workflowServiceAuthMethod.get())) {
            // // test if a JSESSIONID exists?
            // String jSessionID = (String) timer.getInfo();
            // if (jSessionID == null || jSessionID.isEmpty()) {
            // logger.info("Missing or invalid jSessionID - need new login....");
            // // no - we need to login first and store the JSESSIONID in a new timer
            // object...
            // // create a FormAuthenticator
            // FormAuthenticator formAuth = new
            // FormAuthenticator(workflowServiceEndpoint.get(),
            // workflowServiceUser.get(), workflowServicePassword.get());
            // // Authentication successful - do we have a JSESSIONID?
            // String jsessionID = formAuth.getJsessionID();
            // if (jsessionID != null && !jsessionID.isEmpty()) {
            // logger.fine("--- reinitialze timer with new jSessionID : " + jsessionID + "
            // and interval="
            // + interval);
            // // yes - terminate existing timer and create a new one with the JSESSIONID

            // timer.cancel();
            // final TimerConfig timerConfig = new TimerConfig();
            // timerConfig.setInfo(jsessionID);
            // timerConfig.setPersistent(false);
            // timerService.createIntervalTimer(interval, interval, timerConfig);
            // logger.fine("---created new timer");
            // logger.info(
            // "successful reconnected: " + workflowServiceEndpoint.get() + " , new Timer
            // created...");
            // return;
            // }
            // } else {
            // logger.fine("--- reuse jSessionID " + jSessionID + " for login....");
            // // we have already a jsessionCooke Data object - so create a new
            // // FormAuthenticator form the JSESSIONID
            // FormAuthenticator formAuth = new
            // FormAuthenticator(workflowServiceEndpoint.get(), jSessionID);
            // authenticator = formAuth;
            // }
            // } else {
            // // Default behaviro - use a BasicAuthenticator
            // BasicAuthenticator basicAuth = new
            // BasicAuthenticator(workflowServiceUser.get(),
            // workflowServicePassword.get());
            // authenticator = basicAuth;
            // }

            // do we have a valid authentication?
            if (documentClient != null) {
                // yes - create the client objects
                // documentClient = new DocumentClient(workflowServiceEndpoint.get());
                // documentClient.registerClientRequestFilter(authenticator);
                // eventLogClient = new EventLogClient(workflowServiceEndpoint.get());
                // eventLogClient.registerClientRequestFilter(authenticator);
                logger.fine("--- process event log....");
                // release dead locks...
                archiveSyncService.releaseDeadLocks(eventLogClient);
                // process the eventLog...
                archiveSyncService.processEventLog(eventLogClient, documentClient);
                logger.fine("--- process event log completed.");
            } else {
                // no valid Authenticator!
                logger.warning("unable to connect: " + workflowServiceEndpoint);
            }

        } catch (NotFoundException | RestAPIException e) {
            logger.warning("unable to process event log: " + e.getMessage());
            // we need to reset the timer and discard the current JSESSIONID
            restClientHelper.reset();
            timer.cancel();
            final TimerConfig timerConfig = new TimerConfig();
            timerConfig.setInfo(""); // empty info string indicates no JSESSIONID!
            timerConfig.setPersistent(false);
            logger.warning("restarting sync in " + initialDelay + " ms...");
            timerService.createIntervalTimer(initialDelay, interval, timerConfig);
        }

    }

}
