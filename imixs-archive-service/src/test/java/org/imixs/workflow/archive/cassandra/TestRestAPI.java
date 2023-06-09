package org.imixs.workflow.archive.cassandra;

import java.util.List;

import org.imixs.melman.DocumentClient;
import org.imixs.melman.FormAuthenticator;
import org.imixs.workflow.ItemCollection;
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
		try {

			DocumentClient documentClient = new DocumentClient(apiURL);
			FormAuthenticator formAuth = new FormAuthenticator(apiURL, "admin", "adminadmin");
			// register the authenticator
			documentClient.registerClientRequestFilter(formAuth);

			List<ItemCollection> result = documentClient.getCustomResource(apiURL + "/snapshot/syncpoint/0");

			Assert.assertNotNull(result);
			Assert.assertEquals(1, result.size());

		} catch (Exception e) {

			e.printStackTrace();
			Assert.fail();
		}

	}

}