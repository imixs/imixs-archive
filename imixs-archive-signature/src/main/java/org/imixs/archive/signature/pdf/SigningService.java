/*******************************************************************************
 *  Imixs Workflow Technology
 *  Copyright (C) 2001, 2008 Imixs Software Solutions GmbH,  
 *  http://www.imixs.com
 *  
 *  This program is free software; you can redistribute it and/or 
 *  modify it under the terms of the GNU General Public License 
 *  as published by the Free Software Foundation; either version 2 
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful, 
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 *  General Public License for more details.
 *  
 *  You can receive a copy of the GNU General Public
 *  License at http://www.gnu.org/licenses/gpl.html
 *  
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika
 *******************************************************************************/
package org.imixs.archive.signature.pdf;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.util.Matrix;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.signature.KeystoreService;
import org.imixs.archive.signature.pdf.util.SigUtils;

/**
 * The SignatureService provides method to sign a PDF document.
 * 
 * The service expects the following environment variables:
 * <ul>
 * <li>SIGNATURE_KEYSTORE_PATH - path from which the keystore is loaded</li>
 * <li>SIGNATURE_KEYSTORE_PASSWORD - the password used to check the integrity of
 * the keystore, the password used to unlock the keystore</li>
 * <li>SIGNATURE_KEYSTORE_TYPE - keystore file extension (defautl =.jks)
 * </ul>
 * 
 * <p>
 * The KEY_STORE_TYPE is a keystore file extension, by default it’s .jks but
 * also other types are supported like .pkcs12 or whatever.
 * <p>
 * See also here: https://jvmfy.com/2018/11/17/how-to-digitally-sign-pdf-files/
 * https://github.com/apache/pdfbox/blob/trunk/examples/src/main/java/org/apache/pdfbox/examples/signature/CreateVisibleSignature2.java
 * 
 * https://ordina-jworks.github.io/security/2019/08/14/Using-Lets-Encrypt-Certificates-In-Java.html
 * 
 * @author rsoika
 * 
 */
@Stateless
@LocalBean
public class SigningService {

	public final static String ENV_SIGNATURE_TSA_URL = "signature.tsa.url";
	public final static String ENV_SIGNATURE_KEYSTORE_DEFAULT_ALIAS = "signature.keystore.default.alias";


	@Inject
	KeystoreService keystoreService;
	
	@Inject
	@ConfigProperty(name = ENV_SIGNATURE_KEYSTORE_DEFAULT_ALIAS)
	Optional<String> defaultAlias;

	
	@Inject
	@ConfigProperty(name = ENV_SIGNATURE_TSA_URL)
	Optional<String> tsaURL;

	
	
	private static Logger logger = Logger.getLogger(SigningService.class.getName());

	/**
	 * Method Opens the keystore with the given password and creates a new signed
	 * PDF file based on the given PDF File and a signature image.
	 * <p>
	 * generate pkcs12-keystore-file with
	 * <p>
	 * {@code
	  keytool -storepass 123456 -storetype PKCS12 -keystore file.p12 -genkey -alias client -keyalg RSA
	  }
	 *
	 * @param documentFile   - File to be signed
	 * @param alias          - the alias used to sign the document. The alias should
	 *                       be listed in the keystore.
	 * @param signatureImage - image of visible signature
	 * 
	 * @throws java.security.KeyStoreException
	 * @throws java.security.cert.CertificateException
	 * @throws java.io.IOException
	 * @throws java.security.NoSuchAlgorithmException
	 * 
	 */
	public void signPDF(File documentFile, String alias, File signatureImage)
			throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException {

		logger.info("......signPDF '" + documentFile.getName() + "' ...");

		
		File signedDocumentFile;
		String name = documentFile.getName();
		String substring = name.substring(0, name.lastIndexOf('.'));
		signedDocumentFile = new File(documentFile.getParent(), substring + "_signed.pdf");

		// Set the signature rectangle
		// Although PDF coordinates start from the bottom, humans start from the top.
		// So a human would want to position a signature (x,y) units from the
		// top left of the displayed page, and the field has a horizontal width and a
		// vertical height
		// regardless of page rotation.
		Rectangle2D humanRect = new Rectangle2D.Float(50, 660, 170, 50);

		alias="sepp";
		//alias="tiger";
		
		createSignedPDF(documentFile, signedDocumentFile, alias, humanRect, "Signature1", signatureImage, false);

	}

	/**
	 * Sign pdf file and create new file that ends with "_signed.pdf".
	 *
	 * @param inputFile          The source pdf document file.
	 * @param signedFile         The file to be signed.
	 * @param alias              Certificate alias name to be used for signing
	 * @param humanRect          rectangle from a human viewpoint (coordinates start
	 *                           at top left)
	 * @param tsaUrl             optional TSA url
	 * @param signatureFieldName optional name of an existing (unsigned) signature
	 *                           field
	 * @param externalSigning    optional boolean flag to trigger an external
	 *                           signing process
	 * @throws IOException
	 */
	private void createSignedPDF(File inputFile, File signedFile, String alias, Rectangle2D humanRect, String signatureFieldName,
			File imageFile,  boolean externalSigning) throws IOException {

		SignatureOptions signatureOptions = null;

		if (inputFile == null || !inputFile.exists()) {
			throw new IOException("Document for signing does not exist");
		}

		// = Loader.loadPDF(inputFile)) {
		try (FileOutputStream fos = new FileOutputStream(signedFile); PDDocument doc = PDDocument.load(inputFile)) {
			int accessPermissions = SigUtils.getMDPPermission(doc);
			if (accessPermissions == 1) {
				throw new IllegalStateException(
						"No changes to the document are permitted due to DocMDP transform parameters dictionary");
			}
			// Note that PDFBox has a bug that visual signing on certified files with
			// permission 2
			// doesn't work properly, see PDFBOX-3699. As long as this issue is open, you
			// may want to
			// be careful with such files.

			PDSignature pdSignature = null;
			PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
			PDRectangle rect = null;

			// sign a PDF with an existing empty signature, as created by the
			// CreateEmptySignatureForm example.
			if (acroForm != null) {
				pdSignature = findExistingSignature(acroForm, signatureFieldName);
				if (pdSignature != null) {
					rect = acroForm.getField(signatureFieldName).getWidgets().get(0).getRectangle();
				}
			}

			if (pdSignature == null) {
				// create signature dictionary
				pdSignature = new PDSignature();
			}

			if (rect == null) {
				rect = createSignatureRectangle(doc, humanRect);
			}

			// Optional: certify
			// can be done only if version is at least 1.5 and if not already set
			// doing this on a PDF/A-1b file fails validation by Adobe preflight
			// (PDFBOX-3821)
			// PDF/A-1b requires PDF version 1.4 max, so don't increase the version on such
			// files.
			if (doc.getVersion() >= 1.5f && accessPermissions == 0) {
				SigUtils.setMDPPermission(doc, pdSignature, 2);
			}

			if (acroForm != null && acroForm.getNeedAppearances()) {
				// PDFBOX-3738 NeedAppearances true results in visible signature becoming
				// invisible
				// with Adobe Reader
				if (acroForm.getFields().isEmpty()) {
					// we can safely delete it if there are no fields
					acroForm.getCOSObject().removeItem(COSName.NEED_APPEARANCES);
					// note that if you've set MDP permissions, the removal of this item
					// may result in Adobe Reader claiming that the document has been changed.
					// and/or that field content won't be displayed properly.
					// ==> decide what you prefer and adjust your code accordingly.
				} else {
					System.out.println("/NeedAppearances is set, signature may be ignored by Adobe Reader");
				}
			}

			// default filter
			pdSignature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);

			// subfilter for basic and PAdES Part 2 signatures
			pdSignature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);

			pdSignature.setName("Name");
			pdSignature.setLocation("Location");
			pdSignature.setReason("Freigabe");

			// the signing date, needed for valid signature
			pdSignature.setSignDate(Calendar.getInstance());

			// do not set SignatureInterface instance, if external signing used
			// Signature sig = new Signature(certificateChain, privateKey);

			Signature signature = null;
			Certificate[] certificateChain = null;
			
			String certAlias=alias;
			certificateChain=keystoreService.loadCertificate(certAlias);
			if (certificateChain == null || certificateChain.length == 0) {
				// we did not found the alias - so we try the default alias....
				logger.warning("...certificate alias '" + certAlias
						+ "' not found in keystore - fallback to the default certificate...");

				if (!defaultAlias.isPresent()) {
					throw new IOException("missing default certificate alias (SIGNATURE_KEYSTORE_DEFAULT_ALIAS)!");
				}
				certAlias = defaultAlias.get();
				certificateChain = keystoreService.loadCertificate(certAlias);
				if (certificateChain == null || certificateChain.length == 0) {
					throw new IOException("no valid certificate found in keystore!");
				}
			}
			
			
			// create the Signature for signing.....
			try {
				// test if a TSA URL was injected....
				String sTsaUrl = null;
				if (tsaURL.isPresent() && !tsaURL.get().isEmpty()) {
					sTsaUrl = tsaURL.get();
				}
				
				PrivateKey testprivateKey = keystoreService.loadPrivateKey("tiger");
				
				PrivateKey privateKey = keystoreService.loadPrivateKey(certAlias);
				signature = new Signature(certificateChain, privateKey, sTsaUrl);
			} catch (UnrecoverableKeyException | CertificateNotYetValidException | CertificateExpiredException
					| KeyStoreException | NoSuchAlgorithmException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// register signature dictionary and sign interface
			signatureOptions = new SignatureOptions();
			
			// create visual signature if imageFile exists....
			if (imageFile.exists()) {
				signatureOptions.setVisualSignature(
						createVisualSignatureTemplate(doc, 0, rect, pdSignature, imageFile, certificateChain));
			} else {
				logger.warning("Signature Image '" + imageFile.getPath() + "' does not exist. No Image will be added!");
			}
			signatureOptions.setPage(0);
			doc.addSignature(pdSignature, signature, signatureOptions);

			if (externalSigning) {
				ExternalSigningSupport externalSigningSupport = doc.saveIncrementalForExternalSigning(fos);
				// invoke external signature service
				byte[] cmsSignature = signature.sign(externalSigningSupport.getContent());

				// set signature bytes received from the service and save the file
				externalSigningSupport.setSignature(cmsSignature);

			} else {
				// write incremental (only for signing purpose)
				doc.saveIncremental(fos);
			}

		}

		// Do not close signatureOptions before saving, because some COSStream objects
		// within
		// are transferred to the signed document.
		// Do not allow signatureOptions get out of scope before saving, because then
		// the COSDocument
		// in signature options might by closed by gc, which would close COSStream
		// objects prematurely.
		// See https://issues.apache.org/jira/browse/PDFBOX-3743
		IOUtils.closeQuietly(signatureOptions);
	}

	private PDRectangle createSignatureRectangle(PDDocument doc, Rectangle2D humanRect) {
		float x = (float) humanRect.getX();
		float y = (float) humanRect.getY();
		float width = (float) humanRect.getWidth();
		float height = (float) humanRect.getHeight();
		PDPage page = doc.getPage(0);
		PDRectangle pageRect = page.getCropBox();
		PDRectangle rect = new PDRectangle();
		// signing should be at the same position regardless of page rotation.
		switch (page.getRotation()) {
		case 90:
			rect.setLowerLeftY(x);
			rect.setUpperRightY(x + width);
			rect.setLowerLeftX(y);
			rect.setUpperRightX(y + height);
			break;
		case 180:
			rect.setUpperRightX(pageRect.getWidth() - x);
			rect.setLowerLeftX(pageRect.getWidth() - x - width);
			rect.setLowerLeftY(y);
			rect.setUpperRightY(y + height);
			break;
		case 270:
			rect.setLowerLeftY(pageRect.getHeight() - x - width);
			rect.setUpperRightY(pageRect.getHeight() - x);
			rect.setLowerLeftX(pageRect.getWidth() - y - height);
			rect.setUpperRightX(pageRect.getWidth() - y);
			break;
		case 0:
		default:
			rect.setLowerLeftX(x);
			rect.setUpperRightX(x + width);
			rect.setLowerLeftY(pageRect.getHeight() - y - height);
			rect.setUpperRightY(pageRect.getHeight() - y);
			break;
		}
		return rect;
	}

	// create a template PDF document with empty signature and return it as a
	// stream.
	private InputStream createVisualSignatureTemplate(PDDocument srcDoc, int pageNum, PDRectangle rect,
			PDSignature signature, File imageFile, Certificate[] certificateChain) throws IOException {
		try (PDDocument doc = new PDDocument()) {
			PDPage page = new PDPage(srcDoc.getPage(pageNum).getMediaBox());
			doc.addPage(page);
			PDAcroForm acroForm = new PDAcroForm(doc);
			doc.getDocumentCatalog().setAcroForm(acroForm);
			PDSignatureField signatureField = new PDSignatureField(acroForm);
			PDAnnotationWidget widget = signatureField.getWidgets().get(0);
			List<PDField> acroFormFields = acroForm.getFields();
			acroForm.setSignaturesExist(true);
			acroForm.setAppendOnly(true);
			acroForm.getCOSObject().setDirect(true);
			acroFormFields.add(signatureField);

			widget.setRectangle(rect);

			// from PDVisualSigBuilder.createHolderForm()
			PDStream stream = new PDStream(doc);
			PDFormXObject form = new PDFormXObject(stream);
			PDResources res = new PDResources();
			form.setResources(res);
			form.setFormType(1);
			PDRectangle bbox = new PDRectangle(rect.getWidth(), rect.getHeight());
			float height = bbox.getHeight();
			Matrix initialScale = null;
			switch (srcDoc.getPage(pageNum).getRotation()) {
			case 90:
				form.setMatrix(AffineTransform.getQuadrantRotateInstance(1));
				initialScale = Matrix.getScaleInstance(bbox.getWidth() / bbox.getHeight(),
						bbox.getHeight() / bbox.getWidth());
				height = bbox.getWidth();
				break;
			case 180:
				form.setMatrix(AffineTransform.getQuadrantRotateInstance(2));
				break;
			case 270:
				form.setMatrix(AffineTransform.getQuadrantRotateInstance(3));
				initialScale = Matrix.getScaleInstance(bbox.getWidth() / bbox.getHeight(),
						bbox.getHeight() / bbox.getWidth());
				height = bbox.getWidth();
				break;
			case 0:
			default:
				break;
			}
			form.setBBox(bbox);
			PDFont font = PDType1Font.HELVETICA_BOLD;

			// from PDVisualSigBuilder.createAppearanceDictionary()
			PDAppearanceDictionary appearance = new PDAppearanceDictionary();
			appearance.getCOSObject().setDirect(true);
			PDAppearanceStream appearanceStream = new PDAppearanceStream(form.getCOSObject());
			appearance.setNormalAppearance(appearanceStream);
			widget.setAppearance(appearance);

			try (PDPageContentStream cs = new PDPageContentStream(doc, appearanceStream)) {
				// for 90Â° and 270Â° scale ratio of width / height
				// not really sure about this
				// why does scale have no effect when done in the form matrix???
				if (initialScale != null) {
					cs.transform(initialScale);
				}

				// show background (just for debugging, to see the rect size + position)
				cs.setNonStrokingColor(Color.yellow);
				cs.addRect(-5000, -5000, 10000, 10000);
				cs.fill();

				if (imageFile != null) {
					// show background image
					// save and restore graphics if the image is too large and needs to be scaled
					cs.saveGraphicsState();
					cs.transform(Matrix.getScaleInstance(0.25f, 0.25f));
					PDImageXObject img = PDImageXObject.createFromFileByExtension(imageFile, doc);
					cs.drawImage(img, 0, 0);
					cs.restoreGraphicsState();
				}

				// show text
				float fontSize = 10;
				float leading = fontSize * 1.5f;
				cs.beginText();
				cs.setFont(font, fontSize);
				cs.setNonStrokingColor(Color.black);
				cs.newLineAtOffset(fontSize, height - leading);
				cs.setLeading(leading);

				X509Certificate cert = (X509Certificate) certificateChain[0];

				// https://stackoverflow.com/questions/2914521/
				X500Name x500Name = new X500Name(cert.getSubjectX500Principal().getName());
				RDN cn = x500Name.getRDNs(BCStyle.CN)[0];
				String name = IETFUtils.valueToString(cn.getFirst().getValue());

				// See https://stackoverflow.com/questions/12575990
				// for better date formatting
				String date = signature.getSignDate().getTime().toString();
				String reason = signature.getReason();

				cs.showText("Signer: " + name);
				cs.newLine();
				cs.showText(date);
				cs.newLine();
				cs.showText("Reason: " + reason);

				cs.endText();
			}

			// no need to set annotations and /P entry

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			doc.save(baos);
			return new ByteArrayInputStream(baos.toByteArray());
		}
	}

	// Find an existing signature (assumed to be empty). You will usually not need
	// this.
	private PDSignature findExistingSignature(PDAcroForm acroForm, String sigFieldName) {
		PDSignature signature = null;
		PDSignatureField signatureField;
		if (acroForm != null) {
			signatureField = (PDSignatureField) acroForm.getField(sigFieldName);
			if (signatureField != null) {
				// retrieve signature dictionary
				signature = signatureField.getSignature();
				if (signature == null) {
					signature = new PDSignature();
					// after solving PDFBOX-3524
					// signatureField.setValue(signature)
					// until then:
					signatureField.getCOSObject().setItem(COSName.V, signature);
				} else {
					throw new IllegalStateException("The signature field " + sigFieldName + " is already signed.");
				}
			}
		}
		return signature;
	}

}
