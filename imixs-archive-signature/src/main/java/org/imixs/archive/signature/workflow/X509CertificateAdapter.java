package org.imixs.archive.signature.workflow;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.bouncycastle.operator.OperatorCreationException;
import org.imixs.archive.signature.ca.CAService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.SignalAdapter;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.ProcessingErrorException;

/**
 * The X509CertificateAdapter generate a new certificate based on the x509
 * attributes stored in the current document
 * 
 * @version 1.0
 * @author rsoika
 */
public class X509CertificateAdapter implements SignalAdapter {

    private static Logger logger = Logger.getLogger(X509CertificateAdapter.class.getName());

    @Inject
    CAService caService;

    /**
     * This method posts a text from an attachment to the Imixs-ML Analyse service
     * endpoint
     */
    @Override
    public ItemCollection execute(ItemCollection document, ItemCollection event) throws AdapterException {
        String certAlias = document.getItemValueString("txtname");
        logger.finest(".......adding new certificate for userid '" + certAlias + "'");

        try {
            caService.createCertificate(certAlias, document);
        } catch (IOException | UnrecoverableKeyException | InvalidKeyException | KeyStoreException
                | NoSuchAlgorithmException | NoSuchProviderException | OperatorCreationException | CertificateException
                | SignatureException e) {
            throw new ProcessingErrorException(this.getClass().getSimpleName(), "CERTIFICATE_ERROR", e.getMessage(), e);
        }

        return document;
    }

}