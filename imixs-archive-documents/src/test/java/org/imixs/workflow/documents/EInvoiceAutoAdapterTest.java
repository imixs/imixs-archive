package org.imixs.workflow.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.imixs.archive.documents.EInvoiceAdapter;
import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.engine.WorkflowService;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * This test class is testing the EInvoiceAutoAdapter and tests different
 * kind of files
 * 
 * 
 */
@ExtendWith(MockitoExtension.class)
class EInvoiceAutoAdapterTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private WorkflowService workflowService;

    @InjectMocks
    private EInvoiceAdapter adapter;

    private ItemCollection workitem;
    private ItemCollection event;

    @BeforeEach
    void setUp() throws PluginException, ModelException {
        // MockitoAnnotations.openMocks(this);

        workitem = new ItemCollection();
        event = new ItemCollection();
        // set test txtActivityResult....
        String config = "<e-invoice name=\"READ\">\n";
        config += "   <item>invoice.number=//rsm:CrossIndustryInvoice/rsm:ExchangedDocument/ram:ID</item>\n";
        config += "   <item>invoice.date=//rsm:CrossIndustryInvoice/rsm:ExchangedDocument/ram:IssueDateTime</item>\n";
        config += "</e-invoice>";
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