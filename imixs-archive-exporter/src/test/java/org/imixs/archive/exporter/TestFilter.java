package org.imixs.archive.exporter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test the Optional expression
 *
 * @author rsoika
 *
 */
public class TestFilter {

    /**
     * Test filter regex
     *
     */
    @Test
    public void testOptional() {
        String name = "example.pdf";

        String filter = "(\\.pdf$)";

        Pattern pattern = Pattern.compile(filter);
        Matcher matcher = pattern.matcher(name);

        if (!matcher.find()) {
            Assert.fail();
        }

        // Assert.assertTrue(name.matches(filter));

    }

}
