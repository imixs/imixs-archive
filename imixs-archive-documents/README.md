# Imixs-Documents

The Imixs-Documents is a sub-project of Imixs-Archive. The project provides methods to extract textual information - including Optical character recognition - from attached documents during the processing phase. This information can be used for further processing or to search documents


## OCR and Fulltext Search

Imixs-Document provides a feature to search documents. This includes also a *Optical character recognition (OCR)* for documents and images. The textual information extracted by the module is stored together with the file data information in the custom attribute 'content'. To search for the extracted data the information is also stored in the item named 'dms' which can be added to the fulltext-field-list.  

The text extraction and optical character recognition is based on the [Apache Project 'Tika'](https://tika.apache.org/). The extraction process performed by calling the Tika Rest API provided by the [Tika Server module]. See also the [Docker Image Imixs/Tika](https://cloud.docker.com/u/imixs/repository/docker/imixs/tika).

### The TikaDocumentService

The TikaDocumentService extracts the textual information from document attachments. The service sends each new attached document
 to an instance of an Apache Tika Server to get the file content. The following environment variables are mandatory:
 
  * TIKA\_SERVICE\_ENDPONT - defines the Rest API end-point of the tika server.
  * TIKA\_SERVICE\_MODE - if set to 'auto' the TikaDocumentService reacts on the CDI event 'BEFORE\_PROCESS' and extracts the data automatically. If set to 'model' the TikaPlugin or the TikaAdapter can be used in a BPMN model to activate the OCR processing
  
  
## Auto Processing

Documents can be automatically processed including OCR processing. To activate this feature the environment variable *TIKA_SERVICE_MODE* must be set to 'auto'.  
  

## The TikaPlugin

The TikaPlugin class _org.imixs.workflow.documents.TikaPlugin_ can be used as an alternative for the tika service mode 'auto'. The pugin extract  textual information from document attachments based on the model configuration. You need to add the plugin to your model to activate it. 

	org.imixs.workflow.documents.TikaPlugin

The environment variable *TIKA_SERVICE_MODE* must be set to 'model'.  

## The TikaAdapter

The TikaAdapter class _org.imixs.workflow.documents.TikaAdatper_ is a signal adapter to be bound on a specific BPMN event element.


	org.imixs.workflow.documents.TikaAdapter

The environment variable *TIKA_SERVICE_MODE* must be set to 'model'. 

#### The OCR MODE

With the optional environment variable  TIKA\_OCR\_MODE the OCR behavior can be controlled:

  * PDF_ONLY -  OCR scan is disabled, text is extracted from PDF files if available. All other files are ignored
  * OCR_ONLY - pdf and all other files are always OCR scanned.  
  * MIXED - OCR scan is only forced if no text data can be extracted from a given PDF file (default)

For further configuration see also the docker project [Imixs/tika](https://github.com/imixs/imixs-docker/tree/master/tika).

#### Tika Options

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

Find more details about how to configure the tika server [here](https://github.com/imixs/imixs-docker/tree/master/tika)


### Searching Documents

All extracted textual information from attached documents is searchable by the lucene search. 
To activate this feature, the item 'dms' must be included into the lucene fulltext index. 

	lucence.fulltextFieldList=.....,dms
	  


## PDF XML Plugin

The plugin class "_org.imixs.workflow.documents.parser.PDFXMLExtractorPlugin_" can be used to extract embedded XML Data from a PDF document and convert the data into a Imixs Workitem. For example the _ZUGFeRD_ defines a standard XML document for invoices. 

The plugin can be activated by the BPMN Model with the following result definition: 


	<item name="PDFXMLPlugin">
		<filename>*.xml</filename>
	    <report>myReport</report>
	</item>

The Item "PDFXMLExtractorPlugin" provides the following processing instructions for the PDFXMLPlugin:

 * filename - regular expression to select embedded files
 * report - imixs-report definition to convert the xml into a Imixs WorkItem. 




# How to Install

To include the imixs-archive-documents plugins the following maven dependency can be added:


		<!-- Imixs-Documents / Tika Service -->	
		<dependency>
			<groupId>org.imixs.workflow</groupId>
			<artifactId>imixs-archive-documents</artifactId>
			<scope>compile</scope>
		</dependency>	
		
		
# Configuration of OCR 

You have various configuration options on the Tika server. Find details about how to configure imixs-tika [here](https://github.com/imixs/imixs-docker/tree/master/tika).		