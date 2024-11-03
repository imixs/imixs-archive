package org.imixs.archive.documents;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
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
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.imixs.archive.core.SnapshotService;
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
import org.xml.sax.SAXException;

import jakarta.inject.Inject;

/**
 * The EInvoiceAdapter can detect and extract content from e-invoice documents
 * in different formats.
 * <p>
 * The detection outcome of the adapter is a new item named 'einvoice.type'
 * with the detected type of the e-invoice format. E.g:
 * 
 * - Factur-X/ZUGFeRD 2.0
 * 
 * The Adapter can be configured by the BPMN event to extract e-invoice data
 * fields
 * <p>
 * Example e-invoice configuration:
 * 
 * <pre>
 * {@code
 
	  <e-invoice name="ENTITY">
		<name>invoice.date</name>
		<type>date</type>
		<xpath>//rsm:CrossInvoice/ram:ID</xpath>
	  </e-invoice>

 * }
 * </pre>
 * 
 * If the type is not set the item value will be treated as a String. Possible
 * types are 'double' and 'date'
 * <p>
 * If the document is not a e-invoice no items and also the einvoice.type
 * field will be set.
 * 
 * @author rsoika
 * @version 2.0
 * 
 */
public class EInvoiceAdapter implements SignalAdapter {
	private static Logger logger = Logger.getLogger(EInvoiceAdapter.class.getName());

	public static final String E_INVOICE_ENTITY = "ENTITY";
	public static final String FILE_ATTRIBUTE_XML = "xml";
	public static final String FILE_ATTRIBUTE_EINVOICE_TYPE = "einvoice.type";

	public static final String PARSING_EXCEPTION = "PARSING_EXCEPTION";
	public static final String PLUGIN_ERROR = "PLUGIN_ERROR";
	public static final String REPORT_ERROR = "REPORT_ERROR";

	private static final Pattern PDF_PATTERN = Pattern.compile(".[pP][dD][fF]$");
	private static final Pattern XML_PATTERN = Pattern.compile(".[xX][mM][lL]$");
	private static final Pattern ZIP_PATTERN = Pattern.compile(".[zZ][iI][pP]$");

	public static final Map<String, String> NAMESPACES = new HashMap<>();

	static {
		NAMESPACES.put("rsm", "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100");
		NAMESPACES.put("ram", "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100");
		NAMESPACES.put("udt", "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100");
		// Add more namespaces as needed
	}

	public static String PROCESSING_ERROR = "PROCESSING_ERROR";
	public static final String CONFIG_ERROR = "CONFIG_ERROR";

	private XPath xpath = null;
	private Document xmlDoc = null;

	@Inject
	DocumentService documentService;

	@Inject
	WorkflowService workflowService;

	@Inject
	SnapshotService snapshotService;

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

		List<ItemCollection> entityDefinitions = workflowService.evalWorkflowResultXML(event, "e-invoice",
				E_INVOICE_ENTITY, workitem, false);

		// Detect and read E-Invoice Data
		if (entityDefinitions != null && entityDefinitions.size() > 0) {

			FileData eInvoiceFileData = detectEInvoice(workitem);
			if (eInvoiceFileData == null) {
				logger.info("No e-invoice type detected.");
				return workitem;
			} else {
				String einvoiceType = detectEInvoiceType(eInvoiceFileData);
				workitem.setItemValue(FILE_ATTRIBUTE_EINVOICE_TYPE, einvoiceType);
				logger.info("Detected e-invoice type: " + einvoiceType);
				// ItemCollection itemDefinition = entityDefinitions.get(0);
				// List<String> entities = itemDefinition.getItemValueList("entity",
				// String.class);
				readEInvoiceContent(eInvoiceFileData, entityDefinitions, workitem);
			}

		}

		return workitem;
	}

	/**
	 * Detects the first e-invoice from the attached files in the workitem.
	 * It first checks for PDF files with embedded XML, then standalone XML files.
	 * The method returns the FileData Object with the new attributes
	 * 'einvoice.type' and 'xml'
	 *
	 * @param workitem The workitem containing the attachments to analyze
	 * @return The detected e-invoice type, or null if not detected
	 * @throws PluginException If there's an error in processing the attachments
	 */
	public FileData detectEInvoice(ItemCollection workitem) throws PluginException {

		FileData xmlFileData = getXMLFileData(workitem, PDF_PATTERN);
		if (xmlFileData != null) {
			analyzeXMLContent(xmlFileData);
			return xmlFileData;
		}

		// text XML....
		xmlFileData = getXMLFileData(workitem, XML_PATTERN);
		if (xmlFileData != null) {
			analyzeXMLContent(xmlFileData);
			return xmlFileData;
		}

		xmlFileData = getXMLFromZip(workitem);
		if (xmlFileData != null) {
			analyzeXMLContent(xmlFileData);
			return xmlFileData;
		}

		return null;
	}

	/**
	 * This method detects the einvoice.type of a given FileData
	 * 
	 * @param workitem
	 * @return
	 * @throws PluginException
	 */
	public static String detectEInvoiceType(FileData fileData) throws PluginException {
		@SuppressWarnings("unchecked")
		List<Object> list = (List<Object>) fileData.getAttribute(FILE_ATTRIBUTE_EINVOICE_TYPE);
		if (list == null || list.size() == 0) {
			return null;
		}

		return list.get(0).toString();
	}

	/**
	 * Stores a XML Content into the FileData attribute 'xml'
	 * 
	 * @param fileData
	 * @param xmlData
	 */
	@SuppressWarnings("unchecked")
	private void storeXMLContent(FileData fileData, byte[] xmlData) {
		// store the xmlContent....
		List<Object> list = (List<Object>) fileData.getAttribute(FILE_ATTRIBUTE_XML);
		if (list == null) {
			list = new ArrayList<Object>();
		}
		list.add(xmlData);
		fileData.setAttribute(FILE_ATTRIBUTE_XML, list);
	}

	/**
	 * Reads the XML Content from the FileData attribute 'xml'
	 * 
	 * @param fileData
	 * @param xmlData
	 */
	@SuppressWarnings("unchecked")
	public byte[] readXMLContent(FileData fileData) {
		// store the ocrContent....
		List<Object> list = (List<Object>) fileData.getAttribute(FILE_ATTRIBUTE_XML);
		if (list != null) {
			return (byte[]) list.get(0);
		}
		return null;
	}

	private FileData getXMLFromZip(ItemCollection workitem) throws PluginException {
		List<String> filenames = workitem.getFileNames();
		for (String filename : filenames) {
			if (ZIP_PATTERN.matcher(filename).find()) {
				logger.info("Extracting XML from ZIP file: " + filename);

				FileData fileData = workitem.getFileData(filename);
				byte[] fileContent = fileData.getContent();
				if (snapshotService != null) {
					FileData snapShotFileData = snapshotService.getWorkItemFile(workitem.getUniqueID(), filename);
					fileContent = snapShotFileData.getContent();
				}
				byte[] xmlData = extractXMLFromZip(fileContent);
				storeXMLContent(fileData, xmlData);
				return fileData;
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
	private String analyzeXMLContent(FileData fileData) {
		byte[] xmlData = readXMLContent(fileData);

		// fileData.getContent();
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

				fileData.setAttribute("einvoice.type", Arrays.asList("Factur-X/ZUGFeRD 2.0"));
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
	 * The Method returns an updated fileData object attached to the workitem even
	 * if the content was fetched from a Snapshot
	 * <p>
	 * If the file is no XML or a PDF without embedded XML the method returns null
	 * 
	 * @param workitem    The ItemCollection containing the attachments
	 * @param filePattern The pattern to match file names
	 * @return The XML content as a byte array, or null if not found
	 * @throws PluginException If there's an error in processing the files
	 */
	private FileData getXMLFileData(ItemCollection workitem, Pattern filePattern) throws PluginException {
		List<String> filenames = workitem.getFileNames();
		for (String filename : filenames) {
			if (filePattern.matcher(filename).find()) {
				logger.info("Extracting embedded XML from '" + filename + "'");
				FileData fileData = workitem.getFileData(filename);
				byte[] fileContent = fileData.getContent();
				if (snapshotService != null) {
					FileData snapShotFileData = snapshotService.getWorkItemFile(workitem.getUniqueID(), filename);
					fileContent = snapShotFileData.getContent();
				}
				byte[] xmlContent = null;
				if (filePattern == PDF_PATTERN) {
					xmlContent = getFirstEmbeddedXML(fileContent);
				}
				if (filePattern == XML_PATTERN) {
					xmlContent = fileContent;
				}
				if (xmlContent != null) {
					storeXMLContent(fileData, xmlContent);
					return fileData;
				}
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
	 * <entity name=
	 * "invoice.number">//rsm:CrossIndustryInvoice/rsm:ExchangedDocument/ram:ID</entity>
	 * 
	 * 
	 * @param xmlData
	 * @return
	 */
	private void readEInvoiceContent(FileData eInvoiceFileData, List<ItemCollection> entityDefinitions,
			ItemCollection workitem) {
		byte[] xmlData = readXMLContent(eInvoiceFileData);

		try {

			createXMLDoc(xmlData);

			// Map<String, XPathExpression> compiledExpressions = new HashMap<>();

			// extract the itemName and the expression from each itemDefinition....
			for (ItemCollection entityDef : entityDefinitions) {

				if (entityDef.getItemValueString("name").isEmpty()
						|| entityDef.getItemValueString("xpath").isEmpty()) {
					logger.warning("Invalid entity definition: " + entityDef);
					continue;
				}
				String itemName = entityDef.getItemValueString("name");
				String xPathExpr = entityDef.getItemValueString("xpath");
				String itemType = entityDef.getItemValueString("type");

				readItem(workitem, xPathExpr, itemType, itemName);
				// XPathExpression expr = compiledExpressions.computeIfAbsent(xPathExpr,
				// k -> {
				// try {
				// return xpath.compile(k);
				// } catch (Exception e) {
				// logger.warning("Error compiling XPath expression: " + k + " - " +
				// e.getMessage());
				// return null;
				// }
				// });
				// // extract the xpath value and update the workitem...
				// if (expr != null) {
				// Node node = (Node) expr.evaluate(xmlDoc, XPathConstants.NODE);
				// String itemValue = node != null ? node.getTextContent() : null;
				// // test if we have a type....

				// if ("date".equalsIgnoreCase(itemType)) {
				// SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
				// try {
				// Date invoiceDate = formatter.parse(itemValue);
				// workitem.setItemValue(itemName, invoiceDate);
				// } catch (ParseException e) {
				// e.printStackTrace();
				// }
				// } else if ("double".equalsIgnoreCase(itemType)) {
				// workitem.setItemValue(itemName, Double.parseDouble(itemValue));
				// } else {
				// // default...
				// workitem.setItemValue(itemName, itemValue);
				// }
				// }
			}
		} catch (Exception e) {
			logger.warning("Error analyzing XML content: " + e.getMessage());
		}
	}

	/**
	 * Returns a XPath instance to be used to resolve xpath expressions.
	 * 
	 * The method uses a cache
	 * 
	 * @return
	 */
	private void createXPath() {
		if (xpath == null) {

			XPathFactory xPathfactory = XPathFactory.newInstance();
			xpath = xPathfactory.newXPath();

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
		}
	}

	/**
	 * Creates the XML document instance based on a XML content
	 * 
	 * @param xmlData
	 * @throws PluginException
	 */
	public void createXMLDoc(byte[] xmlData) throws PluginException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder;
		try {
			builder = factory.newDocumentBuilder();
			xmlDoc = builder.parse(new ByteArrayInputStream(xmlData));
		} catch (ParserConfigurationException | SAXException | IOException e) {
			throw new PluginException(EInvoiceAdapter.class.getSimpleName(), PARSING_EXCEPTION,
					"Failed to parse XML Content: " + e.getMessage(), e);
		}

	}

	/**
	 * Reads a single item from an e-invoice document based on a xPathExp
	 * 
	 * @param workitem
	 * @param xPathExpr
	 * @param itemType
	 * @param itemName
	 * @throws PluginException
	 */
	public void readItem(ItemCollection workitem, String xPathExpr, String itemType,
			String itemName) throws PluginException {

		if (xmlDoc == null) {
			logger.warning("Missing XML Doc !");
			return;
		}
		createXPath();
		XPathExpression expr = null;

		try {
			expr = xpath.compile(xPathExpr);

			// extract the xpath value and update the workitem...
			if (expr != null) {
				Node node = (Node) expr.evaluate(xmlDoc, XPathConstants.NODE);
				String itemValue = node != null ? node.getTextContent() : null;
				// test if we have a type....
				if ("date".equalsIgnoreCase(itemType)) {
					SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
					try {
						Date invoiceDate = formatter.parse(itemValue);
						workitem.setItemValue(itemName, invoiceDate);
					} catch (ParseException e) {
						e.printStackTrace();
					}
				} else if ("double".equalsIgnoreCase(itemType)) {
					workitem.setItemValue(itemName, Double.parseDouble(itemValue));
				} else {
					// default...
					workitem.setItemValue(itemName, itemValue);
				}
			}
		} catch (XPathExpressionException e) {
			throw new PluginException(EInvoiceAdapter.class.getSimpleName(), PARSING_EXCEPTION,
					"Error compiling XPath expression: " + xPathExpr + " - " + e.getMessage(), e);
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
