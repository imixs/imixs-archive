package org.imixs.workflow.archive.hadoop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;

/**
 * This client class can be used to access the Hadoop WebHDFS Rest API. The
 * client provides methods to write and read data streams. The client is used by
 * the Imixs-Archie HadoopService class to store and restore Imixs Document
 * Collections.
 * 
 * The client reads configuration settings form the property file
 * 'imixs-hadoop.properties'. The properie file provides the following
 * properties
 * <ul>
 * <li>hadoop.hdfs.baseURL</li>
 * <li>hadoop.hdfs.defaultPrincipal</li>
 * </ul>
 * 
 * The Client provide the object HadoopResponse holding the response information
 * from the last operation.
 * 
 * In the first version the client supports only the pseudo authentication.
 * Kerberos authentication will be implemented in version 2.0
 * 
 * 
 * 
 * @author rsoika
 * @version 1.0
 */
public class HDFSClient {

	public final static String BASE_URL = "hadoop.hdfs.baseURL";
	public final static String DEFUALT_PRINCIPAL = "hadoop.hdfs.defaultPrincipal";
	public final static String DEFUALT_URL = "http://localhost:50070/webhdfs/v1";

	private final static Logger logger = Logger.getLogger(HDFSClient.class.getName());

	private String principal = null;
	private String hadoopBaseUrl = null;
	private Properties properties = null;
	private String url = null;
	private HadoopResponse hadoopResponse = null;

	/**
	 * Default constructor creates a new HDFSClient with the user defined by the
	 * imixs-hadoop.properties file.
	 * 
	 * @param principal
	 */
	public HDFSClient() {
		this(null);
	}

	public HadoopResponse getHadoopResponse() {
		return hadoopResponse;
	}

	public void setHadoopResponse(HadoopResponse hadoopResponse) {
		this.hadoopResponse = hadoopResponse;
	}

	/**
	 * Constructor for pseudo authentication. The method expects only a pricipal,
	 * all other properties are read from the imixs-hadoop.properties file
	 * 
	 * @param httpfsUrl
	 * @param principal
	 */
	public HDFSClient(String principal) {
		// load properties
		init();

		// is principal set?
		if (principal == null || principal.isEmpty()) {
			// try to fetch principal from properties
			principal = properties.getProperty(DEFUALT_PRINCIPAL);

			if (principal == null || principal.isEmpty()) {
				principal = "root";
				logger.warning("principal not set, using default principal '" + principal + "'");
			}
		}

		this.principal = principal;
	}

	/**
	 * The init method read the imixs-hadoop.properties file and set the default
	 * values for the principal an base url. The method prints a warning log message
	 * if these values are not defined.
	 */
	void init() {
		loadProperties();
		hadoopBaseUrl = properties.getProperty(BASE_URL);

		if (hadoopBaseUrl == null || hadoopBaseUrl.isEmpty()) {
			logger.warning("base URL not set, using default URL '" + DEFUALT_URL + "'");
			hadoopBaseUrl = DEFUALT_URL;
		}

		// if url ends with / cut the last char
		if (hadoopBaseUrl.endsWith("/")) {
			hadoopBaseUrl = hadoopBaseUrl.substring(0, hadoopBaseUrl.length() - 1);
		}

	}

	/**
	 * This method transfers the data of an input stream to the hadoop file system
	 * by using the <b>CREATE</b> method from the WebHDFS API.
	 * 
	 * To create a new file, 2 requests are needed:
	 * 
	 * 1.) request the datanode post URL
	 * 
	 * curl -i -X PUT -T test.txt
	 * "http://localhost:50070/webhdfs/v1/test.txt?user.name=root&op=CREATE"
	 * 
	 * 2.) transfere the file
	 * 
	 * curl -i -X PUT -T /home/rsoika/Downloads/test.txt
	 * "http://my-hadoop-cluster.local:50075/webhdfs/v1/test.txt?op=CREATE&user.name=root&namenoderpcaddress=localhost:8020&createflag=&createparent=true&overwrite=false"
	 * 
	 * 
	 * 
	 * @param path
	 * @param content
	 *            - file data
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws JAXBException
	 * @throws AuthenticationException
	 */
	public void putData(String path, byte[] content, boolean overwrite)
			throws MalformedURLException, IOException, JAXBException {
		String redirectUrl = null;

		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		if (principal == null || principal.isEmpty()) {
			logger.warning("principal is not set!");
		}

		url = hadoopBaseUrl + path + "?user.name=" + principal + "&op=CREATE&&overwrite=" + overwrite;
		URL obj = new URL(url);
		HttpURLConnection conn = (HttpURLConnection) obj.openConnection();

		conn.setRequestMethod("PUT");
		conn.setInstanceFollowRedirects(false);
		conn.connect();
		logger.fine("Location:" + conn.getHeaderField("Location"));

		if (conn.getResponseCode() == 307) {
			redirectUrl = conn.getHeaderField("Location");
			conn.disconnect();

			// open connection on new location...
			conn = (HttpURLConnection) new URL(redirectUrl).openConnection();
			conn.setRequestMethod("PUT");
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setUseCaches(false);

			// write XML data
			conn.setRequestProperty("Content-type", "text/xml");
			conn.setRequestProperty("Content-Length", "" + Integer.valueOf(content.length));
			conn.setDoOutput(true);
			OutputStream outs = conn.getOutputStream();
			outs.write(content);
			outs.flush();
			outs.close();

			// set response object
			setHadoopResponse(new HadoopResponse(conn));
			conn.disconnect();
			logger.info("putData:" + path + "- responeCode=" + getHadoopResponse().getCode());
		}
	}

	/**
	 * This method reads the data from the hadoop file system by using the
	 * <b>OPEN</b> method from the WebHDFS API.
	 * 
	 * To returns the DocumentCollection.
	 * 
	 * 2 requests are needed:
	 * 
	 * 1.) request the datanode post URL
	 * 
	 * curl -i -X PUT -T test.txt
	 * "http://localhost:50070/webhdfs/v1/test.txt?user.name=root&op=OPEN"
	 * 
	 * 2.) transfere the file
	 * 
	 * curl -i -X PUT -T /home/rsoika/Downloads/test.txt
	 * "http://my-hadoop-cluster.local:50075/webhdfs/v1/test.txt?op=OPEN&user.name=root&namenoderpcaddress=...
	 * 
	 * 
	 * 
	 * @param path
	 * @param is
	 * @return
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws JAXBException
	 * @throws AuthenticationException
	 */
	public byte[] readData(String path) throws MalformedURLException, IOException, JAXBException {
		ByteArrayOutputStream baos = null;
		String redirectUrl = null;

		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		if (principal == null || principal.isEmpty()) {
			logger.warning("principal is not set!");
		}

		url = hadoopBaseUrl + path + "?user.name=" + principal + "&op=OPEN";
		URL obj = new URL(url);
		HttpURLConnection conn = (HttpURLConnection) obj.openConnection();

		conn.setRequestMethod("GET");
		conn.setInstanceFollowRedirects(false);
		conn.connect();
		logger.fine("Location:" + conn.getHeaderField("Location"));
		if (conn.getResponseCode() == 307) {
			redirectUrl = conn.getHeaderField("Location");
			conn.disconnect();
			InputStream is = null;
			try {
				// open connection on new location...
				conn = (HttpURLConnection) new URL(redirectUrl).openConnection();

				conn.setRequestMethod("GET");
				conn.setDoOutput(true);
				conn.setDoInput(true);
				conn.setUseCaches(false);
				conn.setRequestProperty("Content-Type", "application/octet-stream");

				baos = new ByteArrayOutputStream();

				is = conn.getInputStream();
				byte[] byteChunk = new byte[4096]; // Or whatever size you want to read in at a time.
				int n;

				while ((n = is.read(byteChunk)) > 0) {
					baos.write(byteChunk, 0, n);
				}
			} catch (IOException e) {
				System.err.printf("Failed while reading bytes from %s: %s", e.getMessage());
				throw e;
			} finally {
				if (is != null) {
					is.close();
				}
				if (conn != null) {
					conn.disconnect();
				}
			}

		}

		// return byte array
		if (baos != null) {
			return baos.toByteArray();
		} else {
			return null;
		}

	}

	/**
	 * This method renames a existing file . The method returns the http result
	 * code.
	 * 
	 * 
	 * 
	 * @param path
	 * @param destination
	 *            - target
	 * @return
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws JAXBException
	 * @throws AuthenticationException
	 */
	public void renameData(String path, String destination) throws MalformedURLException, IOException, JAXBException {
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		if (!destination.startsWith("/")) {
			destination = "/" + destination;
		}

		if (principal == null || principal.isEmpty()) {
			logger.warning("principal is not set!");
		}

		url = hadoopBaseUrl + path + "?user.name=" + principal + "&overwrite=true&op=RENAME&destination=" + destination;
		URL obj = new URL(url);
		HttpURLConnection conn = (HttpURLConnection) obj.openConnection();

		conn.setRequestMethod("PUT");
		conn.setInstanceFollowRedirects(false);
		conn.connect();
		setHadoopResponse(new HadoopResponse(conn));
		conn.disconnect();
		logger.info("renameData:" + path + "- responeCode=" + getHadoopResponse().getCode());
	}

	/**
	 * This method deletes a existing file . The method returns the http result
	 * code.
	 * 
	 * 
	 * 
	 * @param path
	 * @param destination
	 *            - target
	 * @return
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws JAXBException
	 * @throws AuthenticationException
	 */
	public void deleteData(String path) throws MalformedURLException, IOException, JAXBException {
		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		if (principal == null || principal.isEmpty()) {
			logger.warning("principal is not set!");
		}

		url = hadoopBaseUrl + path + "?user.name=" + principal + "&op=DELETE";
		URL obj = new URL(url);
		HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
		conn.setRequestMethod("DELETE");
		conn.setInstanceFollowRedirects(false);
		conn.connect();
		setHadoopResponse(new HadoopResponse(conn));
		conn.disconnect();
		logger.info("deleteData:" + path + "- responeCode=" + getHadoopResponse().getCode());
	}

	public String getUrl() {
		return url;
	}

	/**
	 * loads a imixs-hadoop.property file
	 * 
	 * (located at current threads classpath)
	 * 
	 */
	private void loadProperties() {
		properties = new Properties();
		try {
			properties.load(
					Thread.currentThread().getContextClassLoader().getResource("imixs-hadoop.properties").openStream());
		} catch (Exception e) {
			logger.severe("PropertyService unable to find imixs-hadoop.properties in current classpath");
			e.printStackTrace();
			properties = new Properties();
		}

	}

}
