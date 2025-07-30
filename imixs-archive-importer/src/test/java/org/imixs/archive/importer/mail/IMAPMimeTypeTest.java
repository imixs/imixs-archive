package org.imixs.archive.importer.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This test class is testing the IMAPImportHelper to fix mime types
 * 
 */
class IMAPMimeTypeTest {

    @BeforeEach
    public void setUp() throws PluginException, ModelException {

    }

    @Test
    void testStandaloneXML() {
        // application/.pdf → application/pdf
        assertEquals("application/pdf", IMAPImportHelper.fixContentType("application/.pdf", "test.pdf", true));

        // text/html;xxx
        assertEquals("text/html", IMAPImportHelper.fixContentType("text/html;xxx", "test.pdf", true));

        // test 'application/octet-stream' into 'application/pdf'
        assertEquals("application/pdf",
                IMAPImportHelper.fixContentType("application/octet-stream", "test.pdf", true));

        // test non pdf
        assertEquals("application/octet-stream",
                IMAPImportHelper.fixContentType("application/octet-stream", "test.cad", true));

    }

}