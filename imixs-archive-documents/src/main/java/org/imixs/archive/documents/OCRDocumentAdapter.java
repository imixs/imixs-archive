package org.imixs.archive.documents;

import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.core.SnapshotService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.SignalAdapter;
import org.imixs.workflow.engine.WorkflowService;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.PluginException;

/**
 * The TikaDocumentAdapter reacts on ProcessingEvent to auto extract the text
 * content.
 * <p>
 * The adapter expect the following environment setting
 * 
 * TIKA_SERVICE_MODE: "MODEL"
 * 
 * You can set additional options to be passed to the Tika Service
 * 
 * <p>
 * 
 * <pre>
 * {@code
        <tika name="options">X-Tika-PDFocrStrategy=OCR_ONLY</tika>
        <tika name="options">X-Tika-PDFOcrImageType=RGB</tika>
        <tika name="options">X-Tika-PDFOcrDPI=400</tika>
   }
 * </pre>
 * 
 * @see OCRDocumentService
 * @version 1.0
 * @author rsoika
 */
public class OCRDocumentAdapter implements SignalAdapter {

    public static final String OCR_ERROR = "OCR_ERROR";

    private static Logger logger = Logger.getLogger(OCRDocumentAdapter.class.getName());

    @Inject
    @ConfigProperty(name = TikaService.ENV_OCR_SERVICE_MODE, defaultValue = "auto")
    String serviceMode;

    @Inject
    TikaService ocrService;

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
                List<String> tikaOptions = null;
                String filePattern = null;
                int maxPdfPages=0;
                // read opitonal tika options
                ItemCollection evalItemCollection = workflowService.evalWorkflowResult(event, "tika", document, false);
                if (evalItemCollection != null) {
                    tikaOptions = evalItemCollection.getItemValue("options");
                    filePattern = evalItemCollection.getItemValueString("filepattern");
                    maxPdfPages = evalItemCollection.getItemValueInteger("maxpdfpages"); // only for pdf documents
                }
                // extract text data....
                ocrService.extractText(document, snapshotService.findSnapshot(document), null, tikaOptions,
                        filePattern,maxPdfPages);
            } catch (PluginException e) {
                String message = "Tika OCRService - unable to extract text: " + e.getMessage();
                throw new AdapterException(e.getErrorContext(), e.getErrorCode(), message, e);
            } catch (RuntimeException e) {
                // we catch a runtimeException to avoid dead locks in the eventLog processing
                // issue #153
                String message = "Tika OCRService - unable to extract text: " + e.getMessage();
                throw new AdapterException(OCRDocumentAdapter.class.getSimpleName(), OCR_ERROR, message, e);
            }
        } else {
            logger.warning("unexpected TIKA_SERVICE_MODE=" + serviceMode
                    + " - running the OCRDocumentAdapter the env TIKA_SERVICE_MODE should be set to 'model'. Adapter will be ignored!");
        }

        return document;
    }

}