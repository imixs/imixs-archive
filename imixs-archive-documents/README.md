# Imixs-Archive-Documents

*Imixs-Archive-Document* is a sub-project of Imixs-Archive. The project provides Plugins and Adapter classes
 to extract textual information from attached documents  - including Optical character recognition -  during the processing life cycle
 of a workitem. This information can be used for further processing or to search for documents. 

## OCR 

The *Optical character recognition (OCR)* is based on the [Apache Project 'Tika'](https://tika.apache.org/). The textual information for each attachment is stored as a custom attribute named 'text' into the FileData object. This information can be used by applications to analyse, verify or process textual information of any document type. The OCR processing is implemented by the *TikaDocumentService*.

### The OCRDocumentService

The *OCRDocumentService* extracts the textual information from file attachments during the processing life cycle. The service calls the Imixs-Archvie OCRService to extract the text information of a file. The following environment variables are mandatory:
 
  * TIKA\_SERVICE\_ENDPONT - defines the Rest API end-point of the tika server.
  * TIKA\_SERVICE\_MODE - if set to 'auto' the TikaDocumentService reacts on the CDI event 'BEFORE\_PROCESS' and extracts the data automatically. If set to 'model' the *TikaPlugin* or the *TikaAdapter* can be used in a BPMN model to activate the OCR processing

See also the [Imixs-Archive OCR project](../imixs-archive-ocr/) for further information about the OCR service. 
  
### Auto Processing

OCR processing can be automatically activated for all new attached documents by setting the environment variable *TIKA_SERVICE_MODE* to 'auto'.  If the variable is set to 'model' the *TikaPlugin* or the *TikaAdapter* can be used in a BPMN model to activate the OCR processing in specific situations only. 


### The OCRDocumentPlugin

The TikaPlugin class *org.imixs.archive.documents.OCRDocumentPlugin* can be used as an alternative for the tika service mode 'auto'. The pugin extract  textual information from document attachments based on the model configuration. You need to add the plugin to your model to activate it. 

	org.imixs.archive.documents.OCRDocumentPlugin

The environment variable *TIKA_SERVICE_MODE* must be set to 'model'.  

### The OCRDocumentAdapter

The Adapter class *org.imixs.archive.documents.OCRDocumentAdapter* is a signal adapter which can be bound on a specific BPMN event element.

	org.imixs.archive.documents.OCRDocumentAdapter

The TikaAdapter allows a more fine grained configuration of OCR processing. The environment variable *TIKA_SERVICE_MODE* must be set to 'model'. 

### OCR Tika Options

Both, the *OCRDocumentPlugin* as also the *OCRDocumentAdapter* can be configured on the BPMN Event level with optional Tika options. The tika options can be configured in the workflow result of the BPMN event element with the tag '*tika*' and the name '*options*'. See the following example:

	<!-- Tika Options -->
	<tika name="options">X-Tika-PDFocrStrategy=OCR_AND_TEXT_EXTRACTION</tika>
	<tika name="options">X-Tika-PDFOcrImageType=RGB</tika>
	<tika name="options">X-Tika-PDFOcrDPI=400</tika>

In this example configuration the OCR processing will be started with 3 additional tika options. For more details about the OCR configuration see the [Imixs-Archive-OCR project](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-ocr).


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
	
	