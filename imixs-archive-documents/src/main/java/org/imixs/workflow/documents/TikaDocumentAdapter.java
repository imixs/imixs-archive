package org.imixs.workflow.documents;

import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.SignalAdapter;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.PluginException;

/**
 * The TikaDocumentAdapter reacts on ProcessingEvent to auto extract the text
 * content.
 * 
 * 
 * @see TikaDocumentService
 * @version 1.1
 * @author rsoika
 */
@Stateless
public class TikaDocumentAdapter implements SignalAdapter {

    private static Logger logger = Logger.getLogger(TikaDocumentAdapter.class.getName());

    @Inject
    @ConfigProperty(name = TikaDocumentService.ENV_TIKA_SERVICE_MODE, defaultValue = "auto")
    String serviceMode;

    @Inject
    TikaDocumentService tikaDocumentService;

    /**
     * This method posts a text from an attachment to the Imixs-ML Analyse service
     * endpoint
     */
    public ItemCollection execute(ItemCollection document, ItemCollection event) throws AdapterException {

        if (!"auto".equalsIgnoreCase(serviceMode)) {
            logger.finest("...running api adapter...");
            // update the dms meta data
            try {
                tikaDocumentService.extractText(document);
            } catch (PluginException e) {
                throw new AdapterException(e.getErrorContext(), e.getErrorCode(), e.getMessage(), e);
            }
        }
        return document;
    }

}