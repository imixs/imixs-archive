package org.imixs.archive.signature.ca;

import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Logger;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.encoders.Base64;
import org.imixs.archive.signature.KeystoreService;

/**
 * Generate root X509Certificate, Sign a Certificate from the root certificate
 * by generating a CSR (Certificate Signing Request) and save the certificates
 * to a keystore using BouncyCastle 1.5x
 * 
 * @see https://gist.github.com/vivekkr12/c74f7ee08593a8c606ed96f4b62a208a
 * 
 * @author rsoika
 *
 */
public class CAService {

    private static final String BC_PROVIDER = "BC";
    private static final String KEY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static Logger logger = Logger.getLogger(KeystoreService.class.getName());

    public static X509Certificate generate(KeyStore sslKeyStore, X509Certificate rootCert, PrivateKey rootPrivatKey,
            String alias, String cn, String ou, String o, String city, String state, String country) throws Exception {
        
        logger.info("...generating new certificate for user " + alias + "...");
        
        // Add the BouncyCastle Provider
        Security.addProvider(new BouncyCastleProvider());

        // Initialize a new KeyPair generator
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM, BC_PROVIDER);
        keyPairGenerator.initialize(2048);

        // Setup start date to yesterday and end date for 1 year validity
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        Date startDate = calendar.getTime();

        calendar.add(Calendar.YEAR, 1);
        Date endDate = calendar.getTime();

        writeCertToFileBase64Encoded(rootCert, "root-cert.cer");

        // Generate a new KeyPair and sign it using the Root Cert Private Key
        // by generating a CSR (Certificate Signing Request)
        X500Name issuedCertSubject = new X500Name("CN=" + cn);
        BigInteger issuedCertSerialNum = new BigInteger(Long.toString(new SecureRandom().nextLong()));
        KeyPair issuedCertKeyPair = keyPairGenerator.generateKeyPair();

        PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(issuedCertSubject,
                issuedCertKeyPair.getPublic());

        JcaContentSignerBuilder csrBuilder = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(BC_PROVIDER);

        // Sign the new KeyPair with the root cert Private Key
        ContentSigner csrContentSigner = csrBuilder.build(rootPrivatKey);
        PKCS10CertificationRequest csr = p10Builder.build(csrContentSigner);

        // Use the Signed KeyPair and CSR to generate an issued Certificate
        // Here serial number is randomly generated. In general, CAs use
        // a sequence to generate Serial number and avoid collisions
        // String rootCertIssuer=rootCert.getIssuerDN();

        // as we sing the certificate from a existing intermediate certificate and not
        // from a self singed root certificate we neee to take the SubjectDN from the
        // rootCert instead of the IssueDN here!
        // X500Name rootCertIssuer = new X500Name(rootCert.getIssuerDN().toString());
        X500Name rootCertIssuer = new X500Name(rootCert.getSubjectDN().toString());

        X500NameBuilder builder = new X500NameBuilder(RFC4519Style.INSTANCE);
        builder.addRDN(RFC4519Style.cn, cn);
        builder.addRDN(RFC4519Style.ou, ou);
        builder.addRDN(RFC4519Style.o, o);
        builder.addRDN(RFC4519Style.l, city);
        builder.addRDN(RFC4519Style.st, state);

        X509v3CertificateBuilder issuedCertBuilder = new X509v3CertificateBuilder(rootCertIssuer, issuedCertSerialNum,
                startDate, endDate, builder.build(), csr.getSubjectPublicKeyInfo());

        JcaX509ExtensionUtils issuedCertExtUtils = new JcaX509ExtensionUtils();

        // Add Extensions
        // Use BasicConstraints to say that this Cert is not a CA
        issuedCertBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        // Add Issuer cert identifier as Extension
        issuedCertBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                issuedCertExtUtils.createAuthorityKeyIdentifier(rootCert));
        issuedCertBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                issuedCertExtUtils.createSubjectKeyIdentifier(csr.getSubjectPublicKeyInfo()));

        // Add intended key usage extension for digitalSignature
        issuedCertBuilder.addExtension(Extension.keyUsage, false, new KeyUsage(KeyUsage.digitalSignature));

        X509CertificateHolder issuedCertHolder = issuedCertBuilder.build(csrContentSigner);
        X509Certificate issuedCert = new JcaX509CertificateConverter().setProvider(BC_PROVIDER)
                .getCertificate(issuedCertHolder);

        // Verify the issued cert signature against the root (issuer) cert
        issuedCert.verify(rootCert.getPublicKey(), BC_PROVIDER);

        writeCertToFileBase64Encoded(issuedCert, "issued-cert.cer");
        exportKeyPairToKeystoreFile(issuedCertKeyPair, issuedCert, "issued-cert", "issued-cert.pfx", "PKCS12", "pass");

        exportKeyPairToKeystore(sslKeyStore, issuedCertKeyPair.getPrivate(), rootCert, issuedCert, alias,
                "mytestkeystore.jks", "123456");
        return issuedCert;

    }

    public static void exportKeyPairToKeystore(KeyStore sslKeyStore, PrivateKey privKey, Certificate rootcert,
            Certificate certificate, String alias, String fileName, String storePass) throws Exception {

        // Build a certificate chain. First the issuerCert and then the rootCert
        Certificate[] certChain = new Certificate[] { certificate, rootcert };

      
//      
//       KeyStore.SecretKeyEntry secret = new KeyStore.SecretKeyEntry((SecretKey) privKey);
//      KeyStore.ProtectionParameter password    = new KeyStore.PasswordProtection("".toCharArray());
//      sslKeyStore.setEntry(alias, secret, password);
//       
//       

        sslKeyStore.setKeyEntry(alias, privKey, null, certChain);
        FileOutputStream keyStoreOs = new FileOutputStream(fileName);
        sslKeyStore.store(keyStoreOs, storePass.toCharArray());
    }

    /**
     * Helper method to export int a keystore
     * 
     * @param keyPair
     * @param certificate
     * @param alias
     * @param fileName
     * @param storeType
     * @param storePass
     * @throws Exception
     */
    static void exportKeyPairToKeystoreFile(KeyPair keyPair, Certificate certificate, String alias, String fileName,
            String storeType, String storePass) throws Exception {
        KeyStore sslKeyStore = KeyStore.getInstance(storeType, BC_PROVIDER);
        sslKeyStore.load(null, null);
        sslKeyStore.setKeyEntry(alias, keyPair.getPrivate(), null, new Certificate[] { certificate });
        FileOutputStream keyStoreOs = new FileOutputStream(fileName);
        sslKeyStore.store(keyStoreOs, storePass.toCharArray());
    }

    static void writeCertToFileBase64Encoded(Certificate certificate, String fileName) throws Exception {
        FileOutputStream certificateOut = new FileOutputStream(fileName);
        certificateOut.write("-----BEGIN CERTIFICATE-----".getBytes());
        certificateOut.write(Base64.encode(certificate.getEncoded()));
        certificateOut.write("-----END CERTIFICATE-----".getBytes());
        certificateOut.close();
    }

}
