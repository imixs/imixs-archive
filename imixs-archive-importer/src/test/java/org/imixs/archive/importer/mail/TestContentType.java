package org.imixs.archive.importer.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

/**
 * Test strip contenttype
 * 
 * @author rsoika
 * 
 */
public class TestContentType {
    private final static Logger logger = Logger.getLogger(TestContentType.class.getName());

    @Test
    public void testConverter() {
        String contentType = "text/html;xxx";
        if (contentType.contains(";")) {
            contentType = contentType.substring(0, contentType.indexOf(";"));
        }

        logger.info("'" + contentType + "'");
        assertEquals("text/html", contentType);
    }

}
