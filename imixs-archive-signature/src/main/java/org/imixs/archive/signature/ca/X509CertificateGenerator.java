package org.imixs.archive.signature.ca;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.encoders.Base64;
import org.imixs.archive.signature.KeystoreService;

/**
 * The X509CertificateGenerator provides static methods to generate
 * X509Certificates to be used for digital Signatures. Certificates can be
 * singed by an existing root or intermediate Certificate stored in a keystore.
 * The X509CertificateGenerator is used by the Imixs-Archive CAService.
 * <p>
 * Certificates stored in the keystore can be protected with an additional
 * optional password. This is recommended for root certificates and intermediate
 * certificates.
 * 
 * <p>
 * The DEFAULT_KEY_ALGORITHM is "RSA", the DEFAULT_SIGNATURE_ALGORITHM is
 * "SHA256withRSA". Both can be changed by properties.
 * 
 * <p>
 * The service is based on the BouncyCastle library v 1.67
 * 
 *
 * @see CAService
 * @author rsoika
 * @version 1.0
 */
public class X509CertificateGenerator {

    private static final String BC_PROVIDER = "BC";
    private static final String DEFAULT_KEY_ALGORITHM = "RSA";
    private static final String DEFAULT_SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static Logger logger = Logger.getLogger(KeystoreService.class.getName());

    private String keyAlgorithm = DEFAULT_KEY_ALGORITHM;
    private String signatureAlgorithm = DEFAULT_SIGNATURE_ALGORITHM;
    // private KeyPairGenerator keyPairGenerator = null;

    public X509CertificateGenerator()
            throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException, NoSuchProviderException {
        super();

        // Add the BouncyCastle Provider
        Security.addProvider(new BouncyCastleProvider());

    }

    public String getKeyAlgorithm() {
        return keyAlgorithm;
    }

    public void setKeyAlgorithm(String keyAlgorithm) {
        this.keyAlgorithm = keyAlgorithm;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    /**
     * This method generates a self signed root certificate. The root certificate
     * can be used to generate signed X509Certificates.
     * <p>
     * The method returns the root certificate.
     * 
     * 
     * @param rootKeyPair - key pair used to generated the certificate
     * @param cn          - common name of the root certificate
     * @return a signed X509Certificate
     * 
     * @throws OperatorCreationException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws CertificateException
     * @throws KeyStoreException
     * @throws Exception
     */
    public X509Certificate generateRootCertificate(KeyPair rootKeyPair, String cn) throws OperatorCreationException,
            NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {

        // Setup start date to yesterday and end date for 1 year validity
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        Date startDate = calendar.getTime();

        calendar.add(Calendar.YEAR, 1);
        Date endDate = calendar.getTime();

        BigInteger rootSerialNum = new BigInteger(Long.toString(new SecureRandom().nextLong()));

        // Issued By and Issued To same for root certificate
        X500Name rootCertIssuer = new X500Name("CN=" + cn);
        X500Name rootCertSubject = rootCertIssuer;
        ContentSigner rootCertContentSigner = new JcaContentSignerBuilder(getSignatureAlgorithm())
                .setProvider(BC_PROVIDER).build(rootKeyPair.getPrivate());
        X509v3CertificateBuilder rootCertBuilder = new JcaX509v3CertificateBuilder(rootCertIssuer, rootSerialNum,
                startDate, endDate, rootCertSubject, rootKeyPair.getPublic());

        // Add Extensions
        // A BasicConstraint to mark root certificate as CA certificate
        JcaX509ExtensionUtils rootCertExtUtils = new JcaX509ExtensionUtils();
        rootCertBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        rootCertBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                rootCertExtUtils.createSubjectKeyIdentifier(rootKeyPair.getPublic()));

        // Create a cert holder and export to X509Certificate
        X509CertificateHolder rootCertHolder = rootCertBuilder.build(rootCertContentSigner);
        X509Certificate rootCert = new JcaX509CertificateConverter().setProvider(BC_PROVIDER)
                .getCertificate(rootCertHolder);

        // return the certificate
        return rootCert;
    }

    /**
     * This method generates a new X509Certificate and signs the certificate from a
     * given root/intermediate certificate by generating a CSR (Certificate Signing
     * Request).
     * <p>
     * The method returns a certificate chain containing the issuer certificate and
     * the root certificate. A certificate chain can be stored in a keyStore.
     * 
     * @throws NoSuchAlgorithmException
     * @throws OperatorCreationException
     * @throws CertIOException
     * @throws CertificateException
     * @throws SignatureException
     * @throws NoSuchProviderException
     * @throws InvalidKeyException
     * @see https://gist.github.com/vivekkr12/c74f7ee08593a8c606ed96f4b62a208a
     *
     */
    public X509Certificate[] generateSignedCertificate(X509Certificate rootCert, PrivateKey rootPrivateKey,
            KeyPair issuedCertKeyPair, String cn,String o, List<String> ou,  String city, String state, String country)
            throws NoSuchAlgorithmException, OperatorCreationException, CertIOException, CertificateException,
            InvalidKeyException, NoSuchProviderException, SignatureException {

        logger.fine("...generating new certificate for user " + cn + "...");

        if (cn == null || cn.isEmpty()) {
            throw new IllegalArgumentException("cn is empty or null!");
        }
        // Setup start date to yesterday and end date for 1 year validity
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        Date startDate = calendar.getTime();

        calendar.add(Calendar.YEAR, 1);
        Date endDate = calendar.getTime();

        // Generate a new KeyPair and sign it using the Root Cert Private Key
        // by generating a CSR (Certificate Signing Request)
        X500Name issuedCertSubject = new X500Name("CN=" + cn);
        BigInteger issuedCertSerialNum = new BigInteger(Long.toString(new SecureRandom().nextLong()));

        PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(issuedCertSubject,
                issuedCertKeyPair.getPublic());

        JcaContentSignerBuilder csrBuilder = new JcaContentSignerBuilder(getSignatureAlgorithm())
                .setProvider(BC_PROVIDER);

        // Sign the new KeyPair with the root cert Private Key
        ContentSigner csrContentSigner = csrBuilder.build(rootPrivateKey);
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
        if (ou != null && !ou.isEmpty()) {
            // here we add a list of OUs...
            for (String _ou: ou) {
                builder.addRDN(RFC4519Style.ou, _ou);
            }
        }
        if (o != null && !o.isEmpty()) {
            builder.addRDN(RFC4519Style.o, o);
        }
        if (city != null && !city.isEmpty()) {
            builder.addRDN(RFC4519Style.l, city);
        }
        if (state != null && !state.isEmpty()) {
            builder.addRDN(RFC4519Style.st, state);
        }
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

        // Build a certificate chain. First the issuerCert and then the rootCert
        X509Certificate[] certChain = new X509Certificate[] { issuedCert, rootCert };

        // return the certificate chain
        return certChain;

    }

    /**
     * Generates a new keyPair.
     * 
     * @return
     */
    public KeyPair generateKeyPair() {
        // Initialize a new KeyPair generator
        KeyPairGenerator keyPairGenerator;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance(getKeyAlgorithm(), BC_PROVIDER);
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            logger.severe("Failed to generate keypair: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * This method stores the certificate into the given keystore.
     * 
     * 
     * 
     * @param certificate - the certificate to be stored
     * @param privKey     - the associated private key
     * @param password    - optional password to protect the entry, can be null
     * @param alias       - alias name to store the entry
     * @param fileName    - filename of the keystore location.
     * @param storePass   - password for keystore
     * @throws KeyStoreException
     * @throws IOException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws Exception
     */
    public void exportKeyPairToKeystore(Certificate[] certChain, PrivateKey privKey, String password, String alias,
            KeyStore keyStore, String fileName, String storePass)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        if (password == null) {
            keyStore.setKeyEntry(alias, privKey, null, certChain);
        } else {
            keyStore.setKeyEntry(alias, privKey, password.toCharArray(), certChain);
        }
        FileOutputStream keyStoreOs = new FileOutputStream(fileName);
        keyStore.store(keyStoreOs, storePass.toCharArray());
    }

    /**
     * This method writes a given certificate into a file.
     * 
     * @param certificate
     * @param fileName
     * @throws Exception
     */
    public void writeCertToFileBase64Encoded(Certificate certificate, String fileName) throws Exception {
        FileOutputStream certificateOut = new FileOutputStream(fileName);
        certificateOut.write("-----BEGIN CERTIFICATE-----".getBytes());
        certificateOut.write(Base64.encode(certificate.getEncoded()));
        certificateOut.write("-----END CERTIFICATE-----".getBytes());
        certificateOut.close();
    }

}
