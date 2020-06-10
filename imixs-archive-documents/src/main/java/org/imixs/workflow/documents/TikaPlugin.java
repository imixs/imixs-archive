package org.imixs.workflow.documents;

import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
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
 * @see TikaDocumentService
 * @version 1.0
 * @author rsoika
 */
public class TikaPlugin extends AbstractPlugin {

    private static Logger logger = Logger.getLogger(TikaPlugin.class.getName());

    @EJB
    TikaDocumentService tikaDocumentService;

    @Inject
    @ConfigProperty(name = TikaDocumentService.ENV_TIKA_SERVICE_MODE, defaultValue = "auto")
    String serviceMode;

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
    @Override
    public ItemCollection run(ItemCollection document, ItemCollection event) throws PluginException {
        if (!"auto".equalsIgnoreCase(serviceMode)) {
            // update the dms meta data
            tikaDocumentService.extractText(document);
        }
        return document;
    }
}
