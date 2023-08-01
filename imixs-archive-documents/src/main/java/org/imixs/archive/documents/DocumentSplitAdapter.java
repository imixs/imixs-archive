package org.imixs.archive.documents;

import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.inject.Inject;

import org.imixs.archive.core.SnapshotService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.SignalAdapter;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.engine.WorkflowService;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.exceptions.ProcessingErrorException;
import org.imixs.workflow.util.XMLParser;

/**
 * The 
 * 
 * 
 * <documentsplit name="subprocess_create">
        <modelversion>1.0.0</modelversion>
        <task>100</task>
        <event>10</event>
        <items>namTeam</items>
    </documentsplit>
 * 
 * @see DocumentSplitAdapter
 * @version 1.0
 * @author rsoika
 */
public class DocumentSplitAdapter implements SignalAdapter {

    private static Logger logger = Logger.getLogger(DocumentSplitAdapter.class.getName());
    public static final String LINK_PROPERTY = "$workitemref";
    public static final String SUBPROCESS_CREATE = "subprocess_create";
    public static final String DOCUMENTSPLIT = "DOCUMENTSPLIT";
    public static final String MODEL_ERROR = "MODEL_ERROR";
    public static final String CONFIG_ERROR = "CONFIG_ERROR";
    public static final String FILE_ERROR = "FILE_ERROR";


    @Inject
    WorkflowService workflowService;

    @Inject
    SnapshotService snapshotService;

    /**
     * This method posts a text from an attachment to the Imixs-ML Analyse service
     * endpoint
     */
    @SuppressWarnings("unchecked")
    @Override
    public ItemCollection execute(ItemCollection workitem, ItemCollection event) throws AdapterException,PluginException {

       try {

            ItemCollection evalItemCollection = null;
            boolean debug = logger.isLoggable(Level.FINE);
            evalItemCollection = workflowService.evalWorkflowResult(event, "documentsplit", workitem, false);
       

            if (evalItemCollection == null) {
                // no configuration found!
                return workitem;
            }

        
            // 1.) test for items with name subprocess_create and create the
            // defined suprocesses
            if (evalItemCollection.hasItem(SUBPROCESS_CREATE)) {
                if (debug) {
                    logger.finest("......processing " + SUBPROCESS_CREATE);
                }
                // extract the create subprocess definitions...
                List<String> processValueList = evalItemCollection.getItemValue(SUBPROCESS_CREATE);

                createSubprocesses(processValueList, workitem);
            }


        } catch ( ProcessingErrorException | ModelException e) {
            String message = "unable to extract file data: " + e.getMessage();
            throw new PluginException(DOCUMENTSPLIT, MODEL_ERROR, message, e);
        }
        return workitem;
    }





    /**
     * This method expects a list of Subprocess definitions and create for each
     * definition a new subprocess. The reference of the created subprocess will be
     * stored in the property $workitemRef of the origin workitem
     * 
     * 
     * The definition is expected in the following format
     * 
     * <code>
     *    <modelversion>1.0.0</modelversion>
     *    <task>100</task>
     *    <event>20</event>
     *    <items>namTeam,_sub_data</items>
     *    <action>home</action>
     * </code>
     * 
     *
     * Both workitems are connected to each other. The subprocess will contain the
     * $UniqueID of the origin process stored in the property $uniqueidRef. The
     * origin process will contain a link to the subprocess stored in the property
     * txtworkitemRef.
     * 
     * @see SplitAndJoinPlugin class
     *
     * 
     * @param subProcessDefinitions
     * @param originWorkitem
     * @throws AccessDeniedException
     * @throws ProcessingErrorException
     * @throws PluginException
     * @throws ModelException
     * @throws AdapterException
     */
    protected void createSubprocesses(final List<String> subProcessDefinitions, final ItemCollection originWorkitem)
            throws  PluginException, AccessDeniedException, ProcessingErrorException, ModelException {

        if (subProcessDefinitions == null || subProcessDefinitions.size() == 0) {
            // no definition found
            return;
        }
        boolean debug = logger.isLoggable(Level.FINE);
        // we iterate over each declaration of a SUBPROCESS_CREATE item....
        for (String processValue : subProcessDefinitions) {
            if (processValue.trim().isEmpty()) {
                // no definition
                continue;
            }
            // evaluate the item content (XML format expected here!)
            ItemCollection processData = XMLParser.parseItemStructure(processValue);
            if (processData != null) {
                // create a string list with all filenames matching the given pattern
                String filepatternString = processData.getItemValueString("filepattern");  
                if (filepatternString.isEmpty()) {
                      throw new PluginException(DOCUMENTSPLIT, MODEL_ERROR, "DocumentSplitAdapter - missing filepattern, please check workflow model!");
                }
                Pattern filePattern;
                try {
                    filePattern = Pattern.compile(filepatternString);                  
                } catch (PatternSyntaxException e) {
                     throw new PluginException(DOCUMENTSPLIT, CONFIG_ERROR,"Invalid filepattern regex: " + e.getMessage());
                }

                List<String> fileNames=originWorkitem.getFileNames();   
                int count=0;   
                for (String fileName : fileNames) {
                    // test if subject matches?
                    Matcher fileMatcher = filePattern.matcher(fileName);
                    if (fileMatcher == null || !fileMatcher.find()) {
                        // file name does not match
                        continue;
                    }                    
                    count++;
                    if (debug) {
                        logger.info("....split filename " + fileName);
                    }
                    processSubWorktitem(processData,originWorkitem, fileName);                    
                } 
                if (count==0) {
                      throw new PluginException(DOCUMENTSPLIT, FILE_ERROR, "DocumentSplitAdapter - no file found matching the given filepattern, please check workflow model!");
                }
            }
        }
    }


    /**
     * This helper method creates and processes a new SubWorkitem based on the given originWorkitem.
     * All itemvalues are copied. All filesm not matching the given filename will be removed.
     * 
     * 
     * @param processData
     * @param originWorkitem
     * @param filename
     * @throws AccessDeniedException
     * @throws ProcessingErrorException
     * @throws PluginException
     * @throws ModelException
     */
    private void processSubWorktitem( ItemCollection processData, ItemCollection originWorkitem, String filename) throws AccessDeniedException, ProcessingErrorException, PluginException, ModelException {
        boolean debug = logger.isLoggable(Level.FINE);
        // create new process instance
        ItemCollection workitemSubProcess = new ItemCollection();

        // now clone the field list...
        copyItemList(processData.getItemValueString("items"), originWorkitem, workitemSubProcess);
        // force creation of new uniqueids
        workitemSubProcess.removeItem(WorkflowKernel.WORKITEMID);
        workitemSubProcess.removeItem(WorkflowKernel.UNIQUEID);     
        workitemSubProcess.removeItem("$eventlog");     
        workitemSubProcess.removeItem("txtworkflowhistory");             

        // now remove all filesdata object not equal to the current filename. 
         List<String> fileNames=originWorkitem.getFileNames(); 
         for (String _fileName : fileNames) {
            if (!_fileName.equals(filename)) {
                if (debug) {
                    logger.info("....remove filename " + _fileName);
                }
                workitemSubProcess.removeFile(_fileName);
            }
        }      
        // check model version
        String sModelVersion = processData.getItemValueString("modelversion");
        if (sModelVersion.isEmpty()) {
            sModelVersion = originWorkitem.getModelVersion();
        }
        workitemSubProcess.replaceItemValue(WorkflowKernel.MODELVERSION, sModelVersion);

        String task_pattern = processData.getItemValueString("task");               
        workitemSubProcess.setTaskID(Integer.valueOf(task_pattern));
        String event_pattern = processData.getItemValueString("event");
        workitemSubProcess.setEventID(Integer.valueOf(event_pattern));

        // add the origin reference
        workitemSubProcess.replaceItemValue(WorkflowService.UNIQUEIDREF, originWorkitem.getUniqueID());

        // process the new subprocess...
        workitemSubProcess = workflowService.processWorkItem(workitemSubProcess);
        
        if (debug) {
            logger.finest("...... successful created new subprocess.");
        }
        // finally add the new workitemRef into the origin
        // documentContext
        addWorkitemRef(workitemSubProcess.getUniqueID(), originWorkitem);
    }

    /**
     * This Method copies the fields defined in 'items' into the targetWorkitem.
     * Multiple values are separated with comma ','.
     * <p>
     * In case a item name contains '|' the target field name will become the right
     * part of the item name.
     * <p>
     * Example: {@code
     *   txttitle,txtfirstname
     *   
     *   txttitle|newitem1,txtfirstname|newitem2
     *   
     * }
     * 
     * <p>
     * Optional also reg expressions are supported. In this case mapping of the item
     * name is not supported.
     * <p>
     * Example: {@code
     *   (^artikel$|^invoice$),txtTitel|txtNewTitel
     *   
     *   
     * } A reg expression must be includes in brackets.
     * 
     */
    protected void copyItemList(String items, ItemCollection source, ItemCollection target) {
        
        // If no items are specified, copy all items
        if (items == null || items.isEmpty()) {
            target.copy(source);
            return;
        }
        
        // clone the field list...
        StringTokenizer st = new StringTokenizer(items, ",");
        while (st.hasMoreTokens()) {
            String field = st.nextToken().trim();

            // test if field is a reg ex
            if (field.startsWith("(") && field.endsWith(")")) {
                Pattern itemPattern = Pattern.compile(field);
                Map<String, List<Object>> map = source.getAllItems();
                for (String itemName : map.keySet()) {
                    if (itemPattern.matcher(itemName).find()) {
                        target.replaceItemValue(itemName, source.getItemValue(itemName));
                    }
                }
            } else {
                // default behavior without reg ex
                int pos = field.indexOf('|');
                if (pos > -1) {
                    target.replaceItemValue(field.substring(pos + 1).trim(),
                            source.getItemValue(field.substring(0, pos).trim()));
                } else {
                    target.replaceItemValue(field, source.getItemValue(field));
                }
            }
        }
    }


       /**
     * This methods adds a new workItem reference into a workitem
     */
    @SuppressWarnings("unchecked")
    protected void addWorkitemRef(String aUniqueID, ItemCollection workitem) {
        boolean debug = logger.isLoggable(Level.FINE);
        if (debug) {
            logger.fine("LinkController add workitem reference: " + aUniqueID);
        }

        List<String> refList = workitem.getItemValue(LINK_PROPERTY);

        // clear empty entry if set
        if (refList.size() == 1 && "".equals(refList.get(0))) {
            refList.remove(0);
        }
        // test if not yet a member of
        if (refList.indexOf(aUniqueID) == -1) {
            refList.add(aUniqueID);
            workitem.replaceItemValue(LINK_PROPERTY, refList);
        }

    }
}