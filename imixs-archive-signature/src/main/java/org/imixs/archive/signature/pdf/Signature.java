package org.imixs.archive.signature.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.imixs.archive.signature.pdf.util.ValidationTimeStamp;

/**
 * SignatureInterface sample implementation.
 * <p>
 * Use your favorite cryptographic library to implement PKCS #7 signature
 * creation. If you want to create the hash and the signature separately (e.g.
 * to transfer only the hash to an external application), read
 * <a href="https://stackoverflow.com/questions/41767351">this answer</a> or
 * <a href="https://stackoverflow.com/questions/56867465">this answer</a>.
 *
 * @throws IOException
 */
public class Signature implements SignatureInterface {

	private Certificate[] certificateChain;
	private PrivateKey privateKey;
	private String tsaUrl;

	public Signature(Certificate[] certificateChain, PrivateKey privateKey)
			throws UnrecoverableKeyException, CertificateNotYetValidException, CertificateExpiredException,
			KeyStoreException, NoSuchAlgorithmException, IOException {
		this(certificateChain, privateKey, null);
	}

	public Signature(Certificate[] certificateChain, PrivateKey privateKey, String _tsaURL)
			throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException, IOException,
			CertificateNotYetValidException, CertificateExpiredException {
		super();
		this.certificateChain = certificateChain;
		this.tsaUrl = _tsaURL;
		this.privateKey = privateKey;
		if (certificateChain != null && certificateChain.length > 0) {
			Certificate certificate = this.certificateChain[0];
			if (certificate instanceof X509Certificate) {
				((X509Certificate) certificate).checkValidity();
			}
		}
	}

	/**
	 * This method will be called from inside of the pdfbox and create the PKCS #7
	 * signature. The given InputStream contains the bytes that are given by the
	 * byte range.
	 * <p>
	 * This method is for internal use only.
	 *
	 * @throws IOException
	 */
	@Override
	public byte[] sign(InputStream content) throws IOException {
		// cannot be done private (interface)
		try {
			CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
			X509Certificate cert = (X509Certificate) certificateChain[0];
			ContentSigner sha1Signer = new JcaContentSignerBuilder("SHA256WithRSA").build(privateKey);
			gen.addSignerInfoGenerator(
					new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
							.build(sha1Signer, cert));
			gen.addCertificates(new JcaCertStore(Arrays.asList(certificateChain)));
			CMSProcessableInputStream msg = new CMSProcessableInputStream(content);
			CMSSignedData signedData = gen.generate(msg, false);

			// If we have an optional tsa server than create a validateionTimeStamp....
			if (tsaUrl != null && tsaUrl.length() > 0) {
				ValidationTimeStamp validation = new ValidationTimeStamp(tsaUrl);
				signedData = validation.addSignedTimeStamp(signedData);
			}

			return signedData.getEncoded();
		} catch (OperatorCreationException | CertificateEncodingException | CMSException | NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

}
