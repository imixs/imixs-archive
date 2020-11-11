package org.imixs.archive.signature.cert;

/**
 * Copied from Apache CXF 2.4.9, initial version:
 * https://svn.apache.org/repos/asf/cxf/tags/cxf-2.4.9/distribution/src/main/release/samples/sts_issue_operation/src/main/java/demo/sts/provider/cert/
 * 
 */
public class CertificateVerificationException extends Exception {
	private static final long serialVersionUID = 1L;
	

	public CertificateVerificationException(String message, Throwable cause) {
		super(message, cause);
	}

	public CertificateVerificationException(String message) {
		super(message);
	}
}