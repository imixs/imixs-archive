# Imixs-Archive-Documents

*Imixs-Archive-Document* is a sub-project of Imixs-Archive. The project provides methods to extract textual information from attached documents - including Optical character recognition -  during the processing phase. This information can be used for further processing or to search for documents


## OCR 

The *Optical character recognition (OCR)* is based on the [Apache Project 'Tika'](https://tika.apache.org/). The textual information for each attachment is stored as a custom attribute named 'text' into the FileData object. This information can be used by applications to analyse, verify or process textual information of any document type. The OCR processing is implemented by the *TikaDocumentService*.

### The TikaDocumentService

The *TikaDocumentService* extracts the textual information from file attachments. The service calls the Tika Rest API to extract the text information of a file. The following environment variables are mandatory:
 
  * TIKA\_SERVICE\_ENDPONT - defines the Rest API end-point of the tika server.
  * TIKA\_SERVICE\_MODE - if set to 'auto' the TikaDocumentService reacts on the CDI event 'BEFORE\_PROCESS' and extracts the data automatically. If set to 'model' the *TikaPlugin* or the *TikaAdapter* can be used in a BPMN model to activate the OCR processing

See also the [Docker Image Imixs/Tika](https://cloud.docker.com/u/imixs/repository/docker/imixs/tika) for further information
  
### Auto Processing

OCR processing can be automatically activated for all new attached documents by setting the environment variable *TIKA_SERVICE_MODE* to 'auto'.  If the variable is set to 'model' the *TikaPlugin* or the *TikaAdapter* can be used in a BPMN model to activate the OCR processing in specific situations only. 


### The TikaPlugin

The TikaPlugin class _org.imixs.workflow.documents.TikaPlugin_ can be used as an alternative for the tika service mode 'auto'. The pugin extract  textual information from document attachments based on the model configuration. You need to add the plugin to your model to activate it. 

	org.imixs.workflow.documents.TikaPlugin

The environment variable *TIKA_SERVICE_MODE* must be set to 'model'.  

### The TikaAdapter

The TikaAdapter class _org.imixs.workflow.documents.TikaAdatper_ is a signal adapter which can be bound on a specific BPMN event element.

	org.imixs.workflow.documents.TikaAdapter

The TikaAdapter allows a more fine grained configuration of OCR processing. The environment variable *TIKA_SERVICE_MODE* must be set to 'model'. 

### The OCR MODE

With the optional environment variable  TIKA\_OCR\_MODE the OCR behavior can be controlled:

  * PDF_ONLY -  OCR processing is disabled and text is extracted only from PDF files if available. All other files are ignored
  * OCR_ONLY - pdf and all other files are always OCR scanned.  
  * MIXED - OCR processing is only performed in case no text data can be extracted from a given PDF file (default)

For further configuration see also the docker project [Imixs/tika](https://github.com/imixs/imixs-docker/tree/master/tika).

### Tika Options

Out of the box, Apache Tika will start with the default configuration. By providing additional config options
 you can specify a custom tika configuration to be used by the tika server.

For example to set the DPI mode call:

	@EJB
	TikaDocumentService tikaDocumentService;
	
	// define options
	List<String> options=new ArrayList<String>();
	options.add("X-Tika-PDFocrStrategy=OCR_AND_TEXT_EXTRACTION");
	options.add("X-Tika-PDFOcrImageType=RGB");
	options.add("X-Tika-PDFOcrDPI=400");
	
	// start ocr 
	tikaDocumentService.extractText(workitem, "MIXED", options)

**Note:** Options set by this method call overwrite the options defined in a tika config file. 

You have various options to configure the Tika server. Find details about how to configure imixs-tika [here](https://github.com/imixs/imixs-docker/tree/master/tika).	


## Searching Documents

All extracted textual information from attached documents is also searchable by the Imixs search index. The class *org.imixs.workflow.documents.DocumentIndexer* adds the ocr content for each file attachment into the search index.

## The PDF XML Plugin

The plugin class "_org.imixs.workflow.documents.parser.PDFXMLExtractorPlugin_" can be used to extract embedded XML Data from a PDF document and convert the data into a Imixs Workitem. For example the _ZUGFeRD_ defines a standard XML document for invoices. 

The plugin can be activated by the BPMN Model with the following result definition: 


	<item name="PDFXMLPlugin">
		<filename>*.xml</filename>
	    <report>myReport</report>
	</item>

The Item "PDFXMLExtractorPlugin" provides the following processing instructions for the PDFXMLPlugin:

 * filename - regular expression to select embedded files
 * report - imixs-report definition to convert the xml into a Imixs WorkItem. 




## How to Install

To include the imixs-archive-documents plugins the following maven dependency can be added:


		<!-- Imixs-Documents / Tika Service -->	
		<dependency>
			<groupId>org.imixs.workflow</groupId>
			<artifactId>imixs-archive-documents</artifactId>
			<scope>compile</scope>
		</dependency>	
	
	