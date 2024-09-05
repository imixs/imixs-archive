package org.imixs.archive.exporter;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

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
            fail();
        }

        // assertTrue(name.matches(filter));

    }

}
