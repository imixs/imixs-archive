package org.imixs.archive.documents;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.logging.Logger;

import javax.xml.xpath.XPathExpressionException;

import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.PluginException;
import org.mustangproject.BankDetails;
import org.mustangproject.Invoice;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.TransactionCalculator;
import org.mustangproject.ZUGFeRD.ZUGFeRDInvoiceImporter;

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
			logger.info("No e-invoice type detected.");
			return workitem;
		} else {
			String einvoiceType = detectEInvoiceType(eInvoiceFileData);
			workitem.setItemValue(FILE_ATTRIBUTE_EINVOICE_TYPE, einvoiceType);
			logger.info("Detected e-invoice type: " + einvoiceType);

			readEInvoiceContent(eInvoiceFileData, workitem);
		}

		return workitem;
	}

	/**
	 * This method resolves the content of a factur-x e-invocie file and extracts
	 * all invoice and customer fields.
	 * 
	 * 
	 * @param xmlData
	 * @return
	 * @throws PluginException
	 */
	private void readEInvoiceContent(FileData eInvoiceFileData,
			ItemCollection workitem) throws PluginException {
		byte[] xmlData = readXMLContent(eInvoiceFileData);
		logger.info("Autodetect e-invoice data...");

		// createXMLDoc(xmlData);
		try {
			ZUGFeRDInvoiceImporter zii = new ZUGFeRDInvoiceImporter(new ByteArrayInputStream(xmlData));

			Invoice invoice = zii.extractInvoice();
			workitem.setItemValue("invoice.number", invoice.getNumber());
			workitem.setItemValue("cdtr.name", invoice.getOwnOrganisationName());
			workitem.setItemValue("invoice.date", invoice.getIssueDate());

			TransactionCalculator tc = new TransactionCalculator(invoice);
			BigDecimal value = tc.getValue();
			workitem.setItemValue("invoice.total.net", tc.getValue());
			workitem.setItemValue("invoice.total", tc.getGrandTotal());
			workitem.setItemValue("invoice.total.tax", tc.getGrandTotal().floatValue() - tc.getValue().floatValue());

			try {
				TradeParty payee = invoice.getSender();
				BankDetails bankDetails = payee.getBankDetails().get(0);
				workitem.setItemValue("cdtr.bic", bankDetails.getBIC());
				workitem.setItemValue("cdtr.iban", bankDetails.getIBAN());
			} catch (Exception e) {
				// no bank data
			}

		} catch (XPathExpressionException | ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
