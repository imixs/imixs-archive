# Imixs-Archive-OCR

*Imixs-Archive-OCR* is a sub-project of Imixs-Archive. The project is decoupled form the Imixs-Workflow Engine and provides a service component to extract textual information from documents attached to a Workitem. The text content of attachments is either extracted by the PDFBox API or by optical character recognition (OCR). This text content is stored in the $file attribute 'text' and can be use for further processing or to search for document content.


## OCR 

The *Optical character recognition (OCR)* is based on the [Apache Project 'Tika'](https://tika.apache.org/). The textual information for each attachment is stored as a custom attribute named 'text' into the FileData object. This information can be used by applications to analyse, verify or process textual information of any document type. The OCR processing is implemented by the *TikaDocumentService*.

### The OCRService

The *OCRService* extracts the textual information from file attachments calling the Tika Rest API Service endpoint. The following environment variables are mandatory:
 
  * TIKA\_SERVICE\_ENDPONT - defines the Rest API end-point of the tika server.
  * TIKA\_SERVICE\_MODE - if set to 'auto' the TikaDocumentService reacts on the CDI event 'BEFORE\_PROCESS' and extracts the data automatically. If set to 'model' the *TikaPlugin* or the *TikaAdapter* can be used in a BPMN model to activate the OCR processing

See also the [Docker Image Imixs/Tika](https://cloud.docker.com/u/imixs/repository/docker/imixs/tika) for further information


### PDF Modes

For PDF files, the service can use the PDFBox API to extract textual information. This is faster than a OCR scan. In case a PDF document does not contain text information the Tika Rest API is used to extract the text information from a pdf file.

With the optional environment variable OCR\_PDF\_MODE the behavior how text is extracted from a PDF file can be controlled:

  * TEXT_ONLY -  OCR processing is disabled and text is extracted only from PDF files using the PDFBox API. If a pdf file does not contain text data no text will be extracted!
  * TEXT_AND_OCR - OCR processing is only performed in case no text data can be extracted from a given PDF file (default)
  * OCR_ONLY - pdf files will always be OCR scanned even if the pdf file contains text data.  

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


## How to Install

To include the imixs-archive-ocr service the following maven dependency can be added:


		<!-- Imixs-Archive OCRService -->	
		<dependency>
			<groupId>org.imixs.workflow</groupId>
			<artifactId>imixs-archive-ocr</artifactId>
			<scope>compile</scope>
		</dependency>	
	
	