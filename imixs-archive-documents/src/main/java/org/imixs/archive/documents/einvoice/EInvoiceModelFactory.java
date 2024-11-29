package org.imixs.archive.documents.einvoice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * The EInvoiceModelFactory can be used to load or create a EInvoiceModel
 * instance.
 * 
 * The factory detects the XML format and loads the EInvoiceModel either by the
 * EInvoiceModelCII or the EInvoiceModelUBL
 * 
 * @author rsoika
 *
 */
public class EInvoiceModelFactory {
    private static Logger logger = Logger.getLogger(EInvoiceModelFactory.class.getName());

    /**
     * Reads a EInvoiceModel instance from an java.io.File
     * 
     * @param modelFile
     * @return a EInvoiceModel instance
     * @throws FileNotFoundException
     * 
     */
    public static EInvoiceModel read(File modelFile) throws FileNotFoundException {
        return read(new FileInputStream(modelFile));
    }

    /**
     * Reads a EInvoiceModel instance from an given file path
     * 
     * @param modelFile
     * @return a EInvoiceModel instance
     * @throws FileNotFoundException
     */
    public static EInvoiceModel read(String modelFilePath) throws FileNotFoundException {
        return read(EInvoiceModel.class.getResourceAsStream(modelFilePath));
    }

    /**
     * Reads a EInvoiceModel instance from an InputStream and detect the e-invoice
     * format.
     * <p>
     * The method uses the corresponding model implementation to read a
     * EInvoiceModel
     * 
     * @param modelFile
     * @return a EInvoiceModel instance
     * @throws FileNotFoundException
     */
    public static EInvoiceModel read(InputStream is) throws FileNotFoundException {
        logger.fine("read from inputStream...");
        if (is == null) {
            throw new NullPointerException(
                    "Model can not be parsed: InputStream is null");
        }
        DocumentBuilderFactory docFactory = DocumentBuilderFactory
                .newInstance();
        docFactory.setIgnoringElementContentWhitespace(true); // because of a bug this does not have
                                                              // any effect!
        docFactory.setNamespaceAware(true);

        try {
            if (is.available() == 0) {
                logger.warning("Empty BPMN file - creating a default process");
                throw new IOException(
                        "Model can not be parsed: No Content");
            }

            // parse XML file
            DocumentBuilder db = docFactory.newDocumentBuilder();
            // read from a project's resources folder
            Document doc = db.parse(is);
            Element root = doc.getDocumentElement();

            EInvoiceModel model = null;
            String rootName = root.getTagName();
            if ("rsm:CrossIndustryInvoice".equals(rootName)) {
                model = new EInvoiceModelCII(doc);
            } else {
                model = new EInvoiceModelUBL(doc);
            }

            return model;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            logger.severe(e.getMessage());
            // create a runtimeException to show a error message in the client
            throw new RuntimeException(e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
