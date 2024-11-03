package org.imixs.archive.documents;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.WorkflowMockEnvironment;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * This test class tests PDFXMLExctractorPlugin
 * 
 * @author rsoika
 * @version 1.0
 */
public class PDFXMLExtractorPluginTest {
	private static Logger logger = Logger.getLogger(PDFXMLExtractorPluginTest.class.getName());

	PDFXMLExtractorPlugin plugin = null;
	ItemCollection event = null;

	/**
	 * We use the provided test workflow model form the AbstractWorkflowServiceTest
	 * 
	 * @throws ModelException
	 */
	WorkflowMockEnvironment workflowMockEnvironment;

	@BeforeEach
	public void setup() throws PluginException, ModelException {

		workflowMockEnvironment = new WorkflowMockEnvironment();
		workflowMockEnvironment.setUp();
		workflowMockEnvironment.loadBPMNModel("/bpmn/TestZUGFeRD.bpmn");

		// mock abstract plugin class for the plitAndJoinPlugin
		plugin = Mockito.mock(PDFXMLExtractorPlugin.class, Mockito.CALLS_REAL_METHODS);
		when(plugin.getWorkflowService()).thenReturn(workflowMockEnvironment.getWorkflowService());

		try {
			plugin.init(workflowMockEnvironment.getWorkflowContext());
		} catch (PluginException e) {

			e.printStackTrace();
		}

	}

	/**
	 * Test extracting an embedded xml file from a PDF file using the pdfBox
	 * library.
	 * 
	 */
	@Test
	public void testPDFXMLExtractor() {
		ItemCollection workitem = null;
		try {

			// Build a Document....
			workitem = new ItemCollection();
			workitem.model("1.0.0").task(100).event(10);
			event = workflowMockEnvironment.getModelService().getModelManager().loadEvent(workitem);
			// load the example pdf ..
			String fileName = "ZUGFeRD/20160504_MX16124-000005_001-001_Muster.pdf";
			InputStream inputStream = getClass().getResourceAsStream("/" + fileName);
			byte[] fileData = PDFXMLExtractorPlugin.streamToByteArray(inputStream);
			workitem.addFileData(new FileData(fileName, fileData, "", null));

			byte[] xmldata = PDFXMLExtractorPlugin.getXMLFile(workitem, ".pdf");

			assertNotNull(xmldata);

			// show first 100 characters from xml.....
			String xml = new String(xmldata);
			logger.info(xml.substring(0, 100) + "...");

		} catch (PluginException | ModelException | IOException e) {

			e.printStackTrace();
			fail();
		}

		assertNotNull(workitem);

	}

	/**
	 * Test extracting an embedded xml file from a PDF file, converting the xml into
	 * a XMLDocument
	 * and merging the data into the current document.
	 * 
	 */
	@Test
	@Disabled
	public void testMergeXMLData() {
		ItemCollection workitem = null;
		try {
			// Build a Document....
			workitem = new ItemCollection();
			workitem.model("1.0.0").task(100).event(10);
			event = workflowMockEnvironment.getModelService().getModelManager().loadEvent(workitem);

			// load the example pdf ..
			String fileName = "ZUGFeRD/20160504_MX16124-000005_001-001_Muster.pdf";
			InputStream inputStream = getClass().getResourceAsStream("/" + fileName);
			byte[] fileData = PDFXMLExtractorPlugin.streamToByteArray(inputStream);
			workitem.addFileData(new FileData(fileName, fileData, "", null));

			workitem = plugin.run(workitem, event);

			assertNotNull(workitem);

		} catch (PluginException | ModelException | IOException e) {

			e.printStackTrace();
			fail();
		}

		assertNotNull(workitem);

	}

	/**
	 * Test the regex for pdf file names.
	 */
	@Test
	public void testRegex() {
		assertTrue(Pattern.compile(".pdf").matcher("sample.pdf").find());
		assertTrue(Pattern.compile(".[pP][dD][fF]").matcher("sample.PDF").find());
	}

}
