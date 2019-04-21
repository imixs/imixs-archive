package org.imixs.workflow.documents;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.documents.parser.DocumentCoreParser;
import org.imixs.workflow.engine.plugins.AbstractPlugin;
import org.imixs.workflow.exceptions.PluginException;

/**
 * The DocumentParserPlugin parses the content of .pdf and ms-doc documents and
 * store the information into the item 'content' of the dms field. This
 * information can be used by plugins to analyze the textual information of a
 * document.
 * 
 * @version 1.0
 * @author rsoika
 */
public class DocumentCoreParserPlugin extends AbstractPlugin {

	public static final String PARSING_EXCEPTION = "PARSING_EXCEPTION";
	public static final String PLUGIN_ERROR = "PLUGIN_ERROR";
	@SuppressWarnings("unused")
	private static Logger logger = Logger.getLogger(DocumentCoreParserPlugin.class.getName());

	/**
	 * This method parses the content of new attached office documents (.pdf, .doc,
	 * ...) and updates the DMS item of workitems before the processing life-cycle
	 * starts.
	 * 
	 * @throws PluginException
	 */
	@Override
	public ItemCollection run(ItemCollection document, ItemCollection event) throws PluginException {

		// update the dms meta data
		updateDMSMetaData(document);
		return document;

	}

	/**
	 * This method iterates over the item 'dms' and parsees the text content for
	 * .pdf, .doc, .xls, .ppt and .docx files. The method updates the result into
	 * the dms list with within the item 'content'.
	 * 
	 * @param aWorkitem
	 * @return true if the dms item was changed
	 * @throws PluginException
	 * @throws NoSuchAlgorithmException
	 */
	private void updateDMSMetaData(ItemCollection workitem) throws PluginException {

		// List<ItemCollection> currentDmsList = DMSHandler.getDmsList(workitem);
		List<FileData> files = workitem.getFileData();

		// List<Map> vDMS = workitem.getItemValue(DMSHandler.DMS_ITEM);

		for (FileData fileData : files) {

			// We parse the file content if a new file content was added
			byte[] fileContent = fileData.getContent();
			;
			if (fileContent != null && fileContent.length > 1) {
				// parse content...
				try {
					String searchContent = DocumentCoreParser.parse(fileData);
					List<Object> l = new ArrayList<Object>();
					l.add(searchContent);
					fileData.setAttribute("content", l);
				} catch (IOException e) {
					throw new PluginException(DocumentCoreParserPlugin.class.getSimpleName(), PARSING_EXCEPTION,
							"Unable to parse attached document '" + fileData.getName() + "'", e);
				}
			}

		}

	}

}