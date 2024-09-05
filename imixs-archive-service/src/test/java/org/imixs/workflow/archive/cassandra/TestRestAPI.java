package org.imixs.workflow.archive.cassandra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.imixs.melman.DocumentClient;
import org.imixs.melman.FormAuthenticator;
import org.imixs.workflow.ItemCollection;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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
	@Disabled
	@Test
	public void simpleApiTest() {

		String apiURL = "http://localhost:8081/api";
		try {

			DocumentClient documentClient = new DocumentClient(apiURL);
			FormAuthenticator formAuth = new FormAuthenticator(apiURL, "admin", "adminadmin");
			// register the authenticator
			documentClient.registerClientRequestFilter(formAuth);

			List<ItemCollection> result = documentClient.getCustomResource(apiURL + "/snapshot/syncpoint/0");

			assertNotNull(result);
			assertEquals(1, result.size());

		} catch (Exception e) {

			e.printStackTrace();
			fail();
		}

	}

}