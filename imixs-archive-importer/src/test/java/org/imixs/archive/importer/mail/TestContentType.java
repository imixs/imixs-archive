package org.imixs.archive.importer.mail;

import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;

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
        Assert.assertEquals("text/html", contentType);
    }

}
