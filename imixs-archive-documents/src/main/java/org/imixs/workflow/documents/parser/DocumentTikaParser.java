package org.imixs.workflow.documents.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Logger;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.imixs.workflow.FileData;
import org.xml.sax.SAXException;

/**
 * The DocumentParser is used to parse the content for .pdf, .doc, .xls, .ppt or
 * .docx files.
 * 
 * The parser uses the Apache libraries POI and PDFBox.
 * 
 * @version 1.0
 * @author rsoika
 */
public class DocumentTikaParser {

	private static Logger logger = Logger.getLogger(DocumentTikaParser.class.getName());

	/**
	 * If the content is a .pdf, .doc, .xls, .ppt or .docx the content will be
	 * parsed and returned as a string.
	 * 
	 * @throws IOException
	 * @throws TikaException
	 * @throws SAXException
	 * 
	 */
	public static String parse(FileData fileData) throws IOException, SAXException, TikaException {

		byte[] data=fileData.getContent();
		if (data.length > 0) {
			logger.fine("parsing document content...");
			AutoDetectParser parser = new AutoDetectParser();
			BodyContentHandler handler = new BodyContentHandler();
			Metadata metadata = new Metadata();

			ByteArrayInputStream bis = new ByteArrayInputStream(data);
			parser.parse(bis, handler, metadata);
			return handler.toString();
		}
		return null;

	}

}