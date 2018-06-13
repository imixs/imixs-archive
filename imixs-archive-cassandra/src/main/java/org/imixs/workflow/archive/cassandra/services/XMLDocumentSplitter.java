package org.imixs.workflow.archive.cassandra.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import javax.xml.bind.DataBindingException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.imixs.workflow.xml.XMLDataCollection;
import org.imixs.workflow.xml.XMLDocument;

/**
 * This class is used to split a XMLDocument into a list of 2MB byte arrays.
 * 
 * @author rsoika
 *
 */
public class XMLDocumentSplitter {

	XMLDocument xmlDocument = null;

	public XMLDocumentSplitter(XMLDocument xmlDocument) {
		super();
		this.xmlDocument = xmlDocument;
	}

	public XMLDocument getXmlDocument() {
		return xmlDocument;
	}

	public void setXmlDocument(XMLDocument xmlDocument) {
		this.xmlDocument = xmlDocument;
	}

	public byte[] compress() throws JAXBException {
		byte[] input = this.getBytes();

		// Compressor with highest level of compression
		Deflater compressor = new Deflater();
		compressor.setLevel(Deflater.BEST_COMPRESSION);

		// Give the compressor the data to compress
		compressor.setInput(input);
		compressor.finish();

		// Create an expandable byte array to hold the compressed data.
		// It is not necessary that the compressed data will be smaller than
		// the uncompressed data.
		ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length);

		// Compress the data
		byte[] buf = new byte[1024];
		while (!compressor.finished()) {
			int count = compressor.deflate(buf);
			bos.write(buf, 0, count);
		}
		try {
			bos.close();
		} catch (IOException e) {
		}

		// Get the compressed data
		return bos.toByteArray();
	}

	/**
	 * decompresses a compressed byte array and returns the XMLDocument
	 * representation
	 * 
	 * @param compressedData
	 * @return
	 * @throws JAXBException
	 * @throws DataFormatException
	 */
	public XMLDocument decompress(byte[] compressedData) throws JAXBException, DataFormatException {
		// Create the decompressor and give it the data to compress
		Inflater decompressor = new Inflater();
		decompressor.setInput(compressedData);

		// Create an expandable byte array to hold the decompressed data
		ByteArrayOutputStream bos = new ByteArrayOutputStream(compressedData.length);

		// Decompress the data
		byte[] buf = new byte[1024];
		while (!decompressor.finished()) {
			try {
				int count = decompressor.inflate(buf);
				bos.write(buf, 0, count);
			} catch (DataBindingException e) {
			}
		}
		try {
			bos.close();
		} catch (IOException e) {
		}

		// Get the decompressed data
		byte[] decompressedData = bos.toByteArray();

		JAXBContext context = JAXBContext.newInstance(XMLDocument.class);
		Unmarshaller m = context.createUnmarshaller();

		ByteArrayInputStream input = new ByteArrayInputStream(decompressedData);
		Object jaxbObject = m.unmarshal(input);
		if (jaxbObject == null) {
			throw new RuntimeException("readItemCollection error - wrong xml file format - unable to read content!");
		}

		return (XMLDocument) jaxbObject;
	}

	/**
	 * returns the byte array representation of an xml Document
	 * 
	 * @return
	 * @throws JAXBException
	 */
	public byte[] getBytes() throws JAXBException {
		if (xmlDocument == null) {
			return null;
		}
		StringWriter writer = new StringWriter();
		JAXBContext context = JAXBContext.newInstance(XMLDocument.class);
		Marshaller m = context.createMarshaller();
		m.marshal(xmlDocument, writer);

		String stringResult = writer.toString();
		return stringResult.getBytes();
	}

	public static XMLDataCollection getXMLDataCollectionFromStream(InputStream inputStream)
			throws IOException, JAXBException {
		JAXBContext context = JAXBContext.newInstance(XMLDataCollection.class);
		Unmarshaller m = context.createUnmarshaller();
		Object jaxbObject = m.unmarshal(inputStream);
		if (jaxbObject == null) {
			throw new RuntimeException("readCollection error - wrong xml file format - unable to read content!");
		}
		return (XMLDataCollection) jaxbObject;
	}

	public static byte[] getBytesFromStream(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int nRead;
		byte[] data = new byte[0x4000];
		while ((nRead = is.read(data, 0, data.length)) != -1) {
			buffer.write(data, 0, nRead);
		}
		buffer.flush();
		is.close();
		return buffer.toByteArray();
	}
}
