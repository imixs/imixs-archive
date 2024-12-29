package org.imixs.archive.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.imixs.einvoice.EInvoiceModel;
import org.imixs.einvoice.EInvoiceModelFactory;
import org.imixs.einvoice.TradeParty;
import org.imixs.workflow.FileData;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This test class is testing the EInvoiceModel and tests different
 * kind of files
 * 
 */
class EInvoiceModelTest {

    @BeforeEach
    public void setUp() throws PluginException, ModelException {

    }

    @Test
    void testStandaloneXML() throws AdapterException, PluginException, IOException {
        // Prepare test data

        EInvoiceModel eInvoiceModel = loadEInvoice("e-invoice/Rechnung_R_00010.xml", "application/xml");

        // Verify the result
        assertNotNull(eInvoiceModel);
        assertEquals("R-00010", eInvoiceModel.getId());

        LocalDate invoiceDate = eInvoiceModel.getIssueDateTime();
        assertEquals(LocalDate.of(2021, 7, 28), invoiceDate);

        assertEquals(new BigDecimal("4380.9"), eInvoiceModel.getGrandTotalAmount());
        assertEquals(new BigDecimal("510.9"), eInvoiceModel.getTaxTotalAmount());
        assertEquals(new BigDecimal("3870.00"), eInvoiceModel.getNetTotalAmount());

        // Test SellerTradeParty
        TradeParty seller = eInvoiceModel.findTradeParty("seller");
        assertNotNull(seller);
        assertEquals("Max Mustermann", seller.getName());
        assertEquals("DE111111111", seller.getVatNumber());

        TradeParty buyer = eInvoiceModel.findTradeParty("buyer");
        assertNotNull(buyer);
        assertEquals("Viborg Metall GbR", buyer.getName());

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

    /**
     * Reads a e-invoice file into a EInvoice object
     * 
     * @param fileName
     * @param contentType
     * @return
     * @throws IOException
     */
    private EInvoiceModel loadEInvoice(String fileName, String contentType) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream is = classLoader.getResourceAsStream(fileName)) {
            if (is == null) {
                throw new IOException("Resource not found: " + fileName);
            }
            return EInvoiceModelFactory.read(is);
        }
    }

}