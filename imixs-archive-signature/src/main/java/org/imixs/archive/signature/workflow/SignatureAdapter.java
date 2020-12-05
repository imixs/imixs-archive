package org.imixs.archive.signature.workflow;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
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
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.engine.WorkflowService;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.exceptions.ProcessingErrorException;
import org.imixs.workflow.exceptions.QueryException;

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
        <signature name="position-x">50</signature>
        <signature name="position-y">650</signature>
        <signature name="dimension-w">170</signature>
        <signature name="dimension-h">50</signature>
        
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
    DocumentService documentService;

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
        float positionx = 30;
        float positiony = 700;
        float dimensionw = 170;
        float dimensionh = 100;

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

                    if (evalItemCollection.hasItem("position-x")) {
                        positionx = evalItemCollection.getItemValueFloat("position-x");
                    }
                    if (evalItemCollection.hasItem("position-y")) {
                        positiony = evalItemCollection.getItemValueFloat("position-y");
                    }
                    if (evalItemCollection.hasItem("dimension-w")) {
                        dimensionw = evalItemCollection.getItemValueFloat("dimension-w");
                    }
                    if (evalItemCollection.hasItem("dimension-h")) {
                        dimensionh = evalItemCollection.getItemValueFloat("dimension-h");
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

                        byte[] signedContent = null;
                        // in case of a rootsignature we do not generate a signature visual!
                        if (rootsignature) {
                            signedContent = signatureService.signPDF(sourceContent, certAlias, certPassword, false);
                        } else {
                            byte[] signatureImage=null;
                            // we reisize the signature image to a maximum height of the half of the signature rect
                            
                            FileData fileDataSignature=loadSignatureImageFromProfile(certAlias);
                            if (fileDataSignature!=null) {
                                //  resize the signature image to the half of the signature rect
                                
                             //   fileDataSignature=resizeSignature(fileDataSignature,(int) (dimensionh/2));
                                signatureImage=fileDataSignature.getContent();
                            }
                            
                          

                            // if we have already a signature we move the x position....
                            int signatureCount = document.getItemValueInteger("signature.count");
                            if (signatureCount > 0) {
                                positionx = positionx + (signatureCount * dimensionw + 10);
                            }
                            Rectangle2D humanRect = new Rectangle2D.Float(positionx, positiony, dimensionw, dimensionh);
                            // create signature withvisual
                            signedContent = signatureService.signPDF(sourceContent, certAlias, certPassword, false,
                                    humanRect, "Signature" + signatureCount, signatureImage,
                                    document.getItemValueString(WorkflowKernel.WORKFLOWSTATUS));

                            document.setItemValue("signature.count", signatureCount + 1);
                        }

                        // ad the signed pdf file to the workitem
                        FileData signedFileData = new FileData(fileName, signedContent, "application/pdf", null);

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

    /**
     * This helper method tries to load a signature image form the current user
     * profile the expected file name is 'signature.jpg'
     * 
     * @param certAlias
     * @return
     */
    private FileData loadSignatureImageFromProfile(String certAlias) {

        // test if we have a signatrue image in the user profile...
        List<ItemCollection> userProfileList;
        try {
            userProfileList = documentService.find("type:profile AND txtname:" + certAlias, 1, 0);

            if (userProfileList.size() > 0) {
                ItemCollection profile = userProfileList.get(0);

                FileData fileData = snapshotService.getWorkItemFile(profile.getUniqueID(), "signature.jpg");
                if (fileData != null && fileData.getContent() != null && fileData.getContent().length > 0) {
                    
                   
                    
                    
                    return fileData;
                }
            }

        } catch (QueryException e) {
            logger.warning("Failed to load signature image from profile : " + e.getMessage());
        }

        return null;
    }

    
    /**
     * Helper method to resize the signature image to a given maximum height
     * 
     * @param fileData
     * @param maxHeight
     * @return resized image fileData
     */
    private FileData resizeSignature(FileData fileData,int maxHeight) {
        
        String inFormat = "jpg";
        try {

            // compute image format...
            if (fileData.getName().indexOf('.') > -1) {
                inFormat = fileData.getName().substring(fileData.getName().lastIndexOf('.') + 1);
                inFormat = inFormat.toLowerCase();
            }

            Iterator<ImageReader> inReaders = ImageIO.getImageReadersByFormatName(inFormat);
            ImageReader imageReader = (ImageReader) inReaders.next();
            ImageInputStream iis;
            iis = ImageIO.createImageInputStream(new ByteArrayInputStream(fileData.getContent()));

            imageReader.setInput(iis);
            BufferedImage originalImage = imageReader.read(0);
            
            
            
         // Now we test if max height is extended?
            if (originalImage!=null && originalImage.getHeight()> maxHeight) {
                int type = originalImage.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : originalImage.getType();
                BufferedImage resizeImageHintJpg = resizeImageWithHint(originalImage, type, maxHeight);

                if (resizeImageHintJpg != null) {

                    // write image back...
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(resizeImageHintJpg,inFormat, baos);
                    baos.flush();
                    byte[] imageInByte = baos.toByteArray();
                    baos.close();

                    // return result
                    FileData resultFileData=new FileData(fileData.getName(), imageInByte, fileData.getContentType(), fileData.getAttributes());
                    return resultFileData;
                }
            }

        } catch (IOException e) {
         logger.warning("Unable to resize signature image: " + e.getMessage());
            e.printStackTrace();
            
        }

        // no resize....
        return fileData;
    }
    
    /*
     * resize an image with a height hint...
     */
    private BufferedImage resizeImageWithHint(BufferedImage originalImage, int type, int imageMaxHeight) {

        // compute hight...
        float width = originalImage.getWidth();
        float height = originalImage.getHeight();
        float factor = (float) height / (float) imageMaxHeight;
        int newWidth = (int) (width / factor);

        BufferedImage resizedImage = new BufferedImage(newWidth, imageMaxHeight, type);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(originalImage, 0, 0, newWidth, imageMaxHeight, null);
        g.dispose();
        g.setComposite(AlphaComposite.Src);

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        return resizedImage;
    }

}