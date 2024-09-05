package org.imixs.archive.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.security.NoSuchAlgorithmException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

/**
 * Test the SnapshotService EJB. The WorkflowArchiveMockEnvironment provides a
 * workflowService and Database mock.
 * 
 * @author rsoika
 * 
 */
public class TestSnapshotService {
	private final static Logger logger = Logger.getLogger(TestSnapshotService.class.getName());

	@InjectMocks
	SnapshotService snapshotService;

	ItemCollection workitem;
	ItemCollection event;

	WorkflowMockEnvironment workflowEnvironment;

	/**
	 * Load test model and mock SnapshotPlugin
	 * 
	 * @throws ModelException
	 */
	@BeforeEach
	public void setup() throws PluginException, ModelException {
		// Ensures that @Mock and @InjectMocks annotations are processed
		MockitoAnnotations.openMocks(this);

		workflowEnvironment = new WorkflowMockEnvironment();

		workflowEnvironment.setUp();
		workflowEnvironment.loadBPMNModel("/bpmn/TestSnapshotService.bpmn");

		// mock session context
		snapshotService.ejbCtx = workflowEnvironment.getWorkflowContext().getSessionContext();

		snapshotService.documentService = workflowEnvironment.getDocumentService();

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
		ItemCollection workitem = workflowEnvironment.getDocumentService().load("W0000-00001");
		workitem.model("1.0.0").task(1000).event(10);

		DocumentEvent documentEvent = new DocumentEvent(workitem, DocumentEvent.ON_DOCUMENT_SAVE);
		snapshotService.archiveServiceEndpoint = Optional.of("");
		snapshotService.backupServiceEndpoint = Optional.of("");
		snapshotService.onSave(documentEvent);

		workitem = workflowEnvironment.getWorkflowService().processWorkItem(workitem);

		assertEquals("1.0.0", workitem.getItemValueString("$ModelVersion"));

		// test the $snapshotID
		assertTrue(workitem.hasItem(SnapshotService.SNAPSHOTID));
		String snapshotID = workitem.getItemValueString(SnapshotService.SNAPSHOTID);
		assertFalse(snapshotID.isEmpty());
		logger.info("$snapshotid=" + snapshotID);
		assertTrue(snapshotID.startsWith("W0000-00001"));

		// load the snapshot workitem
		ItemCollection snapshotworkitem = workflowEnvironment.getDatabase().get(snapshotID);
		assertNotNull(snapshotworkitem);

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
		ItemCollection workitem = workflowEnvironment.getDocumentService().load("W0000-00001");
		workitem.model("1.0.0").task(1000).event(10);

		byte[] data = "This is a test".getBytes();
		// we attache a file....
		// workitem.addFile(data, "test.txt", null);
		workitem.addFileData(new FileData("test.txt", data, null, null));

		DocumentEvent documentEvent = new DocumentEvent(workitem, DocumentEvent.ON_DOCUMENT_SAVE);
		snapshotService.archiveServiceEndpoint = Optional.of("");
		snapshotService.backupServiceEndpoint = Optional.of("");
		snapshotService.onSave(documentEvent);
		workitem = workflowEnvironment.getWorkflowService().processWorkItem(workitem);

		assertEquals("1.0.0", workitem.getItemValueString("$ModelVersion"));

		// test the $snapshotID
		assertTrue(workitem.hasItem(SnapshotService.SNAPSHOTID));
		String snapshotID = workitem.getItemValueString(SnapshotService.SNAPSHOTID);
		assertFalse(snapshotID.isEmpty());
		logger.info("$snapshotid=" + snapshotID);
		assertTrue(snapshotID.startsWith("W0000-00001"));

		// load the snapshot workitem
		ItemCollection snapshotworkitem = workflowEnvironment.getDatabase().get(snapshotID);
		assertNotNull(snapshotworkitem);

		// test the file content
		// List<Object> fileData = snapshotworkitem.getFile("test.txt");
		FileData fileData = snapshotworkitem.getFileData("test.txt");
		assertEquals("This is a test", new String(fileData.getContent()));

		/*
		 * Now we trigger a second event to create a version be we remove the file
		 * content before...
		 */
		workitem.replaceItemValue(WorkflowKernel.EVENTID, 20);
		documentEvent = new DocumentEvent(workitem, DocumentEvent.ON_DOCUMENT_SAVE);
		snapshotService.onSave(documentEvent);
		workitem = workflowEnvironment.getWorkflowService().processWorkItem(workitem);

		// remove the file content from the origin....
		workitem.removeFile("test.txt");
		workflowEnvironment.getDocumentService().save(workitem);

		/*
		 * now lets check the version. The version should have the file content.
		 */

		// now lets check the version....
		String versionID = workitem.getItemValueString("$uniqueIdVersions");

		ItemCollection version = workflowEnvironment.getDatabase().get(versionID);
		assertNotNull(version);

		// simulate snaptshot cdi event
		DocumentEvent documentEvent2 = new DocumentEvent(version, DocumentEvent.ON_DOCUMENT_SAVE);
		snapshotService.onSave(documentEvent2);

		// get the snapshot ID....
		String versionSnapshot = version.getItemValueString("$snapshotid");
		logger.info("Version $snapshotid=" + versionSnapshot);

		// version snapshot id MUST not be equal
		assertFalse(snapshotID.equals(versionSnapshot));

		// we load the snapshot version and we expect again the fail content....
		ItemCollection snapshotworkitemVersion = workflowEnvironment.getDatabase().get(versionSnapshot);
		assertNotNull(snapshotworkitemVersion);

		// test the file content
		// List<Object> fileDataVersion = snapshotworkitemVersion.getFile("test.txt");
		FileData fileDataVersion = snapshotworkitemVersion.getFileData("test.txt");
		// byte[] contentVersion = (byte[]) fileDataVersion.get(1);
		assertEquals("This is a test", new String(fileDataVersion.getContent()));

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
		ItemCollection workitem = workflowEnvironment.getDocumentService().load("W0000-00001");

		workitem.model("1.0.0").task(1000).event(10);

		// add file...
		byte[] dummyContent = { 1, 2, 3 };
		FileData filedata = new FileData("test.txt", dummyContent, "text", null);
		workitem.addFileData(filedata);

		DocumentEvent documentEvent = new DocumentEvent(workitem, DocumentEvent.ON_DOCUMENT_SAVE);

		snapshotService.archiveServiceEndpoint = Optional.of("");
		snapshotService.backupServiceEndpoint = Optional.of("");
		snapshotService.onSave(documentEvent);

		workitem = workflowEnvironment.getWorkflowService().processWorkItem(workitem);

		assertEquals("1.0.0", workitem.getItemValueString("$ModelVersion"));

		// test the $snapshotID
		assertTrue(workitem.hasItem(SnapshotService.SNAPSHOTID));
		String snapshotID = workitem.getItemValueString(SnapshotService.SNAPSHOTID);
		assertFalse(snapshotID.isEmpty());
		logger.info("$snapshotid=" + snapshotID);
		assertTrue(snapshotID.startsWith("W0000-00001"));

		// load the snapshot workitem
		ItemCollection snapshotworkitem = workflowEnvironment.getDatabase().get(snapshotID);
		assertNotNull(snapshotworkitem);

		// test the file data of workitem
		FileData testfiledataOrigin = workitem.getFileData("test.txt");
		assertEquals(filedata.getName(), testfiledataOrigin.getName());
		assertEquals(filedata.getContentType(), testfiledataOrigin.getContentType());
		assertTrue(testfiledataOrigin.getContent().length == 0);

		// test the file data of snapshot
		FileData testfiledataSnapshot = snapshotworkitem.getFileData("test.txt");
		assertEquals(filedata.getName(), testfiledataSnapshot.getName());
		assertEquals(filedata.getContentType(), testfiledataSnapshot.getContentType());
		assertTrue(testfiledataSnapshot.getContent().length == 3);

		// now test the DMS item
		ItemCollection dmsItemCol = new ItemCollection(testfiledataOrigin.getAttributes());
		assertNotNull(dmsItemCol);
		assertEquals(3, dmsItemCol.getItemValueInteger("size"));
		try {
			assertTrue(testfiledataSnapshot.validateMD5(dmsItemCol.getItemValueString("md5checksum")));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			fail();
		}
	}

}
