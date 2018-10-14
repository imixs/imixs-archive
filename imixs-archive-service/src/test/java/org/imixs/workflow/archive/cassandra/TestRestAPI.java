package org.imixs.workflow.archive.cassandra;

import java.util.List;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.services.rest.FormAuthenticator;
import org.imixs.workflow.services.rest.RequestFilter;
import org.imixs.workflow.services.rest.RestClient;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * This class makes a test agains a running instance of office workflow to test
 * the api.
 * 
 * @author rsoika
 * @version 1.0.0
 */
public class TestRestAPI {
	/**
	 * This various snaphsot id patterns
	 * 
	 */
	@Ignore
	@Test
	public void simpleApiTest() {

		String apiURL = "http://localhost:8081/api";

		RequestFilter formAuth = new FormAuthenticator(apiURL, "admin", "adminadmin");
		// register the authenticator
		RestClient client = new RestClient();

		client.registerRequestFilter(formAuth);
		
		
		try {
			List<ItemCollection> result = client.getDocumentCollection(apiURL+"/snapshot/syncpoint/0");
			
			Assert.assertNotNull(result);
			Assert.assertEquals(1, result.size());
			
		} catch (Exception e) {
			
			e.printStackTrace();
			Assert.fail();
		}

	}

}