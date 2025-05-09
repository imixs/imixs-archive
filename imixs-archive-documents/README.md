# Imixs-Archive-Documents

*Imixs-Archive-Document* is a sub-project of Imixs-Archive. The project provides Services, Plugins and Adapter classes
 to extract textual information from attached documents during the processing life cycle of a workitem. 
 This includes also 0ptical character recognition (OCR). 
 The extracted textual information can be used for further processing or to search for documents. 


## Text Extraction

The text extraction is mainly based on the [Apache Tika Project](https://tika.apache.org/). The text extraction can be controlled based on a BPMN model
through the corresponding adapter or plug-in class. For a more general and model independent text extraction the OCRDocumentService can be used. 

The textual information for each attachment is stored as a custom attribute named 'text' into the FileData object of a workitem. This information can be used by applications to analyse, verify or process textual information of any document type. 

The following environment variable is mandatory:
 
  * OCR\_SERVICE\_ENDPONT - defines the Rest API end-point of an Apache Tika  instance.


### The OCRDocumentAdapter

The Adapter class *org.imixs.archive.documents.OCRDocumentAdapter* is a signal adapter which can be bound on a specific BPMN event element.

	org.imixs.archive.documents.OCRDocumentAdapter

The TikaAdapter allows a more fine grained configuration of OCR processing. The environment variable *TIKA_SERVICE_MODE* must be set to 'model'. 


### The OCRDocumentPlugin

The TikaPlugin class *org.imixs.archive.documents.OCRDocumentPlugin* can be used as an alternative for the tika service mode 'auto'. The pugin extract  textual information from document attachments based on the model configuration. You need to add the plugin to your model to activate it. 

	org.imixs.archive.documents.OCRDocumentPlugin

The environment variable *TIKA_SERVICE_MODE* must be set to 'model'.  


### Configuration

Both, the *OCRDocumentPlugin* as also the *OCRDocumentAdapter* can be configured on the BPMN Event level with optional Tika options. The tika options can be configured in the workflow result of the BPMN event element with the tag '*tika*' and the name '*options*'. See the following example:

	<!-- Tika Options -->
	<tika name="options">X-Tika-PDFocrStrategy=OCR_AND_TEXT_EXTRACTION</tika>
	<tika name="options">X-Tika-PDFOcrImageType=RGB</tika>
	<tika name="options">X-Tika-PDFOcrDPI=72</tika>
	<tika name="options">X-Tika-OCRLanguage=eng+deu</tika>
	<tika name="filepattern">(PDF|pdf)$</tika>
	<tika name="maxpdfpages">1</tika>
	

In this example configuration the OCR processing will be started with 4 additional tika options. 

 - X-Tika-PDFOcrImageType=RGB  - set color mode
 - X-Tika-PDFOcrDPI=72     - set DPI to 72
 - X-Tika-OCRLanguage=deu  - set OCR language to german


#### Overriding the configured language as part of your request

Different requests may need processing using different language models. These can be specified for specific requests using the X-Tika-OCRLanguage custom header. An example of this is shown below:

	X-Tika-OCRLanguage=deu

Or for multiple languages:

	X-Tika-OCRLanguage: eng+fra"


For more details about the OCR configuration see the section 'OCR' below.


#### FilePattern Regex

You can provide an optional filepattern as a regular expression to filter attachments to be parsed

Example - parse PDF files only:

	<tika name="filepattern">(PDF|pdf)$</tika>

#### Max Pages of PDF Documents

With the optional parameter `maxpdfpages` you can controll how many pages of a PDF document will be scanned. This optional parameter can be used to reduce the size of very time and CPU intensive scan processing of the Tika service. For example you can set the param to 1 to onyl scann the first page of a PDF document

	<tika name="maxpdfpages">1</tika>

### The OCRDocumentService

The *OCRDocumentService* is a general service to extract the textual information from file attachments during the processing life cycle independent form a BPMN model. The TikaDocumentService reacts on the CDI event 'BEFORE\_PROCESS' and extracts the data automatically. 

The environment variable *TIKA_SERVICE_MODE* must be set to 'auto'. 
If set to 'model' the *TikaPlugin* or the *TikaAdapter* must be used in a BPMN model to activate the text extraction.


## OCR 

The *Optical character recognition (OCR)* is based on the [Apache Project 'Tika'](https://tika.apache.org/). 
Tika extracts text from over a thousand different file types including PDF and office documents and supports *Optical character recognition (OCR)* based on the [Tesseract project](https://github.com/tesseract-ocr/tesseract).

To run a Tika Server with Docker, the [official Docker image](https://hub.docker.com/r/apache/tika) can be used:

	$ docker run -d -p 9998:9998 apache/tika:1.24.1-full


The *TikaService* EJB provides methods to extract textual information from documents attached to a Workitem. A valid Tika Server endpoint must exist.

### The TikaService

The *TikaService* extracts the textual information from file attachments calling the Tika Rest API Service endpoint. The following environment variables are supported:
 
  * OCR\_SERVICE\_ENDPOINT - defines the Rest API end-point of the Tika server (mandetory).
  * OCR\_SERVICE\_MAXFILESIZE - defines the maximum allowed file size in bytes (default is set to 5242880 = 5MB)
  * OCR\_STRATEGY - Which strategy to use for OCR (AUTO|NO_OCR|OCR_AND_TEXT_EXTRACTION|OCR_ONLY) 

The environment variable OCR\_SERVICE\_ENDPOINT  must point to a valid tika service.

With the OCR\_SERVICE\_MAXFILESIZE  you can define the maximum allowed file size to scan. If a file is very large, this can take a lot of memory an processing time. The default max size of 5MB is a good recommendation. 

With the optional environment variable OCR\_STRATEGY the behavior how text is extracted from a PDF file can be controlled:

**AUTO** 
<br />
The best OCR strategy is chosen by the Tika Server itself. This is the default setting.

**NO_OCR**
<br />
OCR processing is disabled and text is extracted only from PDF files including a raw text. If a pdf file does not contain raw text data no text will be extracted!

**OCR_ONLY**
<br />
PDF files will always be OCR scanned even if the pdf file contains text data.  

**OCR_AND_TEXT_EXTRACTION** 
<br />
OCR processing and raw text extraction is performed. Note: This may result is a duplication of text and the mode is not recommended. 

### Tika Options

Out of the box, Apache Tika will start with the default configuration. By providing additional config options
 you can specify a custom tika configuration to be used by the tika server.

For example to set the DPI mode call:

	@EJB
	TikaDocumentService tikaDocumentService;
	
	// define options
	List<String> options=new ArrayList<String>();
	options.add("X-Tika-PDFocrStrategy=OCR_AND_TEXT_EXTRACTION");
	options.add("X-Tika-PDFOcrImageType=RGB"); 	//  support colors 
	options.add("X-Tika-PDFOcrDPI=72");    			// set DPI
	options.add("X-Tika-OCRLanguage=eng"); 			// set english language	
	// start ocr 
	tikaDocumentService.extractText(workitem, "TEXT_AND_OCR", options)

**Note:** Options set by this method call overwrite the options defined in a tika config file. 

You have various options to configure the Tika server. Find details about how to configure imixs-tika [here](https://github.com/imixs/imixs-docker/tree/master/tika).	

 - https://cwiki.apache.org/confluence/display/TIKA/TikaServer
 - https://cwiki.apache.org/confluence/display/TIKA/TikaOCR
 - https://cwiki.apache.org/confluence/display/tika/PDFParser%20(Apache%20PDFBox)

#### Example

In this example configuration the OCR processing will be started with 4 additional tika options. 

 - X-Tika-PDFOcrImageType=RGB  - set color mode
 - X-Tika-PDFOcrDPI=72     - set DPI to 72
 - X-Tika-OCRLanguage=deu  - set OCR language to german


#### Overriding the configured language as part of your request

Different requests may need processing using different language models. These can be specified for specific requests using the X-Tika-OCRLanguage custom header. An example of this is shown below:

	X-Tika-OCRLanguage=deu

Or for multiple languages:

	X-Tika-OCRLanguage: eng+fra"


For more details about the OCR configuration see the [Imixs-Archive-OCR project](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-ocr).



### Searching Documents

All extracted textual information from attached documents is also searchable by the Imixs search index. The class *org.imixs.workflow.documents.DocumentIndexer* adds the ocr content for each file attachment into the search index.



## The e-Invoice Adapter

The Adapter  class `org.imixs.workflow.documents.EInvoiceAutoAdapter` can be used to extract data from an e-invoice document. The `EInvoiceAutoAdapter` class automatically resolve all relevant e-invoice fields. The following fields are supported:

| Item   			| Type  	| Description 				|
| ----------------- | --------- | ------------------------- | 
| invoice.number    | text  	| Invoice number 			|
| invoice.date    	| date  	| Invoice date 				| 
| invoice.duedate   | date  	| Invoice due date 			|
| invoice.total    	| double	| Invoice total grant amount| 
| invoice.total.net	| double	| Invoice total net amount	| 
| invoice.total.tx 	| double	| Invoice total tax amount	| 
| cdtr.name         | text  	| Creditor name				|

The implementation of the EInvoice Adapter classes is based on the [Imixs e-invoice project](https://github.com/imixs/e-invoice).

### The e-Invoice MetaAdapter

The Adapter class `org.imixs.workflow.documents.EInvoiceMetaAdapter` can detect and extract content from e-invoice documents in different formats. This implementation is based on XPath expressions and can resolve custom fields not supported by the `EInvoiceAutoAdapter` class. 

The detection outcome of the adapter is a new item named 'einvoice.type' with the detected type of the e-invoice format. E.g:

 - Factur-X/ZUGFeRD 2.0

The Adapter can be configured in an BPMN event to extract e-invoice data fields by the following entity definition 

```xml
	  <e-invoice name="ENTITY">
		<name>Item Name</name>
		<type>Item Type (text|date|double</type>
		<xpath>xPath expression</xpath>
	  </e-invoice>
```

**Example e-invoice configuration:** 

```xml
<e-invoice name="ENTITY">
  <name>invoice.number</name>
  <xpath>//rsm:CrossIndustryInvoice/rsm:ExchangedDocument/ram:ID</xpath>
</e-invoice>
<e-invoice name="ENTITY">
  <name>invoice.date</name>
  <type>date</type>
  <xpath>//rsm:ExchangedDocument/ram:IssueDateTime/udt:DateTimeString/text()</xpath>
</e-invoice>
<e-invoice name="ENTITY">
  <name>invoice.total</name>
  <type>double</type>
  <xpath>//ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:GrandTotalAmount</xpath>
</e-invoice>
<e-invoice name="ENTITY">
  <name>cdtr.name</name>
  <xpath>//ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:Name/text()</xpath>
</e-invoice>
```


If the type is not set the item value will be treated as a String. Possible types are 'double' and 'date'
If the document is not a e-invoice no items and also the einvoice.type field will be set.




## How to Install

To include the imixs-archive-documents plugins the following maven dependency can be added:

```xml
	<!-- Imixs-Documents / Tika Service -->	
	<dependency>
		<groupId>org.imixs.workflow</groupId>
		<artifactId>imixs-archive-documents</artifactId>
		<scope>compile</scope>
	</dependency>	
```


	