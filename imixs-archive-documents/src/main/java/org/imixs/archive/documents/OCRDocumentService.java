package org.imixs.archive.documents;

import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.core.SnapshotService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.ProcessingEvent;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.PluginException;

import jakarta.ejb.Stateless;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * The TikaDocumentService extracts the textual information from document
 * attachments. The CDI bean runs on the ProcessingEvent BEFORE_PROCESS. The
 * service sends each new attached document to an instance of an Apache Tika
 * Server to get the file content.
 * <p>
 * The service expects a valid Rest API end-point defined by the Environment
 * Parameter 'TIKA_SERVICE_ENDPONT'. If the TIKA_SERVICE_ENDPONT is not set,
 * then the service will be skipped.
 * <p>
 * The environment parameter 'TIKA_SERVICE_MODE' must be set to 'auto' to enable
 * the service.
 * <p>
 * See also the project: https://github.com/imixs/imixs-docker/tree/master/tika
 * 
 * @version 1.1
 * @author rsoika
 */
@Stateless
public class OCRDocumentService {

    public static final String DEFAULT_ENCODING = "UTF-8";
    public static final String PLUGIN_ERROR = "PLUGIN_ERROR";

    @SuppressWarnings("unused")
    private static Logger logger = Logger.getLogger(OCRDocumentService.class.getName());

    @Inject
    @ConfigProperty(name = TikaService.ENV_OCR_SERVICE_ENDPOINT)
    Optional<String> serviceEndpoint;

    @Inject
    @ConfigProperty(name = TikaService.ENV_OCR_SERVICE_MODE, defaultValue = "auto")
    String serviceMode;

    @Inject
    SnapshotService snapshotService;

    @Inject
    TikaService ocrService;

    /**
     * React on the ProcessingEvent. This method sends the document content to the
     * tika server and updates the DMS information.
     * 
     * @throws PluginException
     */
    public void onBeforeProcess(@Observes ProcessingEvent processingEvent) throws PluginException {

        if (!serviceEndpoint.isPresent() || serviceEndpoint.get().isEmpty()) {
            return;
        }

        // Service only runs if the Tika Service mode is set to 'auto'
        if ("auto".equalsIgnoreCase(serviceMode)) {
            if (processingEvent.getEventType() == ProcessingEvent.BEFORE_PROCESS) {
                ItemCollection workitem = processingEvent.getDocument();
                try {
                    ocrService.extractText(workitem, snapshotService.findSnapshot(workitem));
                } catch (AdapterException e) {
                    throw new PluginException(e);
                }
            }
        }
    }

}