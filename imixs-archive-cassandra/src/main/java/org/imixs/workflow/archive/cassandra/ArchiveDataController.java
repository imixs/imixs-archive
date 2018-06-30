package org.imixs.workflow.archive.cassandra;

import java.io.Serializable;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.inject.Named;

import org.imixs.workflow.archive.cassandra.services.ClusterService;

/**
 * Request Scoped CID Bean to hold the config data for a singel archive.
 * 
 * @author rsoika
 *
 */
@Named
@RequestScoped
public class ArchiveDataController implements Serializable {

	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(ArchiveDataController.class.getName());

	String keyspace;
	String url;
	String userid;
	String password;
	String authMethod;
	int pollingInterval;
	

	
	public ArchiveDataController() {
		super();
	}

	public String getKeyspace() {
		return keyspace;
	}

	public void setKeyspace(String keyspace) {
		this.keyspace = keyspace;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	

	public String getUserid() {
		return userid;
	}

	public void setUserid(String userid) {
		this.userid = userid;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getAuthMethod() {
		return authMethod;
	}

	public void setAuthMethod(String authMethod) {
		this.authMethod = authMethod;
	}

	public int getPollingInterval() {
		return pollingInterval;
	}

	public void setPollingInterval(int pollingInterval) {
		this.pollingInterval = pollingInterval;
	}

	

}