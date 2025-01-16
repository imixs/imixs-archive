package org.imixs.archive.documents;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;

import org.imixs.einvoice.EInvoiceModel;
import org.imixs.einvoice.EInvoiceModelFactory;
import org.imixs.einvoice.TradeParty;
import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.PluginException;

/**
 * The EInvoiceAutoAdapter can detect and extract content from e-invoice
 * documents
 * in different formats. This Adapter class extends the {@link EInvoiceAdapter}
 * and resolves pre defined Items according to the Factur-X/ZUGFeRD 2.0
 * standard.
 * 
 * @author rsoika
 * @version 2.0
 * 
 */
public class EInvoiceAutoAdapter extends EInvoiceAdapter {
	private static Logger logger = Logger.getLogger(EInvoiceAdapter.class.getName());

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

		// Detect and read E-Invoice Data
		FileData eInvoiceFileData = detectEInvoice(workitem);

		if (eInvoiceFileData == null) {
			logger.fine("No e-invoice type detected.");
			return workitem;
		} else {
			String einvoiceType = detectEInvoiceType(eInvoiceFileData);
			workitem.setItemValue(FILE_ATTRIBUTE_EINVOICE_TYPE, einvoiceType);
			logger.info("Detected e-invoice type: " + einvoiceType);

			byte[] xmlData = readXMLContent(eInvoiceFileData);
			try {
				EInvoiceModel model = EInvoiceModelFactory.read(new ByteArrayInputStream(xmlData));
				resolveItemValues(workitem, model);

				// store xml into the text attribute
				String xmlText = new String(xmlData, StandardCharsets.UTF_8);
				eInvoiceFileData.setAttribute("text", Arrays.asList(xmlText));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// readEInvoiceContent(eInvoiceFileData, workitem);
		}

		return workitem;
	}

	/**
	 * This method resolves the imixs items for an invoice base don a EInvoiceModel
	 * 
	 * @param workitem
	 * @param model
	 */
	private void resolveItemValues(ItemCollection workitem, EInvoiceModel model) {

		workitem.setItemValue("invoice.number", model.getId());
		workitem.setItemValue("invoice.date", model.getIssueDateTime());
		workitem.setItemValue("invoice.duedate", model.getDueDateTime());

		workitem.setItemValue("invoice.total", model.getGrandTotalAmount().setScale(2, RoundingMode.HALF_UP));
		workitem.setItemValue("invoice.total.net", model.getNetTotalAmount().setScale(2, RoundingMode.HALF_UP));
		workitem.setItemValue("invoice.total.tax", model.getTaxTotalAmount().setScale(2, RoundingMode.HALF_UP));

		// Date.from(model.getIssueDateTime().atStartOfDay(ZoneId.systemDefault()).toInstant()));

		TradeParty seller = model.findTradeParty("seller");
		if (seller != null) {
			workitem.setItemValue("cdtr.name", seller.getName());
			workitem.setItemValue("cdtr.vatid", seller.getVatNumber());
		}

	}

}
