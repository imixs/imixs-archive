package org.imixs.archive.signature.draft;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

/**
 * 
 * 
 * http://senthadev.com/generating-csr-using-java-and-bouncycastle-api.html
 * 
 * 
 * 
 * @author rsoika
 *
 */

public class CSRGenerator {

	/**
	 * This method creates a X500Principal for a certificate.
	 * 
	 * @return
	 */
	public X500Principal createCNForCertificate() {
		X500Principal subject = new X500Principal(
				"C=NO, ST=Trondheim, L=Trondheim, O=Senthadev, OU=Innovation, CN=www.senthadev.com, EMAILADDRESS=senthadev@gmail.com");

		return subject;
	}

	/**
	 * Generate the desired CSR for signing
	 * <p>
	 * Create a ContentSigner object using helper object using a privateKey and
	 * "SHA1withRSA" algorithm.
	 * 
	 * @param sigAlg
	 * @param keyPair
	 * @return
	 * @throws OperatorCreationException
	 */
	public static PKCS10CertificationRequest generateCSR(X500Principal subject, PrivateKey privateKey, PublicKey publicKey)
			throws OperatorCreationException {

		ContentSigner signGen = new JcaContentSignerBuilder("SHA1withRSA").build(privateKey);

		// create the CSR.

		PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(subject, publicKey);
		PKCS10CertificationRequest csr = builder.build(signGen);

		return csr;
	}
	
	
	

}