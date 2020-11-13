package org.imixs.archive.signature;

import java.io.File;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import org.imixs.archive.signature.ca.X509CertificateGenerator;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * This test class tests X509CertificateGenerator
 * 
 * @author rsoika
 * @version 1.0
 */
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
    public void testGenerateRootCert() {
        try {
            KeyStore keyStore = keystoreService.openKeyStore();
            x509CertificateGenerator = new X509CertificateGenerator(keyStore, "root-cert", "123456");

            KeyPair rootKeyPair = x509CertificateGenerator.generateKeyPair();
            
        
            
            
            X509Certificate rootCert = x509CertificateGenerator.generateRootCertificate(rootKeyPair, "root-cert");

            x509CertificateGenerator.writeCertToFileBase64Encoded(rootCert, "root-cert.cer");

            // write to keystore....
            x509CertificateGenerator.writeCertToFileBase64Encoded(rootCert, resourcesPath + "/root-cert.cer");

            x509CertificateGenerator.exportKeyPairToKeystore(rootCert, rootKeyPair.getPrivate(),"pass", "root-cert",
                    keyStorePath, "123456");

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    /**
     * Test method generates a new certificate signed by a root certificate.
     */
    @Test
    public void testCreateSignedCertFromRootCert() {

        try {
            KeyStore keyStore = keystoreService.openKeyStore();
            x509CertificateGenerator = new X509CertificateGenerator(keyStore, "root-cert", "pass");

            KeyPair issueKeyPair = x509CertificateGenerator.generateKeyPair();

            X509Certificate certificate = x509CertificateGenerator.generateSignedCertificate(issueKeyPair, "Melman", "Dev",
                    "Imixs", "Berlin", "BAD", "DE");

            Assert.assertNotNull(certificate);

            // export results to the file system
            x509CertificateGenerator.writeCertToFileBase64Encoded(certificate, resourcesPath + "/melman.cer");
            x509CertificateGenerator.exportKeyPairToKeystore(certificate, issueKeyPair.getPrivate(),null, "melman",
                    keyStorePath, "123456");

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

}
