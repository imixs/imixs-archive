package org.imixs.workflow.documents;

import java.io.IOException;
import java.util.logging.Logger;

import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Diese Klasse testet die X-Rechnung XSLT Transformation
 * 
 * 
 * @author rsoika
 */
public class TestEInvoiceTransform {

	final static String ENCODING = "UTF-8";

	private static Logger logger = Logger.getLogger(TestEInvoiceTransform.class.getName());

	@BeforeEach
	public void setup() throws PluginException, ModelException {
		logger.info("setup test environment ...");

	}

	/**
	 * XSL Transformation
	 * 
	 * @throws IOException
	 */
	@Test
	public void testDefault() throws IOException {
		String MODEL_PATH_XML = "e-invoice/Rechnung_R_00010.xml";
		String MODEL_PATH_XSL = "e-invoice/xrechnung-v2.xsl";
		try {
			logger.info("..start xlst....");
			XSLTester.transform(MODEL_PATH_XML, MODEL_PATH_XSL, "target/result_e-invoice.html");
			logger.info("..end xlst....");
		} catch (Exception e) {
			e.printStackTrace();
			// Assert.fail();
		}
	}

	/**
	 * XSL Transformation
	 * 
	 * @throws IOException
	 */
	@Test
	public void testcii_xr() throws IOException {
		String MODEL_PATH_XML = "e-invoice/Rechnung_R_00010.xml";
		String MODEL_PATH_XSL = "e-invoice/cii-xr.xsl";
		try {
			logger.info("..start xlst....");
			XSLTester.transform(MODEL_PATH_XML, MODEL_PATH_XSL, "target/result_e-invoice.html");
			logger.info("..end xlst....");
		} catch (Exception e) {
			e.printStackTrace();
			// Assert.fail();
		}
	}

}
