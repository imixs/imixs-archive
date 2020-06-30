package org.imixs.archive.documents;

import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.core.SnapshotService;
import org.imixs.archive.ocr.OCRService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.ProcessingEvent;
import org.imixs.workflow.exceptions.PluginException;

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
    public static final String ENV_TIKA_SERVICE_ENDPONT = "tika.service.endpoint";
    public static final String ENV_TIKA_SERVICE_MODE = "tika.service.mode";

    public static final String ENV_TIKA_OCR_MODE = "tika.ocr.mode"; // PDF_ONLY, OCR_ONLY, MIXED

    @SuppressWarnings("unused")
    private static Logger logger = Logger.getLogger(OCRDocumentService.class.getName());

    @Inject
    @ConfigProperty(name = ENV_TIKA_SERVICE_ENDPONT, defaultValue = "")
    String serviceEndpoint;

    @Inject
    @ConfigProperty(name = OCRDocumentService.ENV_TIKA_SERVICE_MODE, defaultValue = "auto")
    String serviceMode;



    @Inject
    SnapshotService snapshotService;
    
     
    @Inject
    OCRService ocrService;

    /**
     * React on the ProcessingEvent. This method sends the document content to the
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
                ItemCollection workitem=processingEvent.getDocument();
                ocrService.extractText(workitem, snapshotService.findSnapshot(workitem));
               // extractText(processingEvent.getDocument());
            }
        }
    }

 

}