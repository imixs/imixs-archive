package org.imixs.archive.api;

import java.util.logging.Logger;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.engine.DocumentEvent;
import org.imixs.workflow.engine.WorkflowMockEnvironment;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.exceptions.ProcessingErrorException;
import org.junit.Before;
import org.junit.Test;
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

		snapshotService.documentService=workflowMockEnvironment.getDocumentService();
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
	public void testOnSave()
			throws AccessDeniedException, ProcessingErrorException, PluginException, ModelException {
		// load test workitem
		ItemCollection workitem = workflowMockEnvironment.getDatabase().get("W0000-00001");
		workitem.replaceItemValue(WorkflowKernel.MODELVERSION, WorkflowMockEnvironment.DEFAULT_MODEL_VERSION);
		workitem.replaceItemValue(WorkflowKernel.PROCESSID, 1000);
		workitem.replaceItemValue(WorkflowKernel.ACTIVITYID, 10);
		
		DocumentEvent documentEvent=new DocumentEvent(workitem, DocumentEvent.ON_DOCUMENT_SAVE);
		
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

}
