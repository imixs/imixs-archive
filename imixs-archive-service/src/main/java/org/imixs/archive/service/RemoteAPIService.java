/*******************************************************************************
 *  Imixs Workflow Technology
 *  Copyright (C) 2001, 2008 Imixs Software Solutions GmbH,  
 *  http://www.imixs.com
 *  
 *  This program is free software; you can redistribute it and/or 
 *  modify it under the terms of the GNU General Public License 
 *  as published by the Free Software Foundation; either version 2 
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful, 
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 *  General Public License for more details.
 *  
 *  You can receive a copy of the GNU General Public
 *  License at http://www.gnu.org/licenses/gpl.html
 *  
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika
 *******************************************************************************/
package org.imixs.archive.service;

import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.service.cassandra.ClusterService;
import org.imixs.melman.DocumentClient;
import org.imixs.melman.RestAPIException;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.xml.XMLDataCollection;
import org.imixs.workflow.xml.XMLDocumentAdapter;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

/**
 * The RemoteService is used to access the remote API from the worklfow
 * instance. All remote clients are based on the Imixs-Rest API.
 * <p>
 * To access the API the services uses the Imixs-Melman DocumentClient.
 * 
 * @version 1.0
 * @author rsoika
 */

@Stateless
public class RemoteAPIService {

    public final static String SNAPSHOT_RESOURCE = "snapshot/";
    public final static String DOCUMENTS_RESOURCE = "documents/";
    public final static String SNAPSHOT_SYNCPOINT_RESOURCE = "snapshot/syncpoint/";

    private static Logger logger = Logger.getLogger(RemoteAPIService.class.getName());

    @Inject
    @ConfigProperty(name = ClusterService.ENV_WORKFLOW_SERVICE_ENDPOINT)
    Optional<String> workflowServiceEndpoint;

    @Inject
    @ConfigProperty(name = ClusterService.ENV_WORKFLOW_SERVICE_USER)
    Optional<String> workflowServiceUser;

    @Inject
    @ConfigProperty(name = ClusterService.ENV_WORKFLOW_SERVICE_PASSWORD)
    Optional<String> workflowServicePassword;

    @Inject
    @ConfigProperty(name = ClusterService.ENV_WORKFLOW_SERVICE_AUTHMETHOD)
    Optional<String> workflowServiceAuthMethod;

    /**
     * This method read sync data. The method returns the first workitem from the
     * given syncpoint. If no data is available the method returns null.
     * 
     * 
     * @return an XMLDataCollection instance representing the data to sync or null
     *         if no data form the given syncpoint is available.
     * @throws ArchiveException
     * 
     */
    public XMLDataCollection readSyncData(long syncPoint, DocumentClient documentClient) throws ArchiveException {
        XMLDataCollection result = null;
        // load next document
        String url = "";
        try {
            url = SNAPSHOT_SYNCPOINT_RESOURCE + syncPoint;
            logger.finest("...... read data: " + url + "....");

            result = documentClient.getCustomResourceXML(url);
        } catch (RestAPIException e) {
            String errorMessage = "...failed readSyncData at : " + url + "  Error Message: " + e.getMessage();
            throw new ArchiveException(ArchiveException.SYNC_ERROR, errorMessage, e);
        }

        if (result != null && result.getDocument().length > 0) {
            return result;
        }
        return null;
    }

    /**
     * This method read the current snapshot id for a given UnqiueID. This
     * information can be used to verify the sync status of a single process
     * instance.
     * 
     * The method throws a ArchiveException in case the snapshot did not exist
     * 
     * @return the current snapshotId
     * @throws ArchiveException
     * 
     */
    public String readSnapshotIDByUniqueID(String uniqueid, DocumentClient documentClient) throws ArchiveException {
        String result = null;
        try {
            // load single document
            String url = DOCUMENTS_RESOURCE + uniqueid + "?items=$snapshotid";
            logger.finest("...... read snapshotid: " + url + "....");

            XMLDataCollection xmlDocument = documentClient.getCustomResourceXML(url);
            if (xmlDocument != null && xmlDocument.getDocument().length > 0) {
                ItemCollection document = XMLDocumentAdapter.putDocument(xmlDocument.getDocument()[0]);
                result = document.getItemValueString("$snapshotid");
            }

        } catch (RestAPIException e) {
            String errorMessage = "...failed to readSyncData : " + e.getMessage();
            throw new ArchiveException(ArchiveException.SYNC_ERROR, errorMessage, e);
        }

        return result;
    }

    public void restoreSnapshot(ItemCollection snapshot, DocumentClient documentClient) throws ArchiveException {
        try {
            String url = SNAPSHOT_RESOURCE;
            logger.finest("...... post data: " + url + "....");
            // documentClient.postDocument(url, snapshot);
            documentClient.postXMLDocument(url, XMLDocumentAdapter.getDocument(snapshot));
        } catch (RestAPIException e) {
            String errorMessage = "...failed to restoreSnapshot: " + e.getMessage();
            throw new ArchiveException(ArchiveException.SYNC_ERROR, errorMessage, e);
        }

    }

    public void deleteSnapshot(String id, DocumentClient documentClient) throws ArchiveException {
        try {
            String url = SNAPSHOT_RESOURCE;
            logger.finest("...... delete data: " + url + "....");
            documentClient.deleteDocument(id);
        } catch (RestAPIException e) {
            String errorMessage = "...failed to deleteSnapshot: " + e.getMessage();
            throw new ArchiveException(ArchiveException.SYNC_ERROR, errorMessage, e);
        }

    }

    /**
     * Helper method to initalize a Melman Workflow Client based on the current
     * archive configuration.
     * 
     * @throws RestAPIException
     */
    // DocumentClient initWorkflowClient() throws RestAPIException {

    // logger.finest("...... WORKFLOW_SERVICE_ENDPOINT = " +
    // workflowServiceEndpoint);

    // DocumentClient documentClient = new
    // DocumentClient(workflowServiceEndpoint.get());

    // // Test authentication method
    // if ("Form".equalsIgnoreCase(workflowServiceAuthMethod.get())) {
    // // default basic authenticator
    // FormAuthenticator formAuth = new
    // FormAuthenticator(workflowServiceEndpoint.get(), workflowServiceUser.get(),
    // workflowServicePassword.get());
    // // register the authenticator
    // documentClient.registerClientRequestFilter(formAuth);

    // } else {
    // // default basic authenticator
    // BasicAuthenticator basicAuth = new
    // BasicAuthenticator(workflowServiceUser.get(),
    // workflowServicePassword.get());
    // // register the authenticator
    // documentClient.registerClientRequestFilter(basicAuth);
    // }
    // return documentClient;
    // }
}
