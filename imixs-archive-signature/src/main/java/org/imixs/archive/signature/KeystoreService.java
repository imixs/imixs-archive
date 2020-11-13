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
package org.imixs.archive.signature;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.logging.Logger;

import javax.ejb.Singleton;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.archive.signature.draft.CertGenerator;

/**
 * The KeystoreService provides methods to open a java keystore and find
 * certificates by alias
 * <p>
 * The service is implemented as a singleton to avoid concurrent access from
 * different clients.
 * 
 * @author rsoika
 * @version 1.0
 */
@Singleton
public class KeystoreService {

	public final static String ENV_SIGNATURE_KEYSTORE_PATH = "signature.keystore.path";
	public final static String ENV_SIGNATURE_KEYSTORE_PASSWORD = "signature.keystore.password";
	public final static String ENV_SIGNATURE_KEYSTORE_TYPE = "signature.keystore.type";
	@Inject
	@ConfigProperty(name = ENV_SIGNATURE_KEYSTORE_PATH, defaultValue = "/")
	String keyStorePath;

	@Inject
	@ConfigProperty(name = ENV_SIGNATURE_KEYSTORE_PASSWORD, defaultValue = "/")
	String keyStorePassword;

	
	@Inject
	@ConfigProperty(name = ENV_SIGNATURE_KEYSTORE_TYPE, defaultValue = ".jks")
	String keyStoreType;

	private static Logger logger = Logger.getLogger(KeystoreService.class.getName());

	
	
	
	
	public KeystoreService(String keyStorePath, String keyStorePassword, String keyStoreType) {
		super();
		this.keyStorePath = keyStorePath;
		this.keyStorePassword = keyStorePassword;
		this.keyStoreType = keyStoreType;
	}

	public KeystoreService() {
        super();
        //  Auto-generated constructor stub
    }

    /**
	 * Open a java keyStore based on the environment variables
	 * SIGNATURE_KEYSTORE_PATH , SIGNATURE_KEYSTORE_TYPE and
	 * SIGNATURE_KEYSTORE_PASSWORD
	 * 
	 * @throws KeyStoreException
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws CertificateException
	 * @throws NoSuchAlgorithmException
	 * 
	 */
	public KeyStore openKeyStore() throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
			FileNotFoundException, IOException {

		logger.info("......open keystore");

		KeyStore keyStore = KeyStore.getInstance(keyStoreType);
		File key = new File(keyStorePath);
		keyStore.load(new FileInputStream(key), keyStorePassword.toCharArray());
		
		return keyStore;
	}

	/**
	 * Loads a certificate chain by a given alias.
	 * <p>
	 * If a certificate for the given alias name does not exist in the keystore than
	 * the default alias (SIGNATURE_KEYSTORE_DEFAULT_ALIAS) will be loaded.
	 * <p>
	 * The method returns null if no certificate was found!
	 * 
	 * @throws CertificateException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyStoreException
	 */
	public Certificate[] loadCertificate(String alias) {

		Certificate[] certificateChain = null;
		try {
			KeyStore keyStore;
			keyStore = openKeyStore();
			// Now we try to load the certificateChat for the given alias
			String certAlias = alias;
			if (certAlias != null && !certAlias.isEmpty()) {
				certificateChain = keyStore.getCertificateChain(certAlias);
			}
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
			logger.warning("Failed to load certificate chain for alias '" + alias + "' - " + e.getMessage());
			certificateChain = null;
		}

		return certificateChain;
	}

	/**
	 * Loads a private key by a given alias name and password.
	 * 
	 * @param alias
	 * @return PrivateKey or null if not found
	 */
	public PrivateKey loadPrivateKey(String alias,String password) {
		KeyStore keyStore;
		PrivateKey privateKey = null;

		try {
			keyStore = openKeyStore();
			
			
			//achtung hier ist das passwort falsch weil dass das vom key ist bzw. "" weil wir keins haben!°!!!!!!!!!!!!!!!!1
			//privateKey = (PrivateKey) keyStore.getKey(alias,keyStorePassword.toCharArray());
			privateKey = (PrivateKey) keyStore.getKey(alias,password.toCharArray());
			
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException
				| UnrecoverableKeyException e) {
			logger.warning("Failed to load certificate chain for alias '" + alias + "' - " + e.getMessage());
			privateKey = null;
		}

		return privateKey;
	}
	
	/**
     * Loads a private key by a given alias name with an empty password.
     * 
     * @param alias
     * @return PrivateKey or null if not found
     */
	   public PrivateKey loadPrivateKey(String alias) {
	       return loadPrivateKey(alias,"");
	   }
	
	
	public void addEntry(String alias) {
		KeyStore keyStore;
		try {
			keyStore = openKeyStore();
			
			CertGenerator.createNewEntry(keyStore, alias, keyStorePassword.toCharArray());
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException
				 e) {
			logger.warning("Failed to load certificate chain for alias '" + alias + "' - " + e.getMessage());
			
		}

	}
	
}
