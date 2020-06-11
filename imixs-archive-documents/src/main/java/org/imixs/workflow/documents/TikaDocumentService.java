package org.imixs.workflow.documents;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.core.SnapshotService;
import org.imixs.workflow.FileData;
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
public class TikaDocumentService {

    public static final String DEFAULT_ENCODING = "UTF-8";
    public static final String PLUGIN_ERROR = "PLUGIN_ERROR";
    public static final String ENV_TIKA_SERVICE_ENDPONT = "tika.service.endpoint";
    public static final String ENV_TIKA_SERVICE_MODE = "tika.service.mode";

    public static final String ENV_TIKA_OCR_MODE = "tika.ocr.mode"; // PDF_ONLY, OCR_ONLY, MIXED

    private static Logger logger = Logger.getLogger(TikaDocumentService.class.getName());

    @Inject
    @ConfigProperty(name = ENV_TIKA_SERVICE_ENDPONT, defaultValue = "")
    String serviceEndpoint;

    @Inject
    @ConfigProperty(name = TikaDocumentService.ENV_TIKA_SERVICE_MODE, defaultValue = "auto")
    String serviceMode;

    @Inject
    @ConfigProperty(name = ENV_TIKA_OCR_MODE, defaultValue = "PDF_AND_OCR")
    String ocrMode;

    @Inject
    SnapshotService snapshotService;

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
                // update the dms meta data
                extractText(processingEvent.getDocument());
            }
        }
    }

    /**
     * Extracts the textual information from document attachments.
     * <p>
     * The method extracts the textual content for each new document of a given
     * workitem. For PDF files with textual content the method calls the method
     * 'extractTextFromPDF' using the PDFBox api. In other cases, the method sends
     * the content via a Rest API to the tika server for OCR processing.
     * <p>
     * The result is stored into the fileData attribute 'text'
     * 
     * @param workitem
     * @throws PluginException
     */
    public void extractText(ItemCollection workitem) throws PluginException {
        extractText(workitem, null, null);
    }

    /**
     * Extracts the textual information from document attachments.
     * <p>
     * The method extracts the textual content for each new document of a given
     * workitem. For PDF files with textual content the method calls the method
     * 'extractTextFromPDF' using the PDFBox api. In other cases, the method sends
     * the content via a Rest API to the tika server for OCR processing.
     * <p>
     * The result is stored into the fileData attribute 'text'
     * <p>
     * The method also extracts files already stored in a snapshot workitem. In this
     * case the method tests if the attribute 'text' already exists.
     * 
     * @param workitem - workitem with file attachments
     * @param _ocrmode - PDF_ONLY, OCR_ONLY, MIXED
     * @param options  - optional tika header params
     * @throws PluginException
     */
    public void extractText(ItemCollection workitem, String _ocrmode, List<String> options) throws PluginException {

        // overwrite ocrmode?
        if (_ocrmode != null) {
            this.ocrMode = _ocrmode;
        }

        long l = System.currentTimeMillis();
        // List<ItemCollection> currentDmsList = DMSHandler.getDmsList(workitem);
        List<FileData> files = workitem.getFileData();

        for (FileData fileData : files) {

            // do we need to parse the content?
            if (!hasOCRContent(fileData)) {
                // yes - fetch the origin fileData object....
                FileData originFileData = fetchOriginFileData(fileData, workitem);
                if (originFileData != null) {
                    String ocrContent = null;
                    // extract the text content...
                    try {
                        logger.info("...text extraction '" + originFileData.getName() + "'...");
                        // test for simple text extraction via PDFBox
                        if (isPDF(originFileData)) {
                            // PDF_ONLY, OCR_ONLY, PDF_AND_OCR
                            if ("OCR_ONLY".equals(ocrMode)) {
                                // OCR Only
                                logger.info("...force orc scan for pdfs...");
                                ocrContent = doORCProcessing(originFileData, options);
                            } else {
                                // try PDFBox....
                                ocrContent = doPDFTextExtraction(originFileData);
                                // if we have not a meaningful content we discard the result and try the tika
                                // api...
                                if (ocrContent != null && ocrContent.length() < 16) {
                                    ocrContent = null;
                                }

                                if (ocrContent == null && ("MIXED".equals(ocrMode))) {
                                    // lets try it with OCR...
                                    ocrContent = doORCProcessing(originFileData, options);
                                }
                            }
                        } else {
                            // for all other files than PDF we do a ocr scann if not PDF_ONLY mode
                            if (!"PDF_ONLY".equals(ocrMode)) {
                                ocrContent = doORCProcessing(originFileData, options);
                            }
                        }

                        if (ocrContent == null) {
                            logger.warning("Unable to extract ocr-content for '" + fileData.getName() + "'");
                            ocrContent = "";
                        }

                        // store the ocrContent....
                        List<Object> list = new ArrayList<Object>();
                        list.add(ocrContent);
                        fileData.setAttribute("text", list);

                    } catch (IOException e) {
                        throw new PluginException(TikaDocumentService.class.getSimpleName(), PLUGIN_ERROR,
                                "Unable to scan attached document '" + fileData.getName() + "'", e);
                    }
                }
            }

        }
        logger.fine("...extracted textual information in " + (System.currentTimeMillis() - l) + "ms");

    }

    /**
     * This method returns true if a given FileData object has already a ocr content
     * object stored. This can be verified by the existence of the 'text'
     * attribute.
     * 
     * @param fileData - fileData object to be verified
     * @return
     */
    @SuppressWarnings("unchecked")
    private boolean hasOCRContent(FileData fileData) {
        if (fileData != null) {
            List<String> ocrContentList = (List<String>) fileData.getAttribute("text");
            if (ocrContentList != null && ocrContentList.size() > 0 && ocrContentList.get(0) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method fetches the origin FileData object. In case the content of the
     * FileData object was already stored in a snapshot, the method loads the origin
     * FileData object form the snapshot workitem.
     * <p>
     * The method returns null if no content can be found. In this case warning is
     * logged.
     * 
     * @param fileData - fileData object to be analyzed
     * @param workitem - origin workitme holding a reference to a optional snapshot
     *                 workitem
     * @return origin fileData object
     */
    private FileData fetchOriginFileData(FileData fileData, ItemCollection workitem) {

        // test if the given fileData object has a content....
        byte[] fileContent = fileData.getContent();
        if (fileContent != null && fileContent.length > 1) {
            // the fileData object contains the origin content.
            // no snapshot need to be loaded here!
            return fileData;
        }

        // load the snapshot FileData...
        FileData snapshotFileData = snapshotService.getWorkItemFile(workitem.getUniqueID(), fileData.getName());
        if (snapshotFileData != null) {
            fileContent = snapshotFileData.getContent();
            if (fileContent != null && fileContent.length > 1) {
                // return the snapshot FileData object
                return snapshotFileData;
            }
        }
        // no content found!
        logger.warning("no content found for fileData '" + fileData.getName() + "'!");
        return null;
    }

    /**
     * This method sends the content of a document to the Tika Rest API for OCR
     * processing.
     * <p>
     * In case the contentType is PDF then the following tika specific header is
     * added:
     * <p>
     * <code>X-Tika-PDFOcrStrategy: ocr_only</code>
     * <p>
     * 
     * @param fileData - file content and metadata
     * @return text content
     * @throws IOException
     */
    public String doORCProcessing(FileData fileData, List<String> options) throws IOException {

        // read the Tika Service Enpoint
        if (serviceEndpoint == null || serviceEndpoint.isEmpty()) {
            return null;
        }

        logger.info("...ocr scanning....");

        // adapt ContentType
        String contentType = adaptContentType(fileData);

        // validate content type
        if (!acceptContentType(contentType)) {
            logger.fine("contentType '" + contentType + " is not supported by Tika Server");
            return null;
        }

        PrintWriter printWriter = null;
        HttpURLConnection urlConnection = null;
        PrintWriter writer = null;
        try {
            urlConnection = (HttpURLConnection) new URL(serviceEndpoint).openConnection();
            urlConnection.setRequestMethod("PUT");
            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);
            urlConnection.setAllowUserInteraction(false);

            /** * HEADER ** */
            urlConnection.setRequestProperty("Content-Type", contentType + "; charset=" + DEFAULT_ENCODING);
            urlConnection.setRequestProperty("Accept", "text/plain");

            /** do we have header options? **/
            if (options != null && options.size() > 0) {
                for (String option : options) {
                    int i = option.indexOf("=");

                    if (i > -1) {
                        String key = option.substring(0, i);
                        String value = option.substring(i + 1);
                        if (key.startsWith("X-Tika")) {
                            // urlConnection.setRequestProperty("X-Tika-PDFOcrStrategy", "ocr_only");
                            urlConnection.setRequestProperty(key, value);
                        } else {
                            logger.warning("Invalid tika option : '" + option + "'  key must start with 'X-Tika'");
                        }
                    } else {
                        logger.warning("Invalid tika option : '" + option + "'  character '=' expreced!");
                    }
                }
            }

            // compute length
            urlConnection.setRequestProperty("Content-Length", "" + Integer.valueOf(fileData.getContent().length));
            OutputStream output = urlConnection.getOutputStream();
            writer = new PrintWriter(new OutputStreamWriter(output, DEFAULT_ENCODING), true);
            output.write(fileData.getContent());
            writer.flush();

            int resposeCode = urlConnection.getResponseCode();

            if (resposeCode >= 200 && resposeCode <= 299) {
                return readResponse(urlConnection, DEFAULT_ENCODING);
            }

            // no data!
            return null;

        } finally {
            // Release current connection
            if (printWriter != null)
                printWriter.close();
        }
    }

    /**
     * This method extracts the text from the given content of an PDF file. In case
     * the pdf file does not contains text the pdf can be forwarded to the tika
     * service for OCR scanning. In this case we append the header attribute
     * X-Tika-PDFOcrStrategy=ocr_only.
     * <p>
     * Extracting text is one of the main features of the PDF box library. You can
     * extract text using the getText() method of the PDFTextStripper class. This
     * class extracts all the text from the given PDF document.
     * 
     * 
     * @param content
     * @return
     */
    public String doPDFTextExtraction(FileData fileData) {
        logger.info("...pdf text extraction....");
        PDDocument doc = null;
        String result = null;
        try {
            doc = PDDocument.load(fileData.getContent());

            PDFTextStripper pdfStripper = new PDFTextStripper();
            // Retrieving text from PDF document
            result = pdfStripper.getText(doc);
            logger.finest("<RESULT>" + result + "</RESULT>");
            // Closing the document
            doc.close();
        } catch (IOException e) {
            logger.warning("unable to load pdf : " + e.getMessage());

        } finally {
            if (doc != null) {
                try {
                    doc.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    /**
     * Reads the response from a http request.
     * 
     * @param urlConnection
     * @throws IOException
     */
    private String readResponse(URLConnection urlConnection, String encoding) throws IOException {
        // get content of result
        logger.finest("......readResponse....");
        StringWriter writer = new StringWriter();
        BufferedReader in = null;
        try {
            // test if content encoding is provided
            String sContentEncoding = urlConnection.getContentEncoding();
            if (sContentEncoding == null || sContentEncoding.isEmpty()) {
                // no so lets see if the client has defined an encoding..
                if (encoding != null && !encoding.isEmpty())
                    sContentEncoding = encoding;
            }

            // if an encoding is provided read stream with encoding.....
            if (sContentEncoding != null && !sContentEncoding.isEmpty())
                in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), sContentEncoding));
            else
                in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                logger.finest("......" + inputLine);
                // append text plus new line!
                writer.write(inputLine + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null)
                in.close();
        }

        return writer.toString();

    }

    /**
     * Tika does not support any content type. So we filter some of them (e.g.
     * application/octet-stream).
     * 
     * @param contentType
     * @return
     */
    private boolean acceptContentType(String contentType) {

        if (contentType == null || contentType.isEmpty()) {
            return false;
        }
        if ("application/octet-stream".equalsIgnoreCase(contentType)) {
            return false;
        }

        return true;
    }

    /**
     * This method verifies the content Type stored in a FileData object.
     * <p>
     * In case no contenttype is provided or is '*' the adapts the content type
     * based on the file extension
     * <p>
     * If no contentType can be computed, the method returns the default contentType
     * application/xml
     * 
     * 
     * @param fileData
     * @return
     */
    private String adaptContentType(FileData fileData) {

        String contentType = fileData.getContentType();

        // verify */*
        if (contentType == null || contentType.isEmpty() || "*/*".equals(contentType)) {
            // compute contentType based on file extension...
            if (fileData.getName().toLowerCase().endsWith(".pdf")) {
                contentType = "application/pdf";
            } else {
                // set default type
                contentType = "application/xml";
            }
        }

        return contentType;
    }

    /**
     * Returns true if the filename ends for '.pdf' or the contentType contains pdf.
     * 
     * @param filename
     * @param contentType
     * @return
     */
    private boolean isPDF(FileData fileData) {
        if (fileData.getName().toLowerCase().endsWith(".pdf")) {
            return true;
        }
        if (fileData.getContentType().contains("pdf")) {
            return true;
        }
        return false;
    }

}