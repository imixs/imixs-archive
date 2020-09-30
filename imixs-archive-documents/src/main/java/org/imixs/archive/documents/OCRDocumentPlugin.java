package org.imixs.archive.documents;

import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.core.SnapshotService;
import org.imixs.archive.ocr.OCRService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowContext;
import org.imixs.workflow.engine.plugins.AbstractPlugin;
import org.imixs.workflow.exceptions.PluginException;

/**
 * The TikaPlugin extracts the textual information from document attachments.
 * The plug-in sends each new attached document to an instance of an Apache Tika
 * Server to get the file content.
 * <p>
 * The TikaPlugin can be used instead of the TIKA_SERVICE_MODE = 'auto' which
 * will react on the ProcessingEvent BEFORE_PROCESS. The plugin runs only in
 * case the TIKA_SERVICE_MODE is NOT set to 'auto'!
 * 
 * @see OCRDocumentService
 * @version 1.0
 * @author rsoika
 */
public class OCRDocumentPlugin extends AbstractPlugin {

    private static Logger logger = Logger.getLogger(OCRDocumentPlugin.class.getName());

    @Inject
    OCRService ocrService;

    @Inject
    @ConfigProperty(name = OCRDocumentService.ENV_TIKA_SERVICE_MODE, defaultValue = "auto")
    String serviceMode;

    @Inject
    SnapshotService snapshotService;

    @Override
    public void init(WorkflowContext actx) throws PluginException {
        super.init(actx);
        logger.finest("...... service mode = " + serviceMode);
    }

    /**
     * This method sends the document content to the tika server and updates the DMS
     * information.
     * 
     * 
     * @throws PluginException
     */
    @SuppressWarnings("unchecked")
    @Override
    public ItemCollection run(ItemCollection document, ItemCollection event) throws PluginException {
        if ("model".equalsIgnoreCase(serviceMode)) {

            // read optional tika options
            ItemCollection evalItemCollection = this.getWorkflowService().evalWorkflowResult(event, "tika", document,
                    false);
            List<String> tikaOptions = evalItemCollection.getItemValue("options");

            // update the dms meta data
            ocrService.extractText(document, snapshotService.findSnapshot(document), null, tikaOptions);
        } else {
            logger.warning("unexpected TIKA_SERVICE_MODE=" + serviceMode
                    + " - running the OCRDocumentPlugin requires serviceMode=model");
        }
        return document;
    }
}
