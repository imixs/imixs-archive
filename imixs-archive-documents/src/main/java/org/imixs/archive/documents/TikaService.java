package org.imixs.archive.documents;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.PluginException;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

/**
 * The OCRService extracts the textual information from document attachments of
 * a workitem and stores the data into the $file attribute 'text'.
 * <p>
 * For the text extraction the services sends the content of a document to an
 * instance of a Apache Tika server via the Rest API. The environment variable
 * OCR_STRATEGY defines how PDF files will be scanned. Possible values are:
 * <ul>
 * <li>AUTO - The best OCR strategy is chosen by the Tika Server itself. This is
 * the default setting.</li>
 * <li>NO_OCR - OCR processing is disabled and text is extracted only from PDF
 * files including a raw text. If a pdf file does not contain raw text data no
 * text will be extracted!</li>
 * <li>OCR_ONLY - PDF files will always be OCR scanned even if the pdf file
 * contains text data.</li>
 * <li>OCR_AND_TEXT_EXTRACTION - OCR processing and raw text extraction is
 * performed. Note: This may result is a duplication of text and the mode is not
 * recommended.</li>
 * <p>
 * The service expects a valid Rest API end-point to an instance of a Tika
 * Server defined by the Environment Parameter 'TIKA_SERVICE_ENDPONT'.
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
public class TikaService {

    public static final String FILE_ATTRIBUTE_TEXT = "text";
    public static final String DEFAULT_ENCODING = "UTF-8";
    // public static final String PLUGIN_ERROR = "PLUGIN_ERROR";
    public static final String API_ERROR = "API_ERROR";
    public static final String DOCUMENT_ERROR = "DOCUMENT_ERROR";
    public static final String ENV_OCR_SERVICE_ENDPOINT = "ocr.service.endpoint";
    public static final String ENV_OCR_SERVICE_MODE = "ocr.service.mode";
    public static final String ENV_OCR_SERVICE_MAXFILESIZE = "ocr.service.maxfilesize";

    public static final String ENV_OCR_STRATEGY = "ocr.strategy"; // NO_OCR, OCR_ONLY, OCR_AND_TEXT_EXTRACTION, AUTO
                                                                  // (default)

    public static final String OCR_STRATEGY_NO_OCR = "NO_OCR";
    public static final String OCR_STRATEGY_OCR_AND_TEXT_EXTRACTION = "OCR_AND_TEXT_EXTRACTION";
    public static final String OCR_STRATEGY_OCR_ONLY = "OCR_ONLY";
    public static final String OCR_STRATEGY_AUTO = "AUTO"; // default

    private static Logger logger = Logger.getLogger(TikaService.class.getName());

    @Inject
    @ConfigProperty(name = ENV_OCR_SERVICE_ENDPOINT)
    Optional<String> serviceEndpoint;

    @Inject
    @ConfigProperty(name = ENV_OCR_STRATEGY, defaultValue = OCR_STRATEGY_AUTO)
    String ocrStategy;

    // Maximum size of bytes to be scanned (default is 5MB)
    @Inject
    @ConfigProperty(name = ENV_OCR_SERVICE_MAXFILESIZE, defaultValue = "5242880")
    int ocrMaxFileSize;

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
     * @throws AdapterException
     */
    public void extractText(ItemCollection workitem, ItemCollection snapshot) throws PluginException, AdapterException {
        extractText(workitem, snapshot, ocrStategy, null, null, 0);
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
     * <p>
     * An optional param 'filePattern' can be provided to extract text only from
     * Attachments mating the given file pattern (regex).
     * <p>
     * The optioanl param 'maxPages' can be provided to reduce the size of PDF
     * documents to a maximum of pages. This avoids blocking the tika service by
     * processing to large documetns. For example only the first 5 pages can be
     * scanned.
     * 
     * @param workitem         - workitem with file attachments
     * @param pdf_mode         - TEXT_ONLY, OCR_ONLY, TEXT_AND_OCR
     * @param options          - optional tika header params
     * @param filePatternRegex - optional regular expression to match files
     * @throws PluginException
     * @throws AdapterException
     */
    public void extractText(ItemCollection workitem, ItemCollection snapshot, String _ocrStategy, List<String> options,
            String filePatternRegex, int maxPdfPages) throws PluginException, AdapterException {
        boolean debug = logger.isLoggable(Level.FINE);
        Pattern filePattern = null;

        if (options == null) {
            options = new ArrayList<String>();
        }

        // overwrite ocrmode?
        if (_ocrStategy != null) {
            this.ocrStategy = _ocrStategy;
        }

        // validate OCR MODE....
        if ("AUTO, NO_OCR, OCR_ONLY, OCR_AND_TEXT_EXTRACTION".indexOf(ocrStategy) == -1) {
            throw new PluginException(TikaService.class.getSimpleName(), API_ERROR,
                    "Invalid TIKA_OCR_MODE - expected one of the following options: NO_OCR | OCR_ONLY | OCR_AND_TEXT_EXTRACTION");
        }

        // if the options did not already include the X-Tika-PDFOcrStrategy than we add
        // it now...
        boolean hasPDFOcrStrategy = options.stream()
                .anyMatch(s -> s.toLowerCase().startsWith("X-Tika-PDFOcrStrategy=".toLowerCase()));
        if (!hasPDFOcrStrategy) {
            // we do need to set a OcrStrategy from the environment...
            options.add("X-Tika-PDFOcrStrategy=" + ocrStategy);
        }

        // print tika options...
        if (debug) {
            logger.info("......  filepattern = " + filePatternRegex);
            for (String opt : options) {
                logger.info("......  Tika Option = " + opt);
            }
        }

        // do we have a file pattern?
        if (filePatternRegex != null && !filePatternRegex.isEmpty()) {
            filePattern = Pattern.compile(filePatternRegex);
        }

        long l = System.currentTimeMillis();
        // List<ItemCollection> currentDmsList = DMSHandler.getDmsList(workitem);
        List<FileData> files = workitem.getFileData();

        if (debug) {
            logger.info("... found " + files.size() + " files");
        }

        for (FileData fileData : files) {
            logger.fine("... processing file: " + fileData.getName());
            // do we have an optional file pattern?
            if (filePattern != null && !filePattern.matcher(fileData.getName()).find()) {
                // the file did not match the given pattern!
                logger.info("... filename does not match given pattern!");
                continue;
            }

            // do we need to parse the content?
            if (!hasOCRContent(fileData)) {
                if (debug) {
                    logger.info("... workitem has not OCRContent - fetching origin file data...");
                }
                // yes - fetch the origin fileData object....
                FileData originFileData = fetchOriginFileData(fileData, snapshot);
                if (originFileData != null) {
                    String textContent = null;
                    // extract the text content...
                    try {
                        // if the size of the file is greater then ENV_OCR_SERVICE_MAXFILESIZE,
                        // we ignore the file!
                        if (originFileData.getContent() != null
                                && originFileData.getContent().length > ocrMaxFileSize) {
                            throw new AdapterException(TikaService.class.getSimpleName(), DOCUMENT_ERROR,
                                    "The file '" + fileData.getName() + "' exceed the allowed max size of "
                                            + ocrMaxFileSize + " bytes (file size=" + originFileData.getContent().length
                                            + ")");
                        }
                        if (debug) {
                            logger.info("...text extraction '" + originFileData.getName() + "' content size="
                                    + originFileData.getContent().length + " ...");
                        }

                        textContent = doORCProcessing(originFileData, options, maxPdfPages);

                        if (textContent == null) {
                            logger.warning("Unable to extract text-content for '" + fileData.getName() + "'");
                            textContent = "";
                        }
                        // store the ocrContent....
                        List<Object> list = new ArrayList<Object>();
                        list.add(textContent);
                        fileData.setAttribute(FILE_ATTRIBUTE_TEXT, list);

                    } catch (IOException e) {
                        throw new PluginException(TikaService.class.getSimpleName(), API_ERROR,
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
    public String doORCProcessing(FileData fileData, List<String> options, int maxPdfPages) throws IOException {
        boolean debug = logger.isLoggable(Level.FINE);

        // read the Tika Service Enpoint
        if (!serviceEndpoint.isPresent() || serviceEndpoint.get().isEmpty()) {
            logger.severe(
                    "OCR_SERVICE_ENDPOINT is missing - OCR processing not supported without a valid tika server endpoint!");
            return null;
        }

        if (debug) {
            logger.info("...ocr scanning of document " + fileData.getName() + " ....");
        }
        // adapt ContentType
        String contentType = adaptContentType(fileData);

        // validate content type
        if (!acceptContentType(contentType)) {
            if (debug) {
                logger.info("contentType '" + contentType + " is not supported by Tika Server");
            }
            return null;
        }

        // remove pages if page size of a pdf document exceeds the max_pagesize
        if (maxPdfPages > 0 && "application/pdf".equals(contentType)) {
            PDDocument pdfdoc = PDDocument.load(fileData.getContent());
            if (pdfdoc.getNumberOfPages() > maxPdfPages) {
                logger.warning("......pdf document '" + fileData.getName() + "' has to many pages (max allowed="
                        + maxPdfPages + ")");
                while (pdfdoc.getNumberOfPages() > maxPdfPages) {
                    logger.warning("......removing page " + pdfdoc.getNumberOfPages());
                    pdfdoc.removePage(pdfdoc.getNumberOfPages() - 1);
                }
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                pdfdoc.save(byteArrayOutputStream);
                pdfdoc.close();
                // update fileData content....
                fileData.setContent(byteArrayOutputStream.toByteArray());
            }
        }

        PrintWriter printWriter = null;
        HttpURLConnection urlConnection = null;
        PrintWriter writer = null;
        try {
            if (debug) {
                logger.info("... sending OCR Request: " + serviceEndpoint.get());
            }
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
            if (debug) {
                logger.info("... response code=" + resposeCode);
            }
            if (resposeCode >= 200 && resposeCode <= 299) {
                logger.info("...call readResponse....");
                return readResponse(urlConnection, DEFAULT_ENCODING, debug);
            }

            logger.warning("... no data!");
            // no data!
            return null;

        } finally {
            // Release current connection
            if (printWriter != null)
                printWriter.close();
        }
    }

    /**
     * This method returns true if a given FileData object has already a ocr content
     * object stored. This can be verified by the existence of the 'text' attribute.
     * <p>
     * The text attribute must not be empty.
     * 
     * @param fileData - fileData object to be verified
     * @return
     */
    @SuppressWarnings("unchecked")
    private boolean hasOCRContent(FileData fileData) {
        if (fileData != null) {
            List<String> ocrContentList = (List<String>) fileData.getAttribute(FILE_ATTRIBUTE_TEXT);
            // do we have a value list at all?
            // Issue #166
            if (ocrContentList == null || ocrContentList.size() == 0) {
                // no attribute found
                return false;
            }

            // test the text value ....
            String textValue = ocrContentList.get(0);
            if (textValue == null || textValue.isEmpty()) {
                return false;
            }

            // else we do have a content!
            return true;
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
     * Reads the response from a http request.
     * 
     * @param urlConnection
     * @throws IOException
     */
    private String readResponse(URLConnection urlConnection, String encoding, boolean debug) throws IOException {

        // get content of result
        if (debug) {
            logger.info("......readResponse....");
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

            if (debug) {
                logger.info("......ContentEncoding=" + sContentEncoding);
            }

            // if an encoding is provided read stream with encoding.....
            if (sContentEncoding != null && !sContentEncoding.isEmpty())
                in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), sContentEncoding));
            else
                in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (debug) {
                    logger.info("......" + inputLine);
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

}