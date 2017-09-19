package org.imixs.archive.api;

import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.engine.WorkflowArchiveMockEnvironment;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.exceptions.ProcessingErrorException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import junit.framework.Assert;

/**
 * Test the Snapshot plug-in
 * 
 * @author rsoika
 * 
 */
public class TestSnapshotPlugin {

	private final static Logger logger = Logger.getLogger(TestSnapshotPlugin.class.getName());

	SnapshotPlugin snapshotPlugin = null;
	ItemCollection documentContext;
	ItemCollection documentActivity, documentProcess;

	/**
	 * We use the provided test workflow model form the AbstractWorkflowServiceTest
	 * 
	 * @throws ModelException
	 */
	WorkflowArchiveMockEnvironment workflowMockEnvironment;

	@Before
	public void setup() throws PluginException, ModelException {

		workflowMockEnvironment = new WorkflowArchiveMockEnvironment();
		workflowMockEnvironment.setModelPath("/bpmn/TestSnapshotPlugin.bpmn");

		workflowMockEnvironment.setup();

		// mock abstract plugin class for the plitAndJoinPlugin
		snapshotPlugin = Mockito.mock(SnapshotPlugin.class, Mockito.CALLS_REAL_METHODS);
		when(snapshotPlugin.getWorkflowService()).thenReturn(workflowMockEnvironment.getWorkflowService());
		try {
			snapshotPlugin.init(workflowMockEnvironment.getWorkflowContext());
		} catch (PluginException e) {

			e.printStackTrace();
		}
		
		// prepare test workitem
		documentContext = new ItemCollection();
		logger.info("setup test data...");
		Vector<String> list = new Vector<String>();
		list.add("manfred");
		list.add("anna");
		documentContext.replaceItemValue("namTeam", list);
		documentContext.replaceItemValue("namCreator", "ronny");
		documentContext.replaceItemValue(WorkflowKernel.MODELVERSION, WorkflowArchiveMockEnvironment.DEFAULT_MODEL_VERSION);
		documentContext.replaceItemValue(WorkflowKernel.PROCESSID, 100);
		documentContext.replaceItemValue(WorkflowKernel.UNIQUEID, WorkflowKernel.generateUniqueID());
		workflowMockEnvironment.getDocumentService().save(documentContext);

	}

	
	/**
	 * This test simulates a workflowService process call by mocking the entity and
	 * model service.
	 * 
	 * This is just a simple simulation...
	 * 
	 * @throws ProcessingErrorException
	 * @throws AccessDeniedException
	 * @throws ModelException
	 * 
	 */
	@Test
	public void testProcessSimple()
			throws AccessDeniedException, ProcessingErrorException, PluginException, ModelException {
		// load test workitem
		ItemCollection workitem = workflowMockEnvironment.getDatabase().get("W0000-00001");
		workitem.replaceItemValue(WorkflowKernel.MODELVERSION, WorkflowArchiveMockEnvironment.DEFAULT_MODEL_VERSION);
		workitem.replaceItemValue(WorkflowKernel.PROCESSID, 1000);
		workitem.replaceItemValue(WorkflowKernel.ACTIVITYID, 10);
		workitem = workflowMockEnvironment.getWorkflowService().processWorkItem(workitem);

		Assert.assertEquals("1.0.0", workitem.getItemValueString("$ModelVersion"));

		
		// test the $snapshotID
		Assert.assertTrue(workitem.hasItem(SnapshotPlugin.SNAPSHOTID));
		String snapshotID=workitem.getItemValueString(SnapshotPlugin.SNAPSHOTID);
		Assert.assertFalse(snapshotID.isEmpty());
		logger.info("$snapshotid="  + snapshotID);
		Assert.assertTrue(snapshotID.startsWith("W0000-00001"));
		
		// load snapshot workitem
		ItemCollection snapshotworkitem= workflowMockEnvironment.getDatabase().get(snapshotID);
		Assert.assertNotNull(snapshotworkitem);
		
	}
	
	

}
