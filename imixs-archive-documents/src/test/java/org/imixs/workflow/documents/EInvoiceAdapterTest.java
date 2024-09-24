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
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * This test class is testing the EInvoiceDetectorAdapter and tests different
 * kind of files
 * 
 * 
 */
@ExtendWith(MockitoExtension.class)
class EInvoiceAdapterTest {

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private EInvoiceAdapter adapter;

    private ItemCollection workitem;
    private ItemCollection event;

    @BeforeEach
    void setUp() {
        workitem = new ItemCollection();
        event = new ItemCollection();
    }

    @Test
    void testExecuteWithPDFContainingXML() throws AdapterException, PluginException, IOException {
        // Prepare test data
        FileData pdfFile = createFileData("e-invoice/Rechnung_R_00011.pdf", "application/pdf");
        workitem.addFileData(pdfFile);

        // Execute the adapter
        String result = adapter.detectEInvoiceFormat(workitem);

        // Verify the result
        assertNotNull(result);
        assertEquals("Factur-X/ZUGFeRD 2.0", result);
    }

    @Test
    void testExecuteWithStandaloneXML() throws AdapterException, PluginException, IOException {
        // Prepare test data
        FileData xmlFile = createFileData("e-invoice/Rechnung_R_00010.xml", "application/xml");
        workitem.addFileData(xmlFile);

        String result = adapter.detectEInvoiceFormat(workitem);

        // Verify the result
        assertNotNull(result);
        assertEquals("Factur-X/ZUGFeRD 2.0", result);
    }

    @Test
    void testExecuteWithZIPContainingXML() throws AdapterException, PluginException, IOException {
        // Prepare test data
        FileData zipFile = createFileData("e-invoice/XRechnung_Beispiel.zip", "application/zip");
        workitem.addFileData(zipFile);

        String result = adapter.detectEInvoiceFormat(workitem);

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
        String result = adapter.detectEInvoiceFormat(workitem);

        // Verify the result
        assertNull(result);
    }

    @Test
    void testExecuteWithNoAttachments() throws AdapterException, PluginException {
        // Execute the adapter
        String result = adapter.detectEInvoiceFormat(workitem);
        // Verify the result
        assertNull(result);
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