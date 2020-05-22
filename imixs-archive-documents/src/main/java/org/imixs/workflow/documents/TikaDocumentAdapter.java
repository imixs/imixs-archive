package org.imixs.workflow.documents;

import javax.ejb.Stateless;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.workflow.engine.ProcessingEvent;
import org.imixs.workflow.exceptions.PluginException;

/**
 * The TikaDocumentAdapter reacts on ProcessingEvent to auto extract the text content.
 * 
 * 
 * @see TikaDocumentService
 * @version 1.1
 * @author rsoika
 */
@Stateless
public class TikaDocumentAdapter {
    
    @Inject
    @ConfigProperty(name = TikaDocumentService.ENV_TIKA_SERVICE_ENDPONT, defaultValue = "")
    String serviceEndpoint;

    @Inject
    @ConfigProperty(name = TikaDocumentService.ENV_TIKA_SERVICE_MODE, defaultValue = "auto")
    String serviceMode;

    
    @Inject
    TikaDocumentService tikaDocumentService; 

    /**
     * React on the ProcessingEvent This method sends the document content to the
     * tika server and updates the DMS information.
     * 
     * @throws PluginException
     */
    public void onBeforeProcess(@Observes ProcessingEvent processingEvent) throws PluginException {

        if (serviceEndpoint == null || serviceEndpoint.isEmpty()) {
            return;
        }
        // Service only runs if the Tika Service mode is set to 'auto'
        if ("auto".equalsIgnoreCase(serviceMode)) {
            if (processingEvent.getEventType() == ProcessingEvent.BEFORE_PROCESS) {
                // update the dms meta data
                tikaDocumentService.extractText(processingEvent.getDocument());
            }
        }
    }

   

}