package org.imixs.workflow.archive.cassandra;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Iterator;
import java.util.zip.DataFormatException;

import javax.xml.bind.JAXBException;

import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.archive.cassandra.services.XMLDocumentSplitter;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test class to test the XMLDocumentSplitter.
 * 
 * 
 * @author rsoika
 * 
 */
public class TestXMLDocumentSplitter {

	/**
	 * This test simply create a dummy workItem with large attachments
	 * 
	 * @throws JAXBException
	 */
	@Test
	public void testBigXMLDocuement() throws JAXBException {

		ItemCollection workitem = WorkitemFactory.createWorkitem();
		WorkitemFactory.addFile(workitem, "file1.dat", 1);
		WorkitemFactory.addFile(workitem, "file2.dat", 2);
		WorkitemFactory.addFile(workitem, "file3.dat", 3);

		Assert.assertNotNull(workitem);

		XMLDocument xmlWorkitem = XMLDocumentAdapter.getDocument(workitem);

		Assert.assertNotNull(xmlWorkitem);

		XMLDocumentSplitter splitter = new XMLDocumentSplitter(xmlWorkitem);
		// create byte array
		byte[] byteResult = splitter.getBytes();

		// we expect more than 6KB of data
		Assert.assertTrue(byteResult.length > (6 * 1024));
		Assert.assertTrue(byteResult.length < (10 * 1024));

	}

	/**
	 * This test simply create a dummy workItem with large attachments
	 * 
	 * @throws JAXBException
	 */
	@Test
	public void testBigXMLDocument1MB() throws JAXBException {

		ItemCollection workitem = WorkitemFactory.createWorkitem();
		WorkitemFactory.addFile(workitem, "file1.dat", 1024);

		Assert.assertNotNull(workitem);
		XMLDocument xmlWorkitem = XMLDocumentAdapter.getDocument(workitem);
		Assert.assertNotNull(xmlWorkitem);

		XMLDocumentSplitter splitter = new XMLDocumentSplitter(xmlWorkitem);
		// create byte array
		byte[] byteResult = splitter.getBytes();
		// we expect more than 6KB of data
		Assert.assertTrue(byteResult.length > (1024 * 1024));

	}

	/**
	 * Test compression
	 * 
	 * @throws JAXBException
	 */
	@Test
	public void testCompression() throws JAXBException {

		ItemCollection workitem = WorkitemFactory.createWorkitem();

		// 1MB file...
		WorkitemFactory.addFile(workitem, "file1.dat", 1024);

		Assert.assertNotNull(workitem);
		XMLDocument xmlWorkitem = XMLDocumentAdapter.getDocument(workitem);
		Assert.assertNotNull(xmlWorkitem);

		XMLDocumentSplitter splitter = new XMLDocumentSplitter(xmlWorkitem);
		// create byte array
		byte[] byteResult = splitter.getBytes();
		// we expect more than 6KB of data
		Assert.assertTrue(byteResult.length > (1024 * 1024 * 1.3));

		byte[] compressedResult = splitter.compress();
		Assert.assertTrue(compressedResult.length < (1024 * 1024 * 1.3));

	}

	/**
	 * Test compression
	 * 
	 * @throws JAXBException
	 * @throws DataFormatException
	 */
	@Test
	public void testDecompression() throws JAXBException, DataFormatException {

		ItemCollection workitem = WorkitemFactory.createWorkitem();

		// 1MB file...
		WorkitemFactory.addFile(workitem, "file1.dat", 1024);

		Assert.assertNotNull(workitem);
		XMLDocument xmlWorkitem = XMLDocumentAdapter.getDocument(workitem);
		Assert.assertNotNull(xmlWorkitem);

		XMLDocumentSplitter splitter = new XMLDocumentSplitter(xmlWorkitem);
		// create byte array
		byte[] byteResult = splitter.getBytes();
		// we expect more than 6KB of data
		Assert.assertTrue(byteResult.length > (1024 * 1024 * 1.3));

		byte[] compressedResult = splitter.compress();
		Assert.assertTrue(compressedResult.length < (1024 * 1024 * 1.3));

		XMLDocument testXMLResult = splitter.decompress(compressedResult);

		ItemCollection testResult = XMLDocumentAdapter.putDocument(testXMLResult);

		Assert.assertEquals(47, testResult.getItemValueInteger("_some_amount"));
		Assert.assertEquals("Hello World", testResult.getItemValueString("_some_text"));

		FileData fileData = workitem.getFileData("file1.dat");

		Assert.assertNotNull(fileData);
		Assert.assertEquals(1024 * 1024, fileData.getContent().length);

	}

	/**
	 * Test compression
	 * 
	 * @throws JAXBException
	 * @throws NoSuchAlgorithmException
	 */
	@Test
	public void testSH1Hash() throws JAXBException, NoSuchAlgorithmException {

		// create a static workitem
		ItemCollection workitem = new ItemCollection();
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.YEAR, 2018);
		cal.set(Calendar.MONTH, 7);
		cal.set(Calendar.DATE, 30);
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.HOUR, 0);

		workitem.replaceItemValue("$created", cal.getTime());
		workitem.replaceItemValue("$modified", cal.getTime());

		workitem.replaceItemValue("_some_text", "Hello World");
		workitem.replaceItemValue("_some_amount", 47);

		workitem.replaceItemValue("_extra_data", "some extra data to be hashed");

		Assert.assertNotNull(workitem);
		XMLDocument xmlWorkitem = XMLDocumentAdapter.getDocument(workitem);
		Assert.assertNotNull(xmlWorkitem);

		XMLDocumentSplitter splitter = new XMLDocumentSplitter(xmlWorkitem);
		// create byte array
		byte[] byteResult = splitter.getBytes();

		String hash = XMLDocumentSplitter.SHAsum(byteResult);

		System.out.println("hash=" + hash);
		Assert.assertEquals("596a727217b3da25241110bc233bb20cd4591c59", hash);
	}

	/**
	 * Test iterator interface
	 * 
	 * This test verifies if a itemCollection is correctly chunked and returned by
	 * the iterator interface
	 * @throws NoSuchAlgorithmException 
	 * 
	 */
	@Test
	public void testIterator1mb() throws NoSuchAlgorithmException {
		// frist create a big itemColection
		ItemCollection workitem = WorkitemFactory.createWorkitem();
		workitem.replaceItemValue("_subject", "The Interator test");
		WorkitemFactory.addFile(workitem, "file1.dat", 1024);

		Assert.assertNotNull(workitem);
		XMLDocument xmlWorkitem = XMLDocumentAdapter.getDocument(workitem);
		Assert.assertNotNull(xmlWorkitem);

		// now test the iterator
		XMLDocumentSplitter splitter = new XMLDocumentSplitter(xmlWorkitem);
		byte[] totalData = null;
		try {
			totalData = splitter.getBytes();
		} catch (JAXBException e) {
			Assert.fail();
		}
		System.out.println("total data size = " + totalData.length+ " => hash: "+XMLDocumentSplitter.SHAsum(totalData));

		// Long way
		Iterator<byte[]> it = splitter.iterator();
		System.out.println("1st test....");
		while (it.hasNext()) {
			byte[] cunk = it.next();
			System.out.println("chunk size=" + cunk.length + " => hash: "+XMLDocumentSplitter.SHAsum(cunk));
		}

		// Shorter, nicer way:

		ByteArrayOutputStream bOutput = new ByteArrayOutputStream();

		System.out.println("2nd test....");
		for (byte[] cunk : splitter) {
			System.out.println("chunk size=" + cunk.length + " => hash: "+XMLDocumentSplitter.SHAsum(cunk));
			try {
				bOutput.write(cunk);
			} catch (IOException e) {

				e.printStackTrace();
			}
		}

		// verify result
		byte[] result = bOutput.toByteArray();
		Assert.assertEquals(totalData.length, result.length);

		for (int i = 0; i < totalData.length; i++) {
			if (totalData[i] != result[i]) {
				Assert.fail();
			}
		}
		System.out.println("total result size = " + result.length+ " => hash: "+XMLDocumentSplitter.SHAsum(result));
	}


	/**
	 * Test iterator interface
	 * 
	 * This test verifies if a itemCollection is correctly chunked and returned by
	 * the iterator interface
	 * @throws NoSuchAlgorithmException 
	 * 
	 */
	@Test
	public void testIterator3mb() throws NoSuchAlgorithmException {

		// second test create a much bigger itemColection
		ItemCollection workitem = WorkitemFactory.createWorkitem();
		workitem.replaceItemValue("_subject", "The Interator 2nd test");
		WorkitemFactory.addFile(workitem, "file1.dat", 1024);
		WorkitemFactory.addFile(workitem, "file2.dat", 2048);
		WorkitemFactory.addFile(workitem, "file3.dat", 3072);

		Assert.assertNotNull(workitem);
		XMLDocument xmlWorkitem = XMLDocumentAdapter.getDocument(workitem);
		Assert.assertNotNull(xmlWorkitem);

		// now test the iterator
		XMLDocumentSplitter splitter = new XMLDocumentSplitter(xmlWorkitem);
		byte[] totalData = null;
		try {
			totalData = splitter.getBytes();
		} catch (JAXBException e) {
			Assert.fail();
		}
		System.out.println("total data size = " + totalData.length+ " => hash: "+XMLDocumentSplitter.SHAsum(totalData));

		// Long way
		Iterator<byte[]> it = splitter.iterator();
		System.out.println("1st test....");
		while (it.hasNext()) {
			byte[] cunk = it.next();
			System.out.println("chunk size=" + cunk.length + " => hash: "+XMLDocumentSplitter.SHAsum(cunk));
		}

		// Shorter, nicer way:
		ByteArrayOutputStream bOutput = new ByteArrayOutputStream();

		System.out.println("2nd test....");
		for (byte[] cunk : splitter) {
			System.out.println("chunk size=" + cunk.length + " => hash: "+XMLDocumentSplitter.SHAsum(cunk));
			try {
				bOutput.write(cunk);
				
			} catch (IOException e) {

				e.printStackTrace();
			}
		}

		// verify result
		byte[] result = bOutput.toByteArray();
		Assert.assertEquals(totalData.length, result.length);

		for (int i = 0; i < totalData.length; i++) {
			if (totalData[i] != result[i]) {
				Assert.fail();
			}
		}
		System.out.println("total result size = " + result.length+ " => hash: "+XMLDocumentSplitter.SHAsum(result));
	}
}
