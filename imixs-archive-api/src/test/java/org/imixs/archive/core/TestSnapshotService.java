package org.imixs.archive.core;

import static org.mockito.Mockito.when;

import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.Optional;
import java.util.logging.Logger;

import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.engine.DocumentEvent;
import org.imixs.workflow.engine.WorkflowMockEnvironment;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.exceptions.ProcessingErrorException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import jakarta.ejb.SessionContext;

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
		workitem.replaceItemValue(WorkflowKernel.TASKID, 1000);
		workitem.replaceItemValue(WorkflowKernel.EVENTID, 10);

		DocumentEvent documentEvent = new DocumentEvent(workitem, DocumentEvent.ON_DOCUMENT_SAVE);
		snapshotService.archiveServiceEndpoint=Optional.of("");
		snapshotService.backupServiceEndpoint=Optional.of("");
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
	 * <p>
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
		workitem.replaceItemValue(WorkflowKernel.TASKID, 1000);
		// trigger the split event
		workitem.replaceItemValue(WorkflowKernel.EVENTID, 10);

		byte[] data = "This is a test".getBytes();
		// we attache a file....
		//workitem.addFile(data, "test.txt", null);
		workitem.addFileData(new FileData("test.txt", data, null, null));
		
		
		DocumentEvent documentEvent = new DocumentEvent(workitem, DocumentEvent.ON_DOCUMENT_SAVE);
		snapshotService.archiveServiceEndpoint=Optional.of("");
		snapshotService.backupServiceEndpoint=Optional.of("");
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
		//List<Object> fileData = snapshotworkitem.getFile("test.txt");
		FileData fileData=snapshotworkitem.getFileData("test.txt");
		Assert.assertEquals("This is a test", new String(fileData.getContent()));
 
		/*
		 * Now we trigger a second event to create a version be we remove the file
		 * content before...
		 */ 
		workitem.replaceItemValue(WorkflowKernel.EVENTID, 20);
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
		//List<Object> fileDataVersion = snapshotworkitemVersion.getFile("test.txt");
		FileData fileDataVersion=snapshotworkitemVersion.getFileData("test.txt");
		//byte[] contentVersion = (byte[]) fileDataVersion.get(1);
		Assert.assertEquals("This is a test", new String(fileDataVersion.getContent()));

	}

	/**
	 * This test simulates a simple workflow process which generates a new
	 * Snapshot-Workitem.
	 * <p>
	 * The method adds a file and tests the fileData of the workitem and the
	 * snapshot as also the custom attributes
	 * 
	 * @throws ProcessingErrorException
	 * @throws AccessDeniedException
	 * @throws ModelException
	 * 
	 */
	@Test
	public void testFileDataAndDMSEntries()
			throws AccessDeniedException, ProcessingErrorException, PluginException, ModelException {
		// load test workitem
		ItemCollection workitem = workflowMockEnvironment.getDatabase().get("W0000-00001");
		workitem.replaceItemValue(WorkflowKernel.MODELVERSION, WorkflowMockEnvironment.DEFAULT_MODEL_VERSION);
		workitem.replaceItemValue(WorkflowKernel.TASKID, 1000);
		workitem.replaceItemValue(WorkflowKernel.EVENTID, 10);

		// add file...
		byte[] dummyContent = { 1, 2, 3 };
		FileData filedata = new FileData("test.txt", dummyContent, "text",null);
		workitem.addFileData(filedata);

		DocumentEvent documentEvent = new DocumentEvent(workitem, DocumentEvent.ON_DOCUMENT_SAVE);

		snapshotService.archiveServiceEndpoint=Optional.of("");
		snapshotService.backupServiceEndpoint=Optional.of("");
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

		// test the file data of workitem
		FileData testfiledataOrigin = workitem.getFileData("test.txt");
		Assert.assertEquals(filedata.getName(), testfiledataOrigin.getName());
		Assert.assertEquals(filedata.getContentType(), testfiledataOrigin.getContentType());
		Assert.assertTrue(testfiledataOrigin.getContent().length == 0);

		// test the file data of snapshot
		FileData testfiledataSnapshot = snapshotworkitem.getFileData("test.txt");
		Assert.assertEquals(filedata.getName(), testfiledataSnapshot.getName());
		Assert.assertEquals(filedata.getContentType(), testfiledataSnapshot.getContentType());
		Assert.assertTrue(testfiledataSnapshot.getContent().length == 3);

		// now test the DMS item
		ItemCollection dmsItemCol =new ItemCollection(testfiledataOrigin.getAttributes());
		Assert.assertNotNull(dmsItemCol);
		Assert.assertEquals(3, dmsItemCol.getItemValueInteger("size"));
		try {
			Assert.assertTrue(testfiledataSnapshot.validateMD5(dmsItemCol.getItemValueString("md5checksum")));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			Assert.fail();
		}
	}

}
