package org.imixs.archive.documents;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.logging.Logger;

import javax.xml.transform.TransformerException;

import org.imixs.workflow.xml.XSLHandler;

/**
 * The XSLTester is used in junit tests to transform XSL/XML files
 * 
 * Do not forget to change your pom.xml and add the following to your build
 * section
 * 
 * <pre>
 * {@code		
 * <testResources>
 *   <testResource>
 *     <directory>${basedir}/../reports</directory>
 *   </testResource>
 *   <testResource>
 *     <directory>${basedir}/src/test/resources</directory>
 *   </testResource>
 * </testResources>
 * }
 * </pre>
 * 
 * @author rsoika
 */
public class XSLTester {

	final static String ENCODING = "UTF-8";
	private static Logger logger = Logger.getLogger(XSLTester.class.getName());

	/**
	 * XSL Transformation
	 * 
	 * @throws IOException
	 * @throws TransformerException
	 */
	public static void transform(String xmlSourcePath, String xslSourcePath, String outputPath)
			throws IOException, TransformerException {

		logger.info("XML Path: " + xmlSourcePath);
		logger.info("XSL Path: " + xslSourcePath);
		logger.info("Output Path: " + outputPath);

		OutputStream outputStream = new FileOutputStream(outputPath);
		// Use the class loader to load the XML file as a resource
		ClassLoader classLoader = XSLTester.class.getClassLoader();
		InputStream xmlInputStream = classLoader.getResourceAsStream(xmlSourcePath);
		InputStream xslInputStream = classLoader.getResourceAsStream(xslSourcePath);

		// InputStream xmlInputStream =
		// XSLTester.class.getClassLoader().getResourceAsStream(xmlSourcePath);
		// InputStream xslInputStream =
		// XSLTester.class.getClassLoader().getResourceAsStream(xslSourcePath)) {

		try {

			if (xmlInputStream == null) {
				throw new RuntimeException("XML file not found: " + xmlSourcePath);
			}
			if (xslInputStream == null) {
				throw new RuntimeException("XSL file not found: " + xslSourcePath);
			}

			String xmlSource = readXml(xmlInputStream);
			String xslSource = readXml(xslInputStream);
			XSLHandler.transform(xmlSource, xslSource, ENCODING, outputStream);

		} finally {
			if (xmlInputStream != null)
				xmlInputStream.close();
			if (xslInputStream != null)
				xslInputStream.close();
			if (outputStream != null)
				outputStream.close();
		}
	}

	/**
	 * read and return the XML content as a String
	 * 
	 */
	private static String readXml(InputStream inputStream) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			StringBuilder xmlContent = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				xmlContent.append(line).append("\n");
			}
			return xmlContent.toString();
		} catch (IOException e) {
			throw new RuntimeException("Error reading XML file", e);
		}
	}
}
