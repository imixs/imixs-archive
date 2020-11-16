package org.imixs.archive.signature;

import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * This class tests the KeystoreService
 * 
 * @author rsoika
 * @version 1.0
 */
@Ignore
public class KeyStoreServiceTest {

    String resourcesPath = null;
    String keystorePassword = "123456";

    KeystoreService keystoreService;

    /**
     * Init resource path to test resources
     * 
     * @throws PluginException
     * @throws ModelException
     */
    @Before
    public void setup() throws PluginException, ModelException {
        resourcesPath = new File("src/test/resources").getAbsolutePath();
    }

    /**
     * Test opening an existing keystore
     * 
     */
    @Test
    public void testOpenKeyStore() {
        KeyStore keystore = null;

        keystoreService = new KeystoreService(resourcesPath + "/keystore/imixs.jks", "123456", "PKCS12");

        try {
            keystore = keystoreService.openKeyStore();
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            e.printStackTrace();
            Assert.fail();
        }

        Assert.assertNotNull(keystore);
    }

  

}
