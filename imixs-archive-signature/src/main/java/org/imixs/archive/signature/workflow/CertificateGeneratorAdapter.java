package org.imixs.archive.signature.workflow;

import java.util.logging.Logger;

import javax.inject.Inject;

import org.imixs.archive.signature.KeystoreService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.SignalAdapter;
import org.imixs.workflow.exceptions.AdapterException;

/**
 * The CertificateGeneratorAdapter generate a new certificate
 * 
 * @version 1.0
 * @author rsoika
 */
public class CertificateGeneratorAdapter implements SignalAdapter {

	private static Logger logger = Logger.getLogger(CertificateGeneratorAdapter.class.getName());

	@Inject
	KeystoreService keystoreService;

	
	/**
	 * This method posts a text from an attachment to the Imixs-ML Analyse service
	 * endpoint
	 */
	@Override
	public ItemCollection execute(ItemCollection document, ItemCollection event) throws AdapterException {

		logger.info("...adding new certificate for user sepp");
		keystoreService.addEntry("sepp");
		
		

		return document;
	}

}