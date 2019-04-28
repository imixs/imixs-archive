# Imixs-Documents

The Imixs-Documents is a sub-project of Imixs-Archive. The project provides methods to extract textual information - including Optical character recognition - from attached documents during the processing phase. This information can be used for further processing or to search documents


## OCR and Fulltext Search

Imixs-Document provides a feature to search documents. This includes also a _Optical character recognition (OCR)_ for documents and images. The textual information extracted by the module is stored together with the file data information in the custom attribute 'content'. To search for the extracted data the information is also stored in the item named 'dms' which can be added to the fulltext-field-list.  

### Apache Tika

The text extraction and optical character recognition is based on the [Apache Project 'Tika'](https://tika.apache.org/). The extraction process performed by calling the Tika Rest API provided by the [Tika Server module]. See also the [Docker Image Imixs/Tika](https://cloud.docker.com/u/imixs/repository/docker/imixs/tika).

### TikaDocumentHandler

The TikaDocumentHandler extracts the textual information from document attachments. The handler sends each new attached document
 to an instance of an Apache Tika Server to get the file content. The following environment variable is mandatory:
 
  * TIKA\_SERVICE\_ENDPONT - defines the Rest API end-point of the tika server.
  * TIKA\_SERVICE\_MODE - if set to 'auto' the DocumentHandler reacts on the CDI event 'BEFORE\_PROCESS' and extracts the data automatically
  
See also the docker project [Imixs/tika](https://github.com/imixs/imixs-docker/tree/master/tika).



### TikaPlugin

The TikaPlugin class _org.imixs.workflow.documents.TikaPlugin_ can be used as an alternative for the tika service mode 'auto'. The pugin extract  textual information from document attachments based on the model configuration. You need to add the plugin to your model to activate it. 

	org.imixs.workflow.documents.TikaPlugin


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
		