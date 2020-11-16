package org.imixs.archive.signature;

import java.io.File;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.imixs.archive.signature.ca.X509CertificateGenerator;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * This test class tests X509CertificateGenerator
 * 
 * @author rsoika
 * @version 1.0
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class X509CertificateGeneratorTest {

    String resourcesPath = null;
    String keyStorePath = null;
    String keystorePassword = "123456";
    KeystoreService keystoreService;

    X509CertificateGenerator x509CertificateGenerator = null;

    /**
     * Init resource path to test resources
     * 
     * @throws PluginException
     * @throws ModelException
     */
    @Before
    public void setup() throws PluginException, ModelException {
        resourcesPath = new File("src/test/resources").getAbsolutePath();
        keyStorePath = resourcesPath + "/keystore/test.jks";
        keystoreService = new KeystoreService(keyStorePath, "123456", "PKCS12");

        
        
    }
    

    /**
     * This test method generates a new password protected root certificate.
     * <p>
     * Note: a password protection is optional but should be used for root
     * certificates!
     */
    @Test
    public void test001GenerateRootCert() {
        try {
            KeyStore keyStore = keystoreService.openKeyStore();
            
            x509CertificateGenerator = new X509CertificateGenerator();
            KeyPair rootKeyPair = x509CertificateGenerator.generateKeyPair();

            X509Certificate rootCert = x509CertificateGenerator.generateRootCertificate(rootKeyPair, "root-cert");
            // write to keystore....
            x509CertificateGenerator.writeCertToFileBase64Encoded(rootCert, resourcesPath + "/root-cert.cer");

            X509Certificate[] certChain = new X509Certificate[] {  rootCert };
            x509CertificateGenerator.exportKeyPairToKeystore(certChain, rootKeyPair.getPrivate(), "rootcert-secret", "root-cert",
                    keyStore, keyStorePath, "123456");

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    /**
     * Test method generates a new certificate signed by a root certificate.
     */
    @Test
    public void test002CreateSignedCertFromRootCert() {

        try {
            KeyStore keyStore = keystoreService.openKeyStore();
            x509CertificateGenerator = new X509CertificateGenerator();
            
            Certificate[] rootCertChain = keystoreService.loadCertificate("root-cert");
            X509Certificate rootCert=(X509Certificate) rootCertChain[0];
            PrivateKey rootPrivKey=keystoreService.loadPrivateKey("root-cert", "rootcert-secret");

            KeyPair issueKeyPair = x509CertificateGenerator.generateKeyPair();

            List<String>ou=new ArrayList<String>();
            ou.add("Development");
            ou.add("Software");
            Certificate[] certChain = x509CertificateGenerator.generateSignedCertificate(rootCert,rootPrivKey,issueKeyPair, "M. Melman", 
                    "Imixs",ou, "Munich", "BAY", "DE");

            Assert.assertNotNull(certChain);
            Assert.assertEquals(2, certChain.length);
            
            // export results to the file system
            x509CertificateGenerator.writeCertToFileBase64Encoded(certChain[0], resourcesPath + "/melman.cer");
            x509CertificateGenerator.exportKeyPairToKeystore(certChain, issueKeyPair.getPrivate(), null, "melman",
                    keyStore,  keyStorePath, "123456");

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    
    /**
     * Load cert chain by alias name
     */
    @Test
    public void test003LoadCertificate() {
        try {
            Certificate[] certChain = keystoreService.loadCertificate("melman");
            Assert.assertNotNull(certChain);
            Assert.assertEquals(2, certChain.length);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
}
