package org.imixs.archive.signature.pdf.cert;

import org.imixs.archive.signature.pdf.SigningService;

/**
 * A SigningException can be thrown during the signing process. 
 * 
 * @see SigningService
 */
public class SigningException extends Exception {
	private static final long serialVersionUID = 1L;
	

	public SigningException(String message, Throwable cause) {
		super(message, cause);
	}

	public SigningException(String message) {
		super(message);
	}
}