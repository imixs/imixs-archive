package org.imixs.archive.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.WorkflowMockEnvironment;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.openbpmn.bpmn.BPMNModel;

/**
 * This test class is testing the EInvoiceAdapter and tests different
 * kind of files
 * 
 */
class EInvoiceAdapterTest {

    @InjectMocks
    protected EInvoiceAdapter adapter;

    protected ItemCollection workitem;
    protected ItemCollection event;
    protected WorkflowMockEnvironment workflowEnvironment;
    BPMNModel model = null;

    @BeforeEach
    public void setUp() throws PluginException, ModelException {
        // Ensures that @Mock and @InjectMocks annotations are processed
        MockitoAnnotations.openMocks(this);
        workflowEnvironment = new WorkflowMockEnvironment();

        // register AccessAdapter Mock
        workflowEnvironment.registerAdapter(adapter);

        // Setup Environment
        workflowEnvironment.setUp();
        workflowEnvironment.loadBPMNModel("/bpmn/TestZUGFeRD.bpmn");
        model = workflowEnvironment.getModelService().getModelManager().getModel("1.0.0");
        adapter.workflowService = workflowEnvironment.getWorkflowService();

        // prepare data
        workitem = new ItemCollection().model("1.0.0").task(100);

        event = new ItemCollection();
        // set test txtActivityResult....
        String config = "<e-invoice name=\"ENTITY\">\n" + //
                "  <name>invoice.number</name>\n" + //
                "  <xpath>//rsm:CrossIndustryInvoice/rsm:ExchangedDocument/ram:ID</xpath>\n" + //
                "</e-invoice>\n" + //
                "<e-invoice name=\"ENTITY\">\n" + //
                "  <name>invoice.date</name>\n" + //
                "  <type>date</type>\n" + //
                "  <xpath>//rsm:ExchangedDocument/ram:IssueDateTime/udt:DateTimeString/text()</xpath>\n" + //
                "</e-invoice>\n" + //
                "<e-invoice name=\"ENTITY\">\n" + //
                "  <name>invoice.total</name>\n" + //
                "  <type>double</type>\n" + //
                "  <xpath>//ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:GrandTotalAmount</xpath>\n" + //
                "</e-invoice>\n" + //
                "<e-invoice name=\"ENTITY\">\n" + //
                "  <name>cdtr.name</name>\n" + //
                "  <xpath>//ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:Name/text()</xpath>\n" + //
                "</e-invoice>";
        event.setItemValue("txtActivityResult", config);
    }

    @Test
    void testExecuteWithPDFContainingXML() throws AdapterException, PluginException, IOException {
        // Prepare test data
        FileData pdfFile = createFileData("e-invoice/Rechnung_R_00011.pdf", "application/pdf");
        workitem.addFileData(pdfFile);
        FileData fileData = adapter.detectEInvoice(workitem);
        String result = EInvoiceAdapter.detectEInvoiceType(fileData);
        assertNotNull(result);
        assertEquals("Factur-X/ZUGFeRD 2.0", result);
    }

    @Test
    void testExecuteWithStandaloneXML() throws AdapterException, PluginException, IOException {
        // Prepare test data
        FileData xmlFile = createFileData("e-invoice/Rechnung_R_00010.xml", "application/xml");
        workitem.addFileData(xmlFile);

        FileData fileData = adapter.detectEInvoice(workitem);
        String result = EInvoiceAdapter.detectEInvoiceType(fileData);

        // Verify the result
        assertNotNull(result);
        assertEquals("Factur-X/ZUGFeRD 2.0", result);
    }

    /**
     * This test uses the xpath expressions form teh workflow event to extract xml
     * content.
     * 
     * @throws AdapterException
     * @throws PluginException
     * @throws IOException
     */
    @Test
    void testExecuteWithStandaloneXMLExtractData() throws AdapterException, PluginException, IOException {

        // Prepare test data
        FileData xmlFile = createFileData("e-invoice/Rechnung_R_00010.xml", "application/xml");
        workitem.addFileData(xmlFile);

        adapter.execute(workitem, event);

        assertEquals("R-00010", workitem.getItemValueString("invoice.number"));
        assertEquals("Max Mustermann", workitem.getItemValueString("cdtr.name"));

    }

    @Test
    void testExecuteWithZIPContainingXML() throws AdapterException, PluginException, IOException {
        // Prepare test data
        FileData zipFile = createFileData("e-invoice/XRechnung_Beispiel.zip", "application/zip");
        workitem.addFileData(zipFile);

        FileData fileData = adapter.detectEInvoice(workitem);
        String result = EInvoiceAdapter.detectEInvoiceType(fileData);
        // Verify the result
        assertNotNull(result);
        assertEquals("Factur-X/ZUGFeRD 2.0", result);
    }

    @Test
    void testExecuteWithNonEInvoiceFile() throws AdapterException, PluginException, IOException {
        // Prepare test data
        FileData txtFile = createFileData("e-invoice/document.txt", "text/plain");
        workitem.addFileData(txtFile);

        // Execute the adapter
        FileData fileData = adapter.detectEInvoice(workitem);
        // Verify the result
        assertNull(fileData);
    }

    @Test
    void testExecuteWithNoAttachments() throws AdapterException, PluginException {
        // Execute the adapter
        FileData fileData = adapter.detectEInvoice(workitem);
        // Verify the result
        assertNull(fileData);
    }

    /**
     * Creates a FileData object from a file stored under /test/resources/
     * 
     * @param fileName
     * @param contentType
     * @return
     * @throws IOException
     */
    private FileData createFileData(String fileName, String contentType) throws IOException {
        byte[] content = null;
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream is = classLoader.getResourceAsStream(fileName)) {
            if (is == null) {
                throw new IOException("Resource not found: " + fileName);
            }
            content = is.readAllBytes();
        }
        Map<String, List<Object>> attributes = new HashMap<>();
        return new FileData(fileName, content, contentType, attributes);
    }

}