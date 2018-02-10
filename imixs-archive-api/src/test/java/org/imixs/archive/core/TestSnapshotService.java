package org.imixs.archive.core;

import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import javax.ejb.SessionContext;

import org.imixs.archive.core.SnapshotService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.engine.DocumentEvent;
import org.imixs.workflow.engine.PropertyService;
import org.imixs.workflow.engine.WorkflowMockEnvironment;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.exceptions.ProcessingErrorException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import junit.framework.Assert;

/**
 * Test the SnapshotService EJB. The WorkflowArchiveMockEnvironment provides a
 * workflowService and Database mock.
 * 
 * @author rsoika
 * 
 */
public class TestSnapshotService {
	private final static Logger logger = Logger.getLogger(TestSnapshotService.class.getName());

	@Spy
	SnapshotService snapshotService;

	@Spy
	PropertyService propertyService;

	SessionContext ctx;

	ItemCollection documentContext;
	ItemCollection documentActivity, documentProcess;

	WorkflowMockEnvironment workflowMockEnvironment;

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
		snapshotService.ejbCtx = ctx;
		// simulate SessionContext ctx.getCallerPrincipal().getName()
		Principal principal = Mockito.mock(Principal.class);
		when(principal.getName()).thenReturn("manfred");
		when(ctx.getCallerPrincipal()).thenReturn(principal);

		snapshotService.documentService = workflowMockEnvironment.getDocumentService();

		// mock property service
		snapshotService.propertyService = propertyService;
		Properties emptyProperties = new Properties();
		when(propertyService.getProperties()).thenReturn(emptyProperties);
	}

	/**
	 * This test simulates a simple workflow process which generates a new
	 * Snapshot-Workitem.
	 * 
	 * @throws ProcessingErrorException
	 * @throws AccessDeniedException
	 * @throws ModelException
	 * 
	 */
	@Test
	public void testOnSave() throws AccessDeniedException, ProcessingErrorException, PluginException, ModelException {
		// load test workitem
		ItemCollection workitem = workflowMockEnvironment.getDatabase().get("W0000-00001");
		workitem.replaceItemValue(WorkflowKernel.MODELVERSION, WorkflowMockEnvironment.DEFAULT_MODEL_VERSION);
		workitem.replaceItemValue(WorkflowKernel.PROCESSID, 1000);
		workitem.replaceItemValue(WorkflowKernel.ACTIVITYID, 10);

		DocumentEvent documentEvent = new DocumentEvent(workitem, DocumentEvent.ON_DOCUMENT_SAVE);

		snapshotService.onSave(documentEvent);

		workitem = workflowMockEnvironment.getWorkflowService().processWorkItem(workitem);

		Assert.assertEquals("1.0.0", workitem.getItemValueString("$ModelVersion"));

		// test the $snapshotID
		Assert.assertTrue(workitem.hasItem(SnapshotService.SNAPSHOTID));
		String snapshotID = workitem.getItemValueString(SnapshotService.SNAPSHOTID);
		Assert.assertFalse(snapshotID.isEmpty());
		logger.info("$snapshotid=" + snapshotID);
		Assert.assertTrue(snapshotID.startsWith("W0000-00001"));

		// load the snapshot workitem
		ItemCollection snapshotworkitem = workflowMockEnvironment.getDatabase().get(snapshotID);
		Assert.assertNotNull(snapshotworkitem);

	}

	/**
	 * This test simulates a split event which results in a version of the origin
	 * workitem. The file content must be migrated from the origin workitem also
	 * into the version.
	 * 
	 * We simulate this here with a second call of snapshotService.onSave(). This is
	 * a little bit ugly but I think the test integration is deep enough.
	 * 
	 * @throws ProcessingErrorException
	 * @throws AccessDeniedException
	 * @throws ModelException
	 * 
	 */
	@Test
	public void testOnSaveVersion()
			throws AccessDeniedException, ProcessingErrorException, PluginException, ModelException {
		// load test workitem
		ItemCollection workitem = workflowMockEnvironment.getDatabase().get("W0000-00001");
		workitem.replaceItemValue(WorkflowKernel.MODELVERSION, WorkflowMockEnvironment.DEFAULT_MODEL_VERSION);
		workitem.replaceItemValue(WorkflowKernel.PROCESSID, 1000);
		// trigger the split event
		workitem.replaceItemValue(WorkflowKernel.ACTIVITYID, 10);

		byte[] data = "This is a test".getBytes();
		// we attache a file....
		workitem.addFile(data, "test.txt", null);

		DocumentEvent documentEvent = new DocumentEvent(workitem, DocumentEvent.ON_DOCUMENT_SAVE);
		snapshotService.onSave(documentEvent);
		workitem = workflowMockEnvironment.getWorkflowService().processWorkItem(workitem);

		Assert.assertEquals("1.0.0", workitem.getItemValueString("$ModelVersion"));

		// test the $snapshotID
		Assert.assertTrue(workitem.hasItem(SnapshotService.SNAPSHOTID));
		String snapshotID = workitem.getItemValueString(SnapshotService.SNAPSHOTID);
		Assert.assertFalse(snapshotID.isEmpty());
		logger.info("$snapshotid=" + snapshotID);
		Assert.assertTrue(snapshotID.startsWith("W0000-00001"));

		// load the snapshot workitem
		ItemCollection snapshotworkitem = workflowMockEnvironment.getDatabase().get(snapshotID);
		Assert.assertNotNull(snapshotworkitem);

		// test the file content
		List<Object> fileData = snapshotworkitem.getFile("test.txt");
		byte[] content = (byte[]) fileData.get(1);
		Assert.assertEquals("This is a test", new String(content));

		/*
		 * Now we trigger a second event to create a version be we remove the file
		 * content before...
		 */
		workitem.replaceItemValue(WorkflowKernel.ACTIVITYID, 20);
		documentEvent = new DocumentEvent(workitem, DocumentEvent.ON_DOCUMENT_SAVE);
		snapshotService.onSave(documentEvent);
		workitem = workflowMockEnvironment.getWorkflowService().processWorkItem(workitem);

		// remove the file content from the origin....
		workitem.removeFile("test.txt");
		workflowMockEnvironment.getDocumentService().save(workitem);

		/*
		 * now lets check the version. The version should have the file content.
		 */

		// now lets check the version....
		String versionID = workitem.getItemValueString("$uniqueIdVersions");

		ItemCollection version = workflowMockEnvironment.getDatabase().get(versionID);
		Assert.assertNotNull(version);

		// simulate snaptshot cdi event
		DocumentEvent documentEvent2 = new DocumentEvent(version, DocumentEvent.ON_DOCUMENT_SAVE);
		snapshotService.onSave(documentEvent2);

		// get the snapshot ID....
		String versionSnapshot = version.getItemValueString("$snapshotid");
		logger.info("Version $snapshotid=" + versionSnapshot);

		// version snapshot id MUST not be equal
		Assert.assertFalse(snapshotID.equals(versionSnapshot));

		// we load the snapshot version and we expect again the fail content....
		ItemCollection snapshotworkitemVersion = workflowMockEnvironment.getDatabase().get(versionSnapshot);
		Assert.assertNotNull(snapshotworkitemVersion);

		// test the file content
		List<Object> fileDataVersion = snapshotworkitemVersion.getFile("test.txt");
		byte[] contentVersion = (byte[]) fileDataVersion.get(1);
		Assert.assertEquals("This is a test", new String(contentVersion));

	}

}
