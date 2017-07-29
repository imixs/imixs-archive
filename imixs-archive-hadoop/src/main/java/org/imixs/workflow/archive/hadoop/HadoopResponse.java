package org.imixs.workflow.archive.hadoop;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * This class encapsulates the hadoop resoponse objects.
 * 
 * @author rsoika
 *
 */
public class HadoopResponse {

	int code;
	String message;
	String type;

	public HadoopResponse(HttpURLConnection conn) {
		try {
			setCode(conn.getResponseCode());
			setMessage(conn.getResponseMessage());
			setType(conn.getContentType());
		} catch (IOException e) {
			// no op!
		}

	}
	
	
	/**
	 * returns true if the response code is >299
	 * @return
	 */
	public boolean isError() {
		if (code<200 || code>299)
			return true;
		else
			return false;
	}

	public int getCode() {
		return code;
	}

	void setCode(int code) {
		this.code = code;
	}

	public String getMessage() {
		return message;
	}

	void setMessage(String message) {
		this.message = message;
	}

	public String getType() {
		return type;
	}

	void setType(String type) {
		this.type = type;
	}

}
