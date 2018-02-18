package org.imixs.workflow.archive.cassandra;

import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.Properties;

import javax.ejb.SessionContext;

import org.imixs.workflow.engine.PropertyService;
import org.imixs.workflow.engine.WorkflowMockEnvironment;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import com.datastax.driver.core.Session;

/**
 * Test class to test the CluserService EJB
 * 
 * @author rsoika
 * 
 */
public class TestClusterService {

	@Spy
	ClusterService clusterService;

	@Spy
	PropertyService propertyService;

	SessionContext ctx;
	WorkflowMockEnvironment workflowMockEnvironment;

	public static String CONNACT_POINT = "localhost";
	public static String KEYSPACE = "imixs_dev";

	/**
	 * Load test model and mock SnapshotPlugin
	 * 
	 * @throws ModelException
	 */
	@Before
	public void setup() throws PluginException, ModelException {

		workflowMockEnvironment = new WorkflowMockEnvironment();
		workflowMockEnvironment.setModelPath("/bpmn/TestSnapshotService.bpmn");
		workflowMockEnvironment.setup();

		MockitoAnnotations.initMocks(this);

		// mock session context
		ctx = Mockito.mock(SessionContext.class);
		// snapshotService.ejbCtx = ctx;
		// simulate SessionContext ctx.getCallerPrincipal().getName()
		Principal principal = Mockito.mock(Principal.class);
		when(principal.getName()).thenReturn("manfred");
		when(ctx.getCallerPrincipal()).thenReturn(principal);

		clusterService.documentService = workflowMockEnvironment.getDocumentService();

		// mock property service
		clusterService.propertyService = propertyService;
		Properties p = new Properties();
		InputStream inputStream = getClass().getResourceAsStream("/imixs.properties");
		try {
			p.load(inputStream);
		} catch (IOException e) {
			Assert.fail();
		}
		when(propertyService.getProperties()).thenReturn(p);
	}

	/**
	 * Test the local connection
	 */
	@Test
	public void testCluster() {

		Session session = clusterService.connect();

		Assert.assertNotNull(session);
	}

}
