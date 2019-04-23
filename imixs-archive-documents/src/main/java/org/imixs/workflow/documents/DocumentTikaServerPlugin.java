package org.imixs.workflow.documents;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.ejb.EJB;

import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowContext;
import org.imixs.workflow.engine.PropertyService;
import org.imixs.workflow.engine.plugins.AbstractPlugin;
import org.imixs.workflow.exceptions.PluginException;


/**
 * The DocumentTikaServerPlugin sends a document to an instance of an Apache
 * Tika Server to get the file content.
 * <p>
 * The plugin expects the Environment Parameter 'TIKA_SERVICE_ENDPONT' to get the
 * Rest API endpoint.
 * <p>
 * See also the project: https://github.com/imixs/imixs-docker/tree/master/tika
 * 
 * @version 1.0
 * @author rsoika
 */
public class DocumentTikaServerPlugin extends AbstractPlugin {

	public static final String PLUGIN_ERROR = "PLUGIN_ERROR";
	public static final String ENV_TIKA_SERVICE_ENDPONT = "TIKA_SERVICE_ENDPONT";

	private static Logger logger = Logger.getLogger(DocumentTikaServerPlugin.class.getName());

	private String serviceEndpoint = null;

	@EJB
	PropertyService propertyService;

	@Override
	public void init(WorkflowContext actx) throws PluginException {
		super.init(actx);
		// read the Tika Service Enpoint
		serviceEndpoint = DocumentTikaServerPlugin.getEnv(ENV_TIKA_SERVICE_ENDPONT, null);
	}

	/**
	 * This method sends the document content to the tika server and updates teh DMS
	 * information.
	 * 
	 * @throws PluginException
	 */
	@Override
	public ItemCollection run(ItemCollection document, ItemCollection event) throws PluginException {
		if (serviceEndpoint != null && !serviceEndpoint.isEmpty()) {
			// update the dms meta data
			updateDMSMetaData(document);
		}
		return document;

	}

	/**
	 * Sends each new document to the tika server and updates the fileData atribute
	 * 'content'
	 * 
	 * @param workitem
	 * @throws PluginException
	 */
	private void updateDMSMetaData(ItemCollection workitem) throws PluginException {
		long l = System.currentTimeMillis();
		// List<ItemCollection> currentDmsList = DMSHandler.getDmsList(workitem);
		List<FileData> files = workitem.getFileData();

		for (FileData fileData : files) {
			// We parse the file content if a new file content was added
			byte[] fileContent = fileData.getContent();

			if (fileContent != null && fileContent.length > 1) {
				// scan content...
				try {
					logger.info("...send " + fileData.getName() + " to tika server...");
				//	RestClient restClient = new RestClient(serviceEndpoint);

					String result = put(serviceEndpoint, fileContent, fileData.getContentType(),"UTF-8");

					if (result!=null && !result.isEmpty()) {
						List<Object> list = new ArrayList<Object>();
						list.add(result);
						fileData.setAttribute("content", list);
					}
				} catch (Exception e) {
					throw new PluginException(DocumentCoreParserPlugin.class.getSimpleName(), PLUGIN_ERROR,
							"Unable to scan attached document '" + fileData.getName() + "'", e);
				}
			}

		}

	}

	
	/**
	 * Posts a String data object with a specific Content-Type to a Rest Service URI
	 * Endpoint. This method can be used to simulate different post scenarios.
	 * 
	 * @param uri
	 *            - Rest Endpoint RUI
	 * @param dataString
	 *            - content
	 * @return content
	 */
	public String put(String uri, byte[] dataString, String contentType,String encoding) throws Exception {
		PrintWriter printWriter = null;
		if (contentType == null || contentType.isEmpty()) {
			contentType = "application/xml";
		}

		HttpURLConnection urlConnection = null;
		try {
			serviceEndpoint = uri;
		
			urlConnection = (HttpURLConnection) new URL(serviceEndpoint).openConnection();
			urlConnection.setRequestMethod("PUT");
			urlConnection.setDoOutput(true);
			urlConnection.setDoInput(true);
			urlConnection.setAllowUserInteraction(false);

			/** * HEADER ** */
			urlConnection.setRequestProperty("Content-Type", contentType + "; charset=" + encoding);
			
			urlConnection.setRequestProperty("Accept","text/plain");

			

			// compute length
			urlConnection.setRequestProperty("Content-Length", "" + Integer.valueOf(dataString.length));
			OutputStream output = urlConnection.getOutputStream();
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, encoding), true);
			output.write(dataString);
			writer.flush();

			String sHTTPResponse = urlConnection.getHeaderField(0);
			int iLastHTTPResult=0;
	
			try {
				iLastHTTPResult = Integer.parseInt(sHTTPResponse.substring(9, 12));
			} catch (Exception eNumber) {
				// eNumber.printStackTrace();
				return null;
			}
			
			if (iLastHTTPResult>=200 && iLastHTTPResult<=299) {
				return readResponse(urlConnection,encoding);
			}
			
			// no data!
			return null;

		} catch (Exception ioe) {
			// ioe.printStackTrace();
			throw ioe;
		} finally {
			// Release current connection
			if (printWriter != null)
				printWriter.close();
		}
	}

	
	/**
	 * Reads the response from a http request.
	 * 
	 * @param urlConnection
	 * @throws IOException
	 */
	private String readResponse(URLConnection urlConnection,String encoding) throws IOException {
		// get content of result
		logger.finest("......readResponse....");
		StringWriter writer = new StringWriter();
		BufferedReader in = null;
		try {
			// test if content encoding is provided
			String sContentEncoding = urlConnection.getContentEncoding();
			if (sContentEncoding == null || sContentEncoding.isEmpty()) {
				// no so lets see if the client has defined an encoding..
				if (encoding != null && !encoding.isEmpty())
					sContentEncoding = encoding;
			}

			// if an encoding is provided read stream with encoding.....
			if (sContentEncoding != null && !sContentEncoding.isEmpty())
				in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), sContentEncoding));
			else
				in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				logger.finest("......" + inputLine);
				writer.write(inputLine);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (in != null)
				in.close();
		}

		return writer.toString();

	}

	/**
	 * Returns a environment variable. An environment variable can be provided as a
	 * System property.
	 * 
	 * @param env
	 *            - environment variable name
	 * @param defaultValue
	 *            - optional default value
	 * @return value
	 */
	public static String getEnv(String env, String defaultValue) {
		logger.finest("......read env: " + env);
		String result = System.getenv(env);
		if (result == null || result.isEmpty()) {
			result = defaultValue;
		}
		return result;
	}
}