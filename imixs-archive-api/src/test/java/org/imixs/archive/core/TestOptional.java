package org.imixs.archive.core;

import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test the Optional expression
 * 
 * @author rsoika
 * 
 */
public class TestOptional {

    /**
     * Test different szenarios
     * 
     */
    @Test
    public void testOptional() {
        String name = "a";
        Optional<String> opt = Optional.of(name);
        Assert.assertTrue(opt.isPresent());

        name = "";
        opt = Optional.of(name);
        Assert.assertTrue(opt.isPresent());
        

        Assert.assertFalse(opt.isPresent() && !opt.get().isEmpty());
    }

}
