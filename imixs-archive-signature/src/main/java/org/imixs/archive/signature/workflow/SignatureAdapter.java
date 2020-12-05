package org.imixs.archive.signature.workflow;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.bouncycastle.operator.OperatorCreationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.core.SnapshotService;
import org.imixs.archive.signature.ca.CAService;
import org.imixs.archive.signature.ca.X509ProfileHandler;
import org.imixs.archive.signature.pdf.SigningService;
import org.imixs.archive.signature.pdf.cert.CertificateVerificationException;
import org.imixs.archive.signature.pdf.cert.SigningException;
import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.SignalAdapter;
import org.imixs.workflow.engine.WorkflowService;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.exceptions.ProcessingErrorException;

/**
 * The SignatureAdapter signs a PDF document.
 * <p>
 * The adapter creates a digital signature with the certificate associated with
 * the current user name. If no certificate exits, the adapter creates a new
 * certificate (autocreate=true) or signs the document with the root certificate
 * (rootsignature=true)
 * <p>
 * 
 * <pre>
 * {@code
        <signature name="autocreate">true</signature>
        <signature name="rootsignature">true</signature>
        <signature name="filepattern">order.pdf</signature>
   }
 * </pre>
 * 
 * @version 1.0
 * @author rsoika
 */
public class SignatureAdapter implements SignalAdapter {

    public static final String PDF_REGEX = "^.+\\.([pP][dD][fF])$";

    @Inject
    @ConfigProperty(name = SigningService.ENV_SIGNATURE_ROOTCERT_ALIAS)
    Optional<String> rootCertAlias;

    @Inject
    @ConfigProperty(name = SigningService.ENV_SIGNATURE_ROOTCERT_PASSWORD)
    Optional<String> rootCertPassword;

    @Inject
    SigningService signatureService;

    @Inject
    CAService caService;

    @Inject
    SnapshotService snapshotService;

    @Inject
    WorkflowService workflowService;

    @Inject
    X509ProfileHandler x509ProfileHandler;

    private static Logger logger = Logger.getLogger(SignatureAdapter.class.getName());

    /**
     * This method posts a text from an attachment to the Imixs-ML Analyse service
     * endpoint
     */
    @Override
    public ItemCollection execute(ItemCollection document, ItemCollection event) throws AdapterException {
        boolean autocreate = true;
        boolean rootsignature = false;
        String file_pattern = PDF_REGEX;

        try {
            // do we have file attachments?
            List<String> fileNames = document.getFileNames();
            if (fileNames.size() > 0) {

                // read signature options
                ItemCollection evalItemCollection = workflowService.evalWorkflowResult(event, "signature", document,
                        false);
                if (evalItemCollection != null) {
                    if (evalItemCollection.hasItem("autocreate")) {
                        autocreate = evalItemCollection.getItemValueBoolean("autocreate");
                    }
                    if (evalItemCollection.hasItem("rootsignature")) {
                        rootsignature = evalItemCollection.getItemValueBoolean("rootsignature");
                    }
                    if (evalItemCollection.hasItem("filepattern")) {
                        file_pattern = evalItemCollection.getItemValueString("filepattern");
                    }
                }

                // do we have files matching the file pattern?
                Pattern filePatternMatcher = Pattern.compile(file_pattern);
                for (String fileName : fileNames) {
                    // did the file math our file pattern?
                    if (filePatternMatcher.matcher(fileName).find()) {
                        // yes! start signing....

                        // we assume an empty password for certificate
                        String certPassword = "";
                        String certAlias = null;

                        // Test if the a signature with the root certificate is requested
                        if (rootsignature && rootCertAlias.isPresent()) {
                            certAlias = rootCertAlias.get();
                            // set SIGNATURE_ROOTCERT_PASSWORD
                            if (rootCertPassword.isPresent()) {
                                certPassword = rootCertPassword.get();
                            }

                            // test existence of default certificate
                            if (!caService.existsCertificate(certAlias)) {
                                throw new ProcessingErrorException(this.getClass().getSimpleName(), "SIGNING_ERROR",
                                        "Root certificate '" + certAlias + "' does not exist!");
                            }
                            logger.info("......signing " + fileName + " with root certificate '" + certAlias + "'...");
                        } else {
                            // signature with user certificate....
                            // compute alias validate existence of certificate
                            certAlias = workflowService.getUserName();
                            logger.info("......signing " + fileName + " by '" + certAlias + "'...");

                            // test if a certificate exits....
                            if (!caService.existsCertificate(certAlias)) {
                                if (autocreate) {
                                    // lookup the x509 data form the x509ProfileHandler
                                    ItemCollection x509Profile = x509ProfileHandler.findX509Profile(certAlias);
                                    // create new certificate....
                                    caService.createCertificate(certAlias, x509Profile);
                                } else {
                                    throw new CertificateVerificationException(
                                            "certificate for alias '" + certAlias + "' not found.");
                                }
                                // test existence of default certificate
                                if (!caService.existsCertificate(certAlias)) {
                                    throw new ProcessingErrorException(this.getClass().getSimpleName(), "SIGNING_ERROR",
                                            "No certificate exists for user '" + certAlias + "'");
                                }
                            }
                        }

                        // read the file data...
                        FileData fileData = document.getFileData(fileName);
                        byte[] sourceContent = fileData.getContent();
                        if (sourceContent.length == 0) {
                            // load from snapshot
                            ItemCollection snapshot = snapshotService.findSnapshot(document);
                            fileData = snapshot.getFileData(fileName);
                            sourceContent = fileData.getContent();
                        }

                        byte[] signedContent=null;
                        // in case of a rootsignature we do not generate a signature visual!
                        if (rootsignature) {
                             signedContent = signatureService.signPDF(sourceContent, certAlias, certPassword,false);
                        } else {
                            Rectangle2D humanRect = new Rectangle2D.Float(50, 660, 170, 50);

                            // create signature withvisual
                            //File fileSignatureImage = new File("/opt/imixs-keystore/" + certAlias + ".jpg");
                            signedContent = signatureService.signPDF(sourceContent, certAlias, certPassword,false,humanRect,"Signature1",
                                    null);
                        }

                        // ad the signed pdf file to the workitem
                        FileData signedFileData = new FileData(fileName, signedContent,"application/pdf", null);
                                
                        document.addFileData(signedFileData);
                        
                        // force overwriting content...
                        document.appendItemValue(SnapshotService.ITEM_SNAPSHOT_OVERWRITEFILECONTENT, fileName);
                        
                        logger.info("......signing " + fileName + " completed!");
                    }
                }
            }
        } catch (IOException | SigningException | CertificateVerificationException | UnrecoverableKeyException
                | InvalidKeyException | KeyStoreException | NoSuchAlgorithmException | NoSuchProviderException
                | OperatorCreationException | CertificateException | SignatureException | PluginException e) {
            throw new ProcessingErrorException(this.getClass().getSimpleName(), "SIGNING_ERROR", e.getMessage(), e);
        }

        return document;
    }

}