package org.imixs.archive.signature.draft;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.Store;

/**
 * Generates certificates based on the Bouncycastle library.
 * 
 * @author rsoika
 *
 */
public class CertGenerator {

    private static Logger logger = Logger.getLogger(CertGenerator.class.getName());
    public static String _country = "Westeros", _organisation = "Targaryen", _location = "Valyria", _state = "Essos",
            _issuer = "Some Trusted CA";

    /**
     * Creates a X509 certificate
     * 
     * 
     * @param privKey
     * @param pubKey
     * @param duration
     * @param signAlg
     * @param isSelfSigned
     * @return
     * @throws OperatorCreationException
     * @throws CertificateException
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     * @throws SignatureException
     */
    public static X509Certificate createCertificate(PrivateKey privKey, PublicKey pubKey, int duration,
            boolean isSelfSigned) throws OperatorCreationException, CertificateException, InvalidKeyException,
            NoSuchAlgorithmException, NoSuchProviderException, SignatureException {
        Provider BC = new BouncyCastleProvider();

        // distinguished name table.
        X500NameBuilder builder = createStdBuilder();

        // Create a ContentSigner object using helper JcaContentSignerBuilder
        // Signer object is created using privateKey and “SHA1withRSA” algorithm.
        ContentSigner sigGen = new JcaContentSignerBuilder("SHA256withRSA").build(privKey);
        X509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(new X500Name("cn=" + _issuer), // Issuer
                BigInteger.valueOf(1), // Serial
                new Date(System.currentTimeMillis() - 50000), // Valid from
                new Date((long) (System.currentTimeMillis() + duration * 8.65 * Math.pow(10, 7))), // Valid to
                builder.build(), // Subject
                pubKey // Publickey to be associated with the certificate
        );

        X509Certificate cert = new JcaX509CertificateConverter().setProvider(BC).getCertificate(certGen.build(sigGen));

        cert.checkValidity(new Date());

        if (isSelfSigned) {
            // check verifies in general
            cert.verify(pubKey);
            // check verifies with contained key
            cert.verify(cert.getPublicKey());
        }

        ByteArrayInputStream bIn = new ByteArrayInputStream(cert.getEncoded());
        CertificateFactory fact = CertificateFactory.getInstance("X.509", BC);

        return (X509Certificate) fact.generateCertificate(bIn);
    }

    /**
     * helper method
     * 
     * @return
     */
    private static X500NameBuilder createStdBuilder() {
        X500NameBuilder builder = new X500NameBuilder(RFC4519Style.INSTANCE);

        builder.addRDN(RFC4519Style.c, _country);
        builder.addRDN(RFC4519Style.o, _organisation);
        builder.addRDN(RFC4519Style.l, _location);
        builder.addRDN(RFC4519Style.st, _state);

        return builder;
    }

    public static void createNewEntry(KeyStore keyStore, String alias, char[] pwdArray) {

        try {

            // create keypair
            SecureRandom random = new SecureRandom();
            KeyPairGenerator keypairGen = KeyPairGenerator.getInstance("RSA");
            // KeyPairGenerator keypairGen = KeyPairGenerator.getInstance("SHA256withRSA");
            keypairGen.initialize(2048, random);
            KeyPair keypair = keypairGen.generateKeyPair();

            PublicKey publicKey = keypair.getPublic();
            PrivateKey privateKey = keypair.getPrivate();

            logger.info("algo2=" + privateKey.getAlgorithm());

            // load the root cert...
            Certificate caCert = keyStore.getCertificate("imixs.com");

            X509Certificate clientCert = createCertificate(privateKey, publicKey, 5000, true);

            X509Certificate[] certificateChain = new X509Certificate[1];
            certificateChain[0] = clientCert;
            // certificateChain[0] = (X509Certificate) cert2;
            // certificateChain[1] = (X509Certificate) caCert;

            // PrivateKey rootPrivKey = (PrivateKey) keyStore.getKey("imixs.com",pwdArray);

            keyStore.setKeyEntry(alias, privateKey, pwdArray, certificateChain);
            // keyStore.setKeyEntry(alias, rootPrivKey, pwdArray, certificateChain);

            // https://stackoverrun.com/de/q/12542528

        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | InvalidKeyException
                | OperatorCreationException | NoSuchProviderException | SignatureException e) {
            logger.warning("Failed to load certificate chain for alias '" + alias + "' - " + e.getMessage());
            e.printStackTrace();

        }

    }

    public static void testMethodic2(KeyStore keyStore) {
        try {
            
            // load the root cert...
            Certificate cacert = keyStore.getCertificate("imixs.com");
            PrivateKey  cakey = (PrivateKey) keyStore.getKey("imixs.com", "123456".toCharArray());
            
            
            KeyPair keyPair = generateKeyPair("RSA", 2048);
            X509Certificate cert = createCertificate(keyPair.getPrivate(), keyPair.getPublic(), 100, true);
            PKCS10CertificationRequest csr = CSRGenerator.generateCSR(cert.getSubjectX500Principal(),
                    keyPair.getPrivate(), keyPair.getPublic());
            
            CMSSignedData dinger = signCSR(csr, cakey, (X509Certificate) cacert, 100);
            List<X509Certificate> dieCertListe=getCertsFromHolder(dinger);
            
            X509Certificate[] certificateChain = new X509Certificate[2];
            certificateChain[0] = dieCertListe.get(0);
            certificateChain[1] = (X509Certificate) cacert ;
            // certificateChain[0] = (X509Certificate) cert2;
            // certificateChain[1] = (X509Certificate) caCert;
            
            //X509Certificate[] certificateChain = (X509Certificate[]) dieCertListe.toArray();
           // keyStore.setKeyEntry(alias, privateKey, pwdArray, certificateChain);
            keyStore.setKeyEntry("kutschi", keyPair.getPrivate(), "123456".toCharArray(), certificateChain);
            
            
            
            Certificate testCert = keyStore.getCertificate("kutschi");
            
             testCert = keyStore.getCertificate("tiger");
            
            logger.info("completed");

        } catch (NoSuchAlgorithmException | InvalidKeyException | OperatorCreationException | CertificateException
                | NoSuchProviderException | SignatureException | KeyStoreException | UnrecoverableKeyException | IOException | CMSException  e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    
    private static List<X509Certificate> getCertsFromHolder(CMSSignedData cmsSignedData) throws CertificateException {
        Store<X509CertificateHolder> certStore = cmsSignedData.getCertificates();
        SignerInformationStore signerInfos = cmsSignedData.getSignerInfos();
        Collection<SignerInformation> signers = signerInfos.getSigners();
        List<X509Certificate> certificates = new ArrayList<X509Certificate>();
        for (SignerInformation signer : signers) {
          Collection<X509CertificateHolder> matches = certStore.getMatches(signer.getSID());
          for (X509CertificateHolder holder : matches) {
              
              
              JcaX509CertificateConverter certificateConverter = new JcaX509CertificateConverter();
              
              certificates.add(certificateConverter.getCertificate(holder));
              
              //certificates.add(new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder));
          }
        }
        return certificates;
    }
    
  
    /**
     * Given a Keystore containing a private key and certificate and a Reader containing a PEM-encoded
     * Certificiate Signing Request (CSR), sign the CSR with that private key and return the signed
     * certificate as a PEM-encoded PKCS#7 signedData object. The returned value can be written to a file
     * and imported into a Java KeyStore with "keytool -import -trustcacerts -alias subjectalias -file file.pem"
     *
     * @param pemcsr a Reader from which will be read a PEM-encoded CSR (begins "-----BEGIN NEW CERTIFICATE REQUEST-----")
     * @param validity the number of days to sign the Certificate for
     * @param keystore the KeyStore containing the CA signing key
     * @param alias the alias of the CA signing key in the KeyStore
     * @param password the password of the CA signing key in the KeyStore
     *
     * @return a String containing the PEM-encoded signed Certificate (begins "-----BEGIN PKCS #7 SIGNED DATA-----")
     * @throws IOException 
     * @throws OperatorCreationException 
     * @throws CertificateEncodingException 
     * @throws CMSException 
     */
    public static CMSSignedData signCSR(PKCS10CertificationRequest csr, PrivateKey cakey,X509Certificate cacert,
            int validity) throws OperatorCreationException, IOException, CertificateEncodingException, CMSException {
       
       
        AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA1withRSA");
        AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
        X500Name issuer = new X500Name(cacert.getSubjectX500Principal().getName());
        BigInteger serial = new BigInteger(32, new SecureRandom());
        Date from = new Date();
        Date to = new Date(System.currentTimeMillis() + (validity * 86400000L));

        X509v3CertificateBuilder certgen = new X509v3CertificateBuilder(issuer, serial, from, to, csr.getSubject(), csr.getSubjectPublicKeyInfo());
//        certgen.addExtension(X509Extension.basicConstraints, false, new BasicConstraints(false));
//        certgen.addExtension(X509Extension.subjectKeyIdentifier, false, new SubjectKeyIdentifier(csr.getSubjectPublicKeyInfo()));
//        certgen.addExtension(X509Extension.authorityKeyIdentifier, false, new AuthorityKeyIdentifier(new GeneralNames(new GeneralName(new X509Name(cacert.getSubjectX500Principal().getName()))), cacert.getSerialNumber()));

        ContentSigner signer = new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(PrivateKeyFactory.createKey(cakey.getEncoded()));
        X509CertificateHolder holder = certgen.build(signer);
        byte[] certencoded = holder.toASN1Structure().getEncoded();

        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        signer = new JcaContentSignerBuilder("SHA1withRSA").build(cakey);
        generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build()).build(signer, cacert));
        generator.addCertificate(new X509CertificateHolder(certencoded));
        generator.addCertificate(new X509CertificateHolder(cacert.getEncoded()));
        CMSTypedData content = new CMSProcessableByteArray(certencoded);
        CMSSignedData signeddata = generator.generate(content, true);

        
        return signeddata;
//       
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        out.write("-----BEGIN PKCS #7 SIGNED DATA-----\n".getBytes("ISO-8859-1"));
//        out.write(Base64.encode(signeddata.getEncoded()));
//        out.write("\n-----END PKCS #7 SIGNED DATA-----\n".getBytes("ISO-8859-1"));
//        out.close();
//        return new String(out.toByteArray(), "ISO-8859-1");
    }
    
    
    
    
    
    /**
     * Generate the desired keypair
     * 
     * @param alg
     * @param keySize
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static KeyPair generateKeyPair(String alg, int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(alg);
        keyPairGenerator.initialize(keySize);
        return keyPairGenerator.generateKeyPair();
    }
}
