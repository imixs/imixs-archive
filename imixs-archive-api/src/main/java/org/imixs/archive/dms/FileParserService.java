package org.imixs.archive.dms;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.ejb.Stateless;

import org.apache.lucene.document.Document;
import org.apache.pdfbox.io.RandomAccessBuffer;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.extractor.PowerPointExtractor;
import org.apache.poi.hssf.extractor.ExcelExtractor;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.imixs.workflow.engine.DocumentService;

/**
 * This service component provides a mechanism to index attachments into lucene.
 * 
 * The service parses the content of .pdf, .doc, .xls, .ppt or .docx files.
 * 
 * The service is called by the DMSService
 * 
 * @version 1.0
 * @author rsoika
 */
@Stateless
public class FileParserService {
	@EJB
	DocumentService documentService;
	private PDFTextStripper stripper = null;
	private static Logger logger = Logger.getLogger(FileParserService.class.getName());

	/**
	 * If the content is a .pdf, .doc, .xls, .ppt or .docx the content will be
	 * parsed and returned as a string.
	 * 
	 */
	public String parse(String fileName, List<?> fileData) {

		String result = null;
		// ...
		String contentType = (String) fileData.get(0);
		byte[] data = (byte[]) fileData.get(1);
		if (data.length > 0) {
			try {

				if (fileName.toLowerCase().endsWith(".pdf")) {
					long l = System.currentTimeMillis();
					logger.fine("Lucene - parsing pdf document '" + fileName + "'.....");

					result = parsePDF(data);

					logger.fine("Lucene - parsing completed in " + (System.currentTimeMillis() - l) + "ms");

				}

				if (fileName.toLowerCase().endsWith(".doc") || fileName.toLowerCase().endsWith(".docx")) {
					long l = System.currentTimeMillis();
					logger.fine("Lucene - parsing MS-DOC document '" + fileName + "'.....");

					result = parseMSDOC(data, fileName);

					logger.fine("Lucene - parsing completed in " + (System.currentTimeMillis() - l) + "ms");

				}

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {

		}
		return result;
	}

	/**
	 * This method parses the text content of a pdf document and adds the contents
	 * to a lucene document.
	 * 
	 * @param document
	 *            The document to add the contents to.
	 * @param is
	 *            The stream to get the contents from.
	 * @param documentLocation
	 *            The location of the document, used just for debug messages.
	 * 
	 * @throws IOException
	 *             If there is an error parsing the document.
	 */
	private String parsePDF(byte[] pdfData) throws IOException {

		RandomAccessRead source = new RandomAccessBuffer(pdfData);
		PDFParser parser = new PDFParser(source);
		parser.parse();
		PDDocument pdfDocument = parser.getPDDocument();

		try {
			// create a writer where to append the text content.
			StringWriter writer = new StringWriter();
			if (stripper == null) {
				stripper = new PDFTextStripper();
			}
			stripper.writeText(pdfDocument, writer);

			// Note: the buffer to string operation is costless;
			// the char array value of the writer buffer and the content string
			// is shared as long as the buffer content is not modified, which will
			// not occur here.
			String contents = writer.getBuffer().toString();

			logger.info("Länge=" + contents.length());
			logger.info(contents);

			return contents;

		} catch (InvalidPasswordException e) {
			// they didn't suppply a password and the default of "" was wrong.
			throw new IOException("Error: The document is encrypted and will not be indexed.", e);
		} finally {
			if (pdfDocument != null) {
				pdfDocument.close();
			}
		}
	}

	/**
	 * parse ms document....
	 * 
	 * 
	 * @param document
	 * @param pdfData
	 * @param fileName
	 * @throws IOException
	 */
	private String parseMSDOC(byte[] pdfData, String fileName) throws IOException {

		String contents = null;
		POIFSFileSystem fs = null;
		try {

			if (fileName.endsWith(".xls")) { // if the file is excel file
				ExcelExtractor ex = new ExcelExtractor(fs);
				contents = ex.getText(); // returns text of the excel file
			} else if (fileName.endsWith(".ppt")) { // if the file is power point file
				PowerPointExtractor extractor = new PowerPointExtractor(fs);
				contents = extractor.getText(); // returns text of the power point file

			} else if (fileName.endsWith(".doc")) {
				ByteArrayInputStream source = new ByteArrayInputStream(pdfData);
				// else for .doc file
				fs = new POIFSFileSystem(source);
				HWPFDocument doc = new HWPFDocument(fs);
				WordExtractor we = new WordExtractor(doc);
				contents = we.getText();// if the extension is .doc

				we.close();
			} else if (fileName.endsWith(".docx")) {
				ByteArrayInputStream source = new ByteArrayInputStream(pdfData);
				XWPFDocument doc = new XWPFDocument(source);
				XWPFWordExtractor we = new XWPFWordExtractor(doc);
				contents = we.getText();// if the extension is .doc

				we.close();
			}
		} catch (Exception e) {
			System.out.println("document file cant be indexed");
		}
		logger.info("Länge=" + contents.length());
		logger.info(contents);

		return contents;
	}

}