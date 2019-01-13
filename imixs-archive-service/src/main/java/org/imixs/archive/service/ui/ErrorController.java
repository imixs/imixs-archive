package org.imixs.archive.service.ui;

import java.io.Serializable;

import javax.enterprise.context.SessionScoped;
import javax.inject.Named;

/**
 * Session Scoped CID Bean to hold the current error messages.
 * 
 * @author rsoika
 *
 */
@Named
@SessionScoped
public class ErrorController implements Serializable {

	private static final long serialVersionUID = 1L;

	String message = null;
 
	public ErrorController() {
		super();
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	/**
	 * adds an error message to the current message.
	 * 
	 * @param message
	 */
	public void addMessage(String message) {
		this.message += "\n" + message;
	}

	/**
	 * reset the current error message
	 */
	public void reset() {
		message = "";
	}

}