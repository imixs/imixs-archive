package org.imixs.workflow.archive.cassandra;

import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.security.Principal;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Logger;

import javax.ejb.SessionContext;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.engine.PropertyService;
import org.imixs.workflow.engine.WorkflowMockEnvironment;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.xml.XMLItemCollection;
import org.imixs.workflow.xml.XMLItemCollectionAdapter;
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
	private static Logger logger = Logger.getLogger(TestClusterService.class.getName());

	@Spy
	ClusterService clusterService;

	@Spy
	PropertyService propertyService;

	SessionContext ctx;
	WorkflowMockEnvironment workflowMockEnvironment;

	public static String CONNACT_POINT = "localhost";
	public static String KEYSPACE = "imixs_dev";

	Session session = null;

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

		// create session...
		session = clusterService.connect();

	}

	/**
	 * Test the local connection
	 */
	@Test
	public void testCluster() {

		Assert.assertNotNull(session);
	}

	/**
	 * Test save method
	 */
	@Test
	public void testSaveDocument() {

		ItemCollection itemCol = new ItemCollection();
		itemCol.replaceItemValue("$uniqueid", WorkflowKernel.generateUniqueID());
		itemCol.replaceItemValue("type", "workitem");
		itemCol.replaceItemValue("$created", new Date());
		itemCol.replaceItemValue("$modified", new Date());
		itemCol.replaceItemValue("someData", "Let’s understand this by examining the data of an ItemCollection");

		Session session = clusterService.connect();

		try {
			clusterService.save(itemCol, session);
		} catch (JAXBException e) {
			Assert.fail();
		}

		Assert.assertNotNull(session);
	}

	/**
	 * Test save 10 documents...
	 */
	@Test
	public void testSaveBatch() {

		Session session = clusterService.connect();

		int count = 0;
		while (count < 10) {
			long l = System.currentTimeMillis();

			ItemCollection itemCol = new ItemCollection();
			itemCol.replaceItemValue("$uniqueid", WorkflowKernel.generateUniqueID());
			itemCol.replaceItemValue("type", "workitem");
			itemCol.replaceItemValue("$created", new Date());
			itemCol.replaceItemValue("$modified", new Date());
			itemCol.replaceItemValue("someData", "Let’s understand this by examining the data of an ItemCollection");

			try {
				clusterService.save(itemCol, session);
			} catch (JAXBException e) {
				Assert.fail();
			}

			Assert.assertNotNull(session);
			count++;

			System.out.println(".....written in " + (System.currentTimeMillis() - l) + "ms");

		}

	}

	/**
	 * This is just a simple test to show that serialized hash map is * only
	 * slightly different in size from a XML representation object.
	 */
	@Test
	public void testXMLvsSerializeable() {
		int xmlSize = 0;
		int objectSize = 0;

		// create a simple workitem...
		ItemCollection itemCol = new ItemCollection();
		itemCol.replaceItemValue("$uniqueid", WorkflowKernel.generateUniqueID());
		itemCol.replaceItemValue("type", "workitem");
		itemCol.replaceItemValue("$created", new Date());
		itemCol.replaceItemValue("$modified", new Date());
		itemCol.replaceItemValue("someData",
				"Let’s understand this by examining the data of an ItemCollection.Let’s understand this by examining the data of an ItemCollection.Let’s understand this by examining the data of an ItemCollection.Let’s understand this by examining the data of an ItemCollection.");

		// convert the ItemCollection into xml...
		XMLItemCollection xmlItemCollection = XMLItemCollectionAdapter.putItemCollection(itemCol);
		StringWriter writer = new StringWriter();
		JAXBContext context;
		try {
			context = JAXBContext.newInstance(XMLItemCollection.class);
			Marshaller m = context.createMarshaller();
			m.marshal(xmlItemCollection, writer);
		} catch (JAXBException e1) {
			e1.printStackTrace();
			Assert.fail();
		}
		String result = writer.toString();
		xmlSize = result.length();
		logger.info(result);
		logger.info("Size of XML Document = " + xmlSize);

		// serialize the object...
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos;
		try {
			oos = new ObjectOutputStream(baos);
			oos.writeObject(itemCol.getAllItems());
			oos.close();
			byte[] seriaizedObj = baos.toByteArray();

			logger.info(seriaizedObj.toString());
			objectSize = seriaizedObj.length;
			logger.info("Size of serialized Object = " + objectSize);

		} catch (IOException e) {

			e.printStackTrace();
			Assert.fail();
		}

		float f = (xmlSize - objectSize) / (float) xmlSize * 100;
		logger.info("Difference = " + (f) + "%");
	}

}
