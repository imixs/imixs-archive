package org.imixs.archive.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * This test class is testing the EInvoiceAutoAdapter and tests different
 * kind of files
 * 
 */
class EInvoiceAutoAdapterTest {

    @InjectMocks
    protected EInvoiceAutoAdapter adapter;

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

    }

    /**
     * Simple test that extracts the invoice from a XML file in ubl format
     * 
     * @throws AdapterException
     * @throws PluginException
     * @throws IOException
     */
    @Test
    void testUBLFormat() throws AdapterException, PluginException, IOException {
        // Prepare test data
        FileData xmlFile = createFileData("e-invoice/EN16931_Einfach.ubl.xml", "application/xml");
        workitem.addFileData(xmlFile);

        adapter.execute(workitem, event);

        assertEquals("471102", workitem.getItemValueString("invoice.number"));

        LocalDate invoiceDate = workitem.getItemValueDate("invoice.date").toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        assertEquals(LocalDate.of(2018, 03, 05), invoiceDate);
        assertEquals("Lieferant GmbH", workitem.getItemValueString("cdtr.name"));

        assertEquals(529.87, workitem.getItemValueDouble("invoice.total"));
        assertEquals(473.0, workitem.getItemValueDouble("invoice.total.net"));
        assertEquals(56.87, workitem.getItemValueDouble("invoice.total.tax"));
    }

    /**
     * Simple test that extracts the invoice from a XML file - number and the
     * creditor name
     * 
     * @throws AdapterException
     * @throws PluginException
     * @throws IOException
     */
    @Test
    void testXMLWithExtraction() throws AdapterException, PluginException, IOException {
        // Prepare test data
        FileData xmlFile = createFileData("e-invoice/Rechnung_R_00010.xml", "application/xml");
        workitem.addFileData(xmlFile);

        adapter.execute(workitem, event);

        assertEquals("R-00010", workitem.getItemValueString("invoice.number"));
        assertEquals("Max Mustermann", workitem.getItemValueString("cdtr.name"));
        LocalDate invoiceDate = workitem.getItemValueDate("invoice.date").toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        assertEquals(LocalDate.of(2021, 7, 28), invoiceDate);

    }

    /**
     * Simple test that extracts the invoice from a zugferd pdf file - number and
     * the creditor name
     * 
     * @throws AdapterException
     * @throws PluginException
     * @throws IOException
     */
    @Test
    void testZugferdWithExtraction() throws AdapterException, PluginException, IOException {
        // Prepare test data
        FileData xmlFile = createFileData("e-invoice/Rechnung_R_00011.pdf", "application/pdf");
        workitem.addFileData(xmlFile);

        adapter.execute(workitem, event);

        assertEquals("R-00011", workitem.getItemValueString("invoice.number"));
        assertEquals("Max Mustermann", workitem.getItemValueString("cdtr.name"));

        LocalDate invoiceDate = workitem.getItemValueDate("invoice.date").toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        assertEquals(LocalDate.of(2021, 7, 27), invoiceDate);

        // Payment data
        assertEquals(892.50, workitem.getItemValueFloat("invoice.total"));
        assertEquals(750, workitem.getItemValueFloat("invoice.total.net"));
        assertEquals(142.5, workitem.getItemValueFloat("invoice.total.tax"));
        // assertEquals("xxxxxR-00011", workitem.getItemValueString("cdtr.iban"));
        // assertEquals("xxxxxR-00011", workitem.getItemValueString("cdtr.bic"));

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