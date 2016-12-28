package org.imixs.archive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test class to check a HDFS
 * 
 * @author rsoika
 * 
 */
public class HadoopTest {

	public static String TEST_DATA = "/test-data.txt";

	// change this to string arg in main
	public static final String inputfile = "hdfsinput.txt";
	public static final String outputfile = "hdfsoutput.txt";
	public static final String inputmsg = "Imixs-Archive - this is a test sentence!\n";

	@Test
	public void testLocalHDFSWrite() throws IOException {

		// Create a default hadoop configuration
		Configuration config = new Configuration();
		// Parse created config to the HDFS
		FileSystem fs = FileSystem.get(config);

		// Specifies a new file in HDFS.
		Path filenamePath = new Path(inputfile);

		try {
			// if the file already exists delete it.
			if (fs.exists(filenamePath)) {
				// remove the file
				System.out.println("file exists - deleting....");
				fs.delete(filenamePath, true);
			}

			// FSOutputStream to write the inputmsg into the HDFS file
			FSDataOutputStream fin = fs.create(filenamePath);
			fin.writeUTF(inputmsg);
			fin.close();

			// FSInputStream to read out of the filenamePath file
			FSDataInputStream fout = fs.open(filenamePath);
			String msgIn = fout.readUTF();
			// Print to screen
			System.out.println(msgIn);
			Assert.assertEquals(inputmsg, msgIn);
			fout.close();
		} catch (IOException ioe) {
			System.err.println("IOException during operation " + ioe.toString());
			Assert.fail();
		}

	}

	@Test
	public void testLocalHDFSWrite2() throws IOException {
		Configuration conf = new Configuration();

		FileSystem fs = FileSystem.get(conf);

		// Hadoop DFS deals with Path
		Path inFile = new Path(inputfile);
		Path outFile = new Path(outputfile);

		// Check if input/output are valid
		if (!fs.exists(inFile)) {
			System.err.println("Input file not found");
			Assert.fail();
		}
		if (!fs.isFile(inFile)) {
			System.err.println("Input should be a file");
			Assert.fail();
		}
		if (fs.exists(outFile)) {
			System.err.println("Output already exists");
			Assert.fail();
		}
		// Read from and write to new file
		FSDataInputStream in = fs.open(inFile);
		FSDataOutputStream out = fs.create(outFile);
		byte buffer[] = new byte[256];
		try {
			int bytesRead = 0;
			while ((bytesRead = in.read(buffer)) > 0) {
				out.write(buffer, 0, bytesRead);
			}
		} catch (IOException e) {
			System.out.println("Error while copying file " + e.getMessage());
			Assert.fail();
		} finally {
			in.close();
			out.close();
		}

	}

	@Test
	public void testLocalHDFSRead() throws IOException {

		// Create a default hadoop configuration
		Configuration config = new Configuration();
		// Parse created config to the HDFS
		FileSystem fs = FileSystem.get(config);

		// Specifies a new file in HDFS.
		Path filenamePath = new Path(inputfile);

		try {
			// if the file already exists delete it.
			if (!fs.exists(filenamePath)) {
				System.out.println("file not found.");
				// Assert.fail();
			}

			// FSInputStream to read out of the filenamePath file
			FSDataInputStream fout = fs.open(filenamePath);
			String msgIn = fout.readUTF();
			// Print to screen
			System.out.println(msgIn);
			Assert.assertEquals(inputmsg, msgIn);

			fout.close();
		} catch (IOException ioe) {
			System.err.println("IOException during operation " + ioe.toString());
			Assert.fail();
		}

	}

	/**
	 * Test a local 'remote' connection to a HDFS node (Docker imixs/archive)
	 * 
	 * @throws IOException
	 */
	@Test
	@Ignore
	public void testRemoteLocalHDFSWrite() throws IOException {

		// Create a default hadoop configuration
		Configuration config = new Configuration();
		// config.set("fs.defaultFS", "hdfs://localhost:8020");
		config.set("fs.defaultFS", "hdfs://localhost:9000");
		// Parse created config to the HDFS
		FileSystem fs = FileSystem.get(config);

		// Specifies a new file in HDFS.
		String remoteinputfile = "hdfs://localhost:9000/hdfsinput.txt";
		// String remoteinputfile = "hdfs://localhost:8020/hdfsinput.txt";
		Path filenamePath = new Path(remoteinputfile);

		try {
			// if the file already exists delete it.
			if (fs.exists(filenamePath)) {
				// remove the file
				fs.delete(filenamePath, true);
			}

			// FSOutputStream to write the inputmsg into the HDFS file
			FSDataOutputStream fin = fs.create(filenamePath);
			fin.writeUTF(inputmsg);
			fin.close();

			// FSInputStream to read out of the filenamePath file
			FSDataInputStream fout = fs.open(filenamePath);
			String msgIn = fout.readUTF();
			// Print to screen
			System.out.println(msgIn);
			fout.close();
		} catch (IOException ioe) {
			System.err.println("IOException during operation " + ioe.toString());
			System.exit(1);
		}

	}

	/**
	 * This test test the local filesystem through the hadoop interface
	 * 
	 * @see: https://community.hortonworks.com/questions/19449/hadoop-localfilesystem-checksum-calculation.html
	 * 
	 * @throws IOException
	 */
	@Test
	public void testLocalFSWrite() throws IOException {

		// Create a default hadoop configuration
		Configuration config = new Configuration();
		// Parse created config to the HDFS
		FileSystem fs = FileSystem.get(config);

		// Specifies a new file in HDFS.
		Path filenamePath = new Path("file:///tmp/hello");

		try {
			// if the file already exists delete it.
			if (fs.exists(filenamePath)) {
				// remove the file
				fs.delete(filenamePath, true);
			}

			// FSOutputStream to write the inputmsg into the HDFS file
			FSDataOutputStream fin = fs.create(filenamePath);
			fin.writeUTF(inputmsg);
			fin.close();

			// FSInputStream to read out of the filenamePath file
			FSDataInputStream fout = fs.open(filenamePath);
			String msgIn = fout.readUTF();
			// Print to screen
			System.out.println(msgIn);
			fout.close();
		} catch (IOException ioe) {
			System.err.println("IOException during operation " + ioe.toString());
			System.exit(1);
		}

	}

	/**
	 * simple test to write bytes into hdfs
	 * 
	 * @throws IOException
	 */
	@Test
	public void testWriteBytes() throws IOException {
		Configuration conf = new Configuration();

		conf.addResource(new Path("/opt/hadoop/etc/hadoop/core-site.xml"));
		conf.addResource(new Path("/opt/hadoop/etc/hadoop/hdfs-site.xml"));
		FSDataOutputStream out = null;
		try {

			// FileSystem fs = FileSystem.get(new URI("hdfs://localhost:54310"),
			// conf);
			// Path file = new Path("hdfs://localhost:54310/table.html");

			FileSystem fs = FileSystem.get(conf);
			Path file = new Path("byte-test-data2");

			if (fs.exists(file)) {
				System.err.println("Output already exists");
				fs.delete(file, true);
			}
			// Read from and write to new file
			out = fs.create(file);

			String s = "some test data....2";
			byte data[] = s.getBytes();

			out.write(data);
		} catch (Exception e) {
			System.out.println("Error while copying file " + e.getMessage());
			Assert.fail();
		} finally {
			if (out != null) {
				out.close();
			}
		}

	}

	/**
	 * simple test to read bytes from hdfs
	 * 
	 * @throws IOException
	 */
	@Test
	public void testReadBytes() throws IOException {
		Configuration conf = new Configuration();

		conf.addResource(new Path("/opt/hadoop/etc/hadoop/core-site.xml"));
		conf.addResource(new Path("/opt/hadoop/etc/hadoop/hdfs-site.xml"));
		FSDataOutputStream out = null;
		try {

			FileSystem fs = FileSystem.get(conf);
			Path file = new Path("byte-test-data2");

			// if the file already exists delete it.
			if (!fs.exists(file)) {
				System.out.println("file not found.");
				Assert.fail();
			}

			// FSInputStream to read out of the filenamePath file
			FSDataInputStream fout = fs.open(file);

			ByteArrayOutputStream buffer = new ByteArrayOutputStream();

			int nRead;
			byte[] data = new byte[16384];

			while ((nRead = fout.read(data, 0, data.length)) != -1) {
				buffer.write(data, 0, nRead);
			}

			buffer.flush();
			String msgIn = buffer.toString();

			// Print to screen
			System.out.println(msgIn);
			Assert.assertEquals("some test data....2", msgIn);

		} catch (Exception e) {
			System.out.println("Error while copying file " + e.getMessage());
			Assert.fail();
		} finally {
			if (out != null) {
				out.close();
			}
		}

	}

}
