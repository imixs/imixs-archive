package org.imixs.archive.documents;

import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.core.SnapshotService;
import org.imixs.archive.ocr.OCRService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.SignalAdapter;
import org.imixs.workflow.engine.WorkflowService;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.PluginException;

/**
 * The TikaDocumentAdapter reacts on ProcessingEvent to auto extract the text
 * content.
 * 
 * 
 * @see OCRDocumentService
 * @version 1.0
 * @author rsoika
 */
public class OCRDocumentAdapter implements SignalAdapter {

    private static Logger logger = Logger.getLogger(OCRDocumentAdapter.class.getName());

    @Inject
    @ConfigProperty(name = OCRDocumentService.ENV_TIKA_SERVICE_MODE, defaultValue = "auto")
    String serviceMode;

    @Inject
    OCRService ocrService;

    @Inject
    WorkflowService workflowService;

    @Inject
    SnapshotService snapshotService;

    /**
     * This method posts a text from an attachment to the Imixs-ML Analyse service
     * endpoint
     */
    @SuppressWarnings("unchecked")
    @Override
    public ItemCollection execute(ItemCollection document, ItemCollection event) throws AdapterException {

        logger.info("......starting TikaDocumentAdapter mode=" + serviceMode);
        if ("model".equalsIgnoreCase(serviceMode)) {
            logger.finest("...running api adapter...");

           
            try {
                // read opitonal tika options
                ItemCollection evalItemCollection = workflowService.evalWorkflowResult(event, "tika", document, false);
                List<String> tikaOptions = evalItemCollection.getItemValue("options");

                // extract text data....
                ocrService.extractText(document, snapshotService.findSnapshot(document), null, tikaOptions);
            } catch (PluginException e) {
                throw new AdapterException(e.getErrorContext(), e.getErrorCode(), e.getMessage(), e);
            }
        } else {
            logger.warning("unexpected TIKA_SERVICE_MODE=" + serviceMode);
        }

        return document;
    }

}