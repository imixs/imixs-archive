package org.imixs.archive.ocr;

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
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.PluginException;

/**
 * The OCRService extracts the textual information from document attachments of
 * a workitem.
 * <p>
 * The text information is stored in the $file attribute 'text'.
 * <p>
 * For PDF files with textual content the PDFBox api is used. In other cases,
 * the method sends the content via a Rest API to the tika server for OCR
 * processing.
 * The environment variable OCR_PDF_MODE defines how PDF files will be scanned. Possible 
 * values are  TEXT_ONLY | OCR_ONLY | TEXT_AND_OCR (default)
 * <p>
 * For OCR processing the service expects a valid Rest API end-point defined by
 * the Environment Parameter 'TIKA_SERVICE_ENDPONT'. If the TIKA_SERVICE_ENDPONT
 * is not set, then the service will be skipped.
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
public class OCRService {

    public static final String FILE_ATTRIBUTE_TEXT = "text";
    public static final String DEFAULT_ENCODING = "UTF-8";
    public static final String PLUGIN_ERROR = "PLUGIN_ERROR";
    public static final String ENV_OCR_SERVICE_ENDPOINT = "ocr.service.endpoint";
    public static final String ENV_OCR_SERVICE_MODE = "ocr.service.mode";
    public static final String ENV_OCR_PDF_MODE = "ocr.scan.mode"; // TEXT_ONLY, OCR_ONLY, TEXT_AND_OCR (default)
    
    public static final String PDF_MODE_TEXT_ONLY="TEXT_ONLY";
    public static final String PDF_MODE_OCR_ONLY="OCR_ONLY";
    public static final String PDF_MODE_TEXT_AND_OCR="TEXT_AND_OCR";
    
    
    private static Logger logger = Logger.getLogger(OCRService.class.getName());

    @Inject
    @ConfigProperty(name = ENV_OCR_SERVICE_ENDPOINT)
    Optional<String> serviceEndpoint;

    @Inject
    @ConfigProperty(name = ENV_OCR_PDF_MODE, defaultValue = PDF_MODE_TEXT_AND_OCR)
    String pdfMode;

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
    public void extractText(ItemCollection workitem, ItemCollection snapshot) throws PluginException {
        extractText(workitem, snapshot, pdfMode, null);
    }

    /**
     * Extracts the textual information from document attachments.
     * <p>
     * The method extracts the textual content for each new file attachment of a
     * given workitem. The text information is stored in the $file attribute 'text'.
     * <p>
     * For PDF files with textual content the method calls the method
     * 'extractTextFromPDF' using the PDFBox api. In other cases, the method sends
     * the content via a Rest API to the tika server for OCR processing.
     * <p>
     * The method also extracts files already stored in a snapshot workitem. In this
     * case the method tests if the $file attribute 'text' already exists.
     * 
     * @param workitem - workitem with file attachments
     * @param pdf_mode - TEXT_ONLY, OCR_ONLY, TEXT_AND_OCR
     * @param options  - optional tika header params
     * @throws PluginException
     */
    public void extractText(ItemCollection workitem, ItemCollection snapshot, String pdf_mode, List<String> options)
            throws PluginException {
        boolean debug = logger.isLoggable(Level.FINE);

        // overwrite ocrmode?
        if (pdf_mode != null) {
            this.pdfMode = pdf_mode;
        }

        // validate OCR MODE....
        if ("TEXT_ONLY, OCR_ONLY, TEXT_AND_OCR".indexOf(pdfMode) == -1) {
            throw new PluginException(OCRService.class.getSimpleName(), PLUGIN_ERROR,
                    "Invalid TIKA_OCR_MODE - expected one of the following options: TEXT_ONLY | OCR_ONLY | TEXT_AND_OCR");
        }

        long l = System.currentTimeMillis();
        // List<ItemCollection> currentDmsList = DMSHandler.getDmsList(workitem);
        List<FileData> files = workitem.getFileData();

        for (FileData fileData : files) {

            // do we need to parse the content?
            if (!hasOCRContent(fileData)) {
                // yes - fetch the origin fileData object....
                FileData originFileData = fetchOriginFileData(fileData, snapshot);
                if (originFileData != null) {
                    String ocrContent = null;
                    // extract the text content...
                    try {
                        if (debug) {
                            logger.fine("...text extraction '" + originFileData.getName() + "'...");
                        }
                        // test for simple text extraction via PDFBox
                        if (isPDF(originFileData)) {
                            
                            if (PDF_MODE_OCR_ONLY.equals(pdfMode)) {
                                // OCR Only
                                if (debug) {
                                    logger.fine("...force orc scan for pdfs...");
                                }
                                ocrContent = doORCProcessing(originFileData, options);
                            } else {
                                // try PDFBox....
                                ocrContent = doPDFTextExtraction(originFileData);
                                // if we have not a meaningful content we discard the result and try the tika
                                // api...
                                if (ocrContent != null && ocrContent.length() < 16) {
                                    ocrContent = null;
                                }

                                if (ocrContent == null && (PDF_MODE_TEXT_AND_OCR.equals(pdfMode))) {
                                    // lets try it with OCR...
                                    ocrContent = doORCProcessing(originFileData, options);
                                }
                            }
                        } else {
                            // for all other files than PDF we do a ocr scann if not PDF_ONLY mode
                            if (!PDF_MODE_TEXT_ONLY.equals(pdfMode)) {
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
                        fileData.setAttribute(FILE_ATTRIBUTE_TEXT, list);

                    } catch (IOException e) {
                        throw new PluginException(OCRService.class.getSimpleName(), PLUGIN_ERROR,
                                "Unable to scan attached document '" + fileData.getName() + "'", e);
                    }
                }
            }

        }
        if (debug) {
            logger.fine("...extracted textual information in " + (System.currentTimeMillis() - l) + "ms");
        }
    }

    /**
     * This method returns true if a given FileData object has already a ocr content
     * object stored. This can be verified by the existence of the 'text' attribute.
     * 
     * @param fileData - fileData object to be verified
     * @return
     */
    @SuppressWarnings("unchecked")
    private boolean hasOCRContent(FileData fileData) {
        if (fileData != null) {
            List<String> ocrContentList = (List<String>) fileData.getAttribute(FILE_ATTRIBUTE_TEXT);
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
    private FileData fetchOriginFileData(FileData fileData, ItemCollection snapshot) {
        // test if the given fileData object has a content....
        byte[] fileContent = fileData.getContent();
        if (fileContent != null && fileContent.length > 1) {
            // the fileData object contains the origin content.
            // no snapshot need to be loaded here!
            return fileData;
        }

        // load the snapshot FileData...
        // FileData snapshotFileData =
        // snapshotService.getWorkItemFile(workitem.getUniqueID(), fileData.getName());

        if (snapshot != null) {
            FileData snapshotFileData = snapshot.getFileData(fileData.getName());

            if (snapshotFileData != null) {
                fileContent = snapshotFileData.getContent();
                if (fileContent != null && fileContent.length > 1) {
                    // return the snapshot FileData object
                    return snapshotFileData;
                }
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
        boolean debug = logger.isLoggable(Level.FINE);

        // read the Tika Service Enpoint
        if (!serviceEndpoint.isPresent() || serviceEndpoint.get().isEmpty()) {
            return null;
        }

        if (debug) {
            logger.fine("...ocr scanning....");
        }
        // adapt ContentType
        String contentType = adaptContentType(fileData);

        // validate content type
        if (!acceptContentType(contentType)) {
            if (debug) {
                logger.fine("contentType '" + contentType + " is not supported by Tika Server");
            }
            return null;
        }

        PrintWriter printWriter = null;
        HttpURLConnection urlConnection = null;
        PrintWriter writer = null;
        try {
            urlConnection = (HttpURLConnection) new URL(serviceEndpoint.get()).openConnection();
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
                        logger.warning("Invalid tika option : '" + option + "'  character '=' expeced!");
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
        boolean debug = logger.isLoggable(Level.FINE);

        if (debug) {
            logger.fine("...pdf text extraction....");
        }
        PDDocument doc = null;
        String result = null;
        try {
            doc = PDDocument.load(fileData.getContent());

            PDFTextStripper pdfStripper = new PDFTextStripper();
            // Retrieving text from PDF document
            result = pdfStripper.getText(doc);
            if (debug) {
                logger.finest("<RESULT>" + result + "</RESULT>");
            }

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
        boolean debug = logger.isLoggable(Level.FINE);

        // get content of result
        if (debug) {
            logger.finest("......readResponse....");
        }
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
                if (debug) {
                    logger.finest("......" + inputLine);
                }
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