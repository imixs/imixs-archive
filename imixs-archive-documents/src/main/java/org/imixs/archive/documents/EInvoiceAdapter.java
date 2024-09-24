package org.imixs.archive.documents;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.SignalAdapter;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.engine.WorkflowService;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.PluginException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import jakarta.inject.Inject;

/**
 * The EInvoiceAdapter can detect and extract content from e-invoice documents
 * in different formats.
 * <p>
 * The detection outcome of the adapter is a new item named 'einvoice.format'
 * with the
 * type of the e-invoice format:
 * 
 * - zugferd
 * - xrechnung
 * 
 * 
 * The Adapter can be configured by the BPMN event either to detect the
 * e-invoice
 * type (DETECT) or extract e-invoice data fields (READ)
 * <p>
 * Example e-invoice configuration:
 * 
 * <pre>
 * {@code
        <e-invoice name="DETECT">
        </e-invoice>
 
        <e-invoice name="READ">
            <item>invoice.number=</item>
            <result-event>JSON</result-event>
        </e-invoice>	
 * }
 * </pre>
 * 
 * In 'READ' mode the adapter expects item elements with a itemname followed by
 * a xPath expression
 * 
 * @author rsoika
 * @version 2.0
 * 
 */
public class EInvoiceAdapter implements SignalAdapter {
	private static Logger logger = Logger.getLogger(EInvoiceAdapter.class.getName());

	public static final String E_INVOICE_DETECT = "DETECT";
	public static final String E_INVOICE_READ = "READ";

	public static final String PARSING_EXCEPTION = "PARSING_EXCEPTION";
	public static final String PLUGIN_ERROR = "PLUGIN_ERROR";
	public static final String REPORT_ERROR = "REPORT_ERROR";

	private static final Pattern PDF_PATTERN = Pattern.compile(".[pP][dD][fF]$");
	private static final Pattern XML_PATTERN = Pattern.compile(".[xX][mM][lL]$");
	private static final Pattern ZIP_PATTERN = Pattern.compile(".[zZ][iI][pP]$");

	private static final Map<String, String> NAMESPACES = new HashMap<>();

	static {
		NAMESPACES.put("rsm", "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100");
		NAMESPACES.put("ram", "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100");
		// Add more namespaces as needed
	}

	public static String PROCESSING_ERROR = "PROCESSING_ERROR";
	public static final String CONFIG_ERROR = "CONFIG_ERROR";

	@Inject
	DocumentService documentService;

	@Inject
	private WorkflowService workflowService;

	/**
	 * Executes the e-invoice detection process on the given workitem.
	 * It attempts to detect the e-invoice format from attached files and
	 * updates the workitem with the result.
	 *
	 * @param workitem The workitem to process
	 * @param event    The event triggering this execution
	 * @return The updated workitem
	 * @throws AdapterException If there's an error in the adapter execution
	 * @throws PluginException  If there's an error in plugin processing
	 */
	@Override
	public ItemCollection execute(ItemCollection workitem, ItemCollection event)
			throws AdapterException, PluginException {

		List<ItemCollection> detectDefinitions = workflowService.evalWorkflowResultXML(event, "e-invoice",
				E_INVOICE_DETECT, workitem, false);
		List<ItemCollection> readDefinitions = workflowService.evalWorkflowResultXML(event, "e-invoice",
				E_INVOICE_READ, workitem, false);

		// Detect E-Invoice
		if (detectDefinitions != null && detectDefinitions.size() > 0) {
			String einvoiceFormat = detectEInvoiceFormat(workitem);

			if (einvoiceFormat != null) {
				workitem.setItemValue("einvoice.format", einvoiceFormat);
				logger.info("Detected e-invoice format: " + einvoiceFormat);
			} else {
				logger.info("No e-invoice format detected.");
			}
		}

		// Read E-Invoice Data
		if (readDefinitions != null && readDefinitions.size() > 0) {
			ItemCollection itemDefinition = readDefinitions.get(0);
			List<String> items = itemDefinition.getItemValueList("item", String.class);
			readEInvoiceContent(workitem, items);
		}

		return workitem;
	}

	/**
	 * Detects the e-invoice format from the attached files in the workitem.
	 * It first checks for PDF files with embedded XML, then standalone XML files.
	 *
	 * @param workitem The workitem containing the attachments to analyze
	 * @return The detected e-invoice format, or null if not detected
	 * @throws PluginException If there's an error in processing the attachments
	 */
	public String detectEInvoiceFormat(ItemCollection workitem) throws PluginException {
		byte[] xmlData = getXMLFile(workitem, PDF_PATTERN);
		if (xmlData != null) {
			return analyzeXMLContent(xmlData);
		}

		xmlData = getXMLFile(workitem, XML_PATTERN);
		if (xmlData != null) {
			return analyzeXMLContent(xmlData);
		}

		xmlData = getXMLFromZip(workitem);
		if (xmlData != null) {
			return analyzeXMLContent(xmlData);
		}

		return null;
	}

	private byte[] getXMLFromZip(ItemCollection document) throws PluginException {
		List<String> filenames = document.getFileNames();
		for (String filename : filenames) {
			if (ZIP_PATTERN.matcher(filename).find()) {
				logger.info("Extracting XML from ZIP file: " + filename);
				FileData fileData = document.getFileData(filename);
				return extractXMLFromZip(fileData.getContent());
			}
		}
		return null;
	}

	private byte[] extractXMLFromZip(byte[] zipContent) {
		try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.getName().toLowerCase().endsWith(".xml")) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					byte[] buffer = new byte[1024];
					int len;
					while ((len = zis.read(buffer)) > 0) {
						baos.write(buffer, 0, len);
					}
					return baos.toByteArray();
				}
			}
		} catch (IOException e) {
			logger.warning("Error extracting XML from ZIP: " + e.getMessage());
		}
		return null;
	}

	/**
	 * Analyzes the XML content to determine the specific e-invoice format.
	 * This method should be implemented to distinguish between different
	 * e-invoice formats such as ZUGFeRD or XRechnung.
	 *
	 * @param xmlData The XML content as a byte array
	 * @return The detected e-invoice format
	 */
	private String analyzeXMLContent(byte[] xmlData) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(new ByteArrayInputStream(xmlData));

			Element rootElement = doc.getDocumentElement();
			String rootNamespace = rootElement.getNamespaceURI();
			String rootLocalName = rootElement.getLocalName();

			if ("CrossIndustryInvoice".equals(rootLocalName) &&
					"urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100".equals(rootNamespace)) {
				return "Factur-X/ZUGFeRD 2.0";
			}

			// Add more conditions here for other formats as needed

			return "unknown";
		} catch (Exception e) {
			logger.warning("Error analyzing XML content: " + e.getMessage());
			return "unknown";
		}

	}

	/**
	 * Extracts XML content from attached files matching the given pattern.
	 * For PDF files, it extracts embedded XML. For XML files, it returns the file
	 * content.
	 *
	 * @param document    The ItemCollection containing the attachments
	 * @param filePattern The pattern to match file names
	 * @return The XML content as a byte array, or null if not found
	 * @throws PluginException If there's an error in processing the files
	 */
	private byte[] getXMLFile(ItemCollection document, Pattern filePattern) throws PluginException {
		List<String> filenames = document.getFileNames();
		for (String filename : filenames) {
			if (filePattern.matcher(filename).find()) {
				logger.info("Extracting embedded XML from '" + filename + "'");
				FileData fileData = document.getFileData(filename);
				return filePattern == PDF_PATTERN ? getFirstEmbeddedXML(fileData.getContent()) : fileData.getContent();
			}
		}
		return null;
	}

	/**
	 * Extracts the first embedded XML file from a PDF document.
	 *
	 * @param content The PDF file content as a byte array
	 * @return The embedded XML content as a byte array, or null if not found
	 */
	private byte[] getFirstEmbeddedXML(byte[] content) {
		try (PDDocument doc = PDDocument.load(content)) {
			PDDocumentNameDictionary namesDictionary = new PDDocumentNameDictionary(doc.getDocumentCatalog());
			PDEmbeddedFilesNameTreeNode efTree = namesDictionary.getEmbeddedFiles();
			if (efTree != null) {
				return extractXMLFromNameTreeNode(efTree);
			}
		} catch (IOException e) {
			logger.log(Level.WARNING, "Unable to load embedded XML: " + e.getMessage(), e);
		}
		return null;
	}

	/**
	 * Recursively extracts XML files from a PDEmbeddedFilesNameTreeNode.
	 *
	 * @param efTree The PDEmbeddedFilesNameTreeNode to process
	 * @return The first found XML file content as a byte array, or null if not
	 *         found
	 * @throws IOException If there's an error in reading the embedded files
	 */
	private byte[] extractXMLFromNameTreeNode(PDEmbeddedFilesNameTreeNode efTree) throws IOException {
		Map<String, PDComplexFileSpecification> names = efTree.getNames();
		if (names != null) {
			return extractFirstXMLFile(names);
		}
		for (PDNameTreeNode<PDComplexFileSpecification> node : efTree.getKids()) {
			byte[] result = extractFirstXMLFile(node.getNames());
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	/**
	 * Extracts the first XML file from a map of PDComplexFileSpecifications.
	 *
	 * @param names A map of file names to PDComplexFileSpecifications
	 * @return The content of the first XML file found as a byte array, or null if
	 *         not found
	 * @throws IOException If there's an error in reading the embedded file
	 */
	private byte[] extractFirstXMLFile(Map<String, PDComplexFileSpecification> names) throws IOException {
		for (PDComplexFileSpecification fileSpec : names.values()) {
			String filename = fileSpec.getFile();
			if (XML_PATTERN.matcher(filename).find()) {
				PDEmbeddedFile embeddedFile = getEmbeddedFile(fileSpec);
				if (embeddedFile != null) {
					try (InputStream inStream = embeddedFile.createInputStream()) {
						return streamToByteArray(inStream);
					}
				}
			}
		}
		return null;
	}

	/**
	 * This method reads xpath expressions and set the values into Items of the
	 * given workitem. The method expects a List of Strings containing a item name
	 * followed by a xpath expression.
	 * <p>
	 * Example:
	 * 
	 * invoice.number=//rsm:CrossIndustryInvoice/rsm:ExchangedDocument/ram:ID
	 * invoice.date=//rsm:CrossIndustryInvoice/rsm:ExchangedDocument/ram:IssueDateTime
	 *
	 * 
	 * @param xmlData
	 * @return
	 */
	private void readEInvoiceContent(ItemCollection workitem, List<String> itemDefinitions) {
		byte[] xmlData = null;
		// TODO get the xml data....

		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(new ByteArrayInputStream(xmlData));

			XPathFactory xPathfactory = XPathFactory.newInstance();
			XPath xpath = xPathfactory.newXPath();

			xpath.setNamespaceContext(new NamespaceContext() {
				public String getNamespaceURI(String prefix) {
					return NAMESPACES.get(prefix);
				}

				public String getPrefix(String uri) {
					return null;
				}

				public Iterator<String> getPrefixes(String uri) {
					return null;
				}
			});

			Map<String, XPathExpression> compiledExpressions = new HashMap<>();

			// extract the itemName and the expression from each itemDefinition....
			for (String itemDef : itemDefinitions) {
				String[] parts = itemDef.split("=", 2);
				if (parts.length != 2) {
					logger.warning("Invalid item definition: " + itemDef);
					continue;
				}
				String itemName = parts[0].trim();
				String xPathExpr = parts[1].trim();
				XPathExpression expr = compiledExpressions.computeIfAbsent(xPathExpr,
						k -> {
							try {
								return xpath.compile(k);
							} catch (Exception e) {
								logger.warning("Error compiling XPath expression: " + k + " - " + e.getMessage());
								return null;
							}
						});
				// extract the xpath value and update the workitem...
				if (expr != null) {
					Node node = (Node) expr.evaluate(doc, XPathConstants.NODE);
					String itemValue = node != null ? node.getTextContent() : null;
					workitem.setItemValue(itemName, itemValue);
				}
			}
		} catch (Exception e) {
			logger.warning("Error analyzing XML content: " + e.getMessage());
		}
	}

	/**
	 * Converts an InputStream to a byte array.
	 *
	 * @param ins The InputStream to convert
	 * @return The content of the InputStream as a byte array
	 * @throws IOException If there's an error in reading from the InputStream
	 */
	private static byte[] streamToByteArray(InputStream ins) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int read;
		while ((read = ins.read(buffer)) != -1) {
			baos.write(buffer, 0, read);
		}
		return baos.toByteArray();
	}

	/**
	 * Retrieves the embedded file from a PDComplexFileSpecification,
	 * trying different platform-specific methods.
	 *
	 * @param fileSpec The PDComplexFileSpecification to extract the embedded file
	 *                 from
	 * @return The PDEmbeddedFile if found, or null if not available
	 */
	private static PDEmbeddedFile getEmbeddedFile(PDComplexFileSpecification fileSpec) {
		if (fileSpec != null) {
			return fileSpec.getEmbeddedFileUnicode() != null ? fileSpec.getEmbeddedFileUnicode()
					: fileSpec.getEmbeddedFileDos() != null ? fileSpec.getEmbeddedFileDos()
							: fileSpec.getEmbeddedFileMac() != null ? fileSpec.getEmbeddedFileMac()
									: fileSpec.getEmbeddedFileUnix() != null ? fileSpec.getEmbeddedFileUnix()
											: fileSpec.getEmbeddedFile();
		}
		return null;
	}
}
