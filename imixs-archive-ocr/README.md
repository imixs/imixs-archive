# Imixs-Archive-OCR

*Imixs-Archive-OCR* is a sub-project of Imixs-Archive. The project provides methods to extract textual information from documents
attached to a Workitem. The text content of attachmets is either extracted by the PDFBox API or by optical character recognition (OCR). This text content is stored in the $file attribute 'text' and can be use for further processing or to search for document content.


## OCR 

The *Optical character recognition (OCR)* is based on the [Apache Project 'Tika'](https://tika.apache.org/). The textual information for each attachment is stored as a custom attribute named 'text' into the FileData object. This information can be used by applications to analyse, verify or process textual information of any document type. The OCR processing is implemented by the *TikaDocumentService*.

### The OCRService

The *OCRService* extracts the textual information from file attachments. The service calls the PDFBox API,  or in case a PDF document does not contain text information the Tika Rest API to extract the text information of a file. The following environment variables are mandatory:
 
  * TIKA\_SERVICE\_ENDPONT - defines the Rest API end-point of the tika server.
  * TIKA\_SERVICE\_MODE - if set to 'auto' the TikaDocumentService reacts on the CDI event 'BEFORE\_PROCESS' and extracts the data automatically. If set to 'model' the *TikaPlugin* or the *TikaAdapter* can be used in a BPMN model to activate the OCR processing

See also the [Docker Image Imixs/Tika](https://cloud.docker.com/u/imixs/repository/docker/imixs/tika) for further information


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




## How to Install

To include the imixs-archive-ocr service the following maven dependency can be added:


		<!-- Imixs-Archive OCRService -->	
		<dependency>
			<groupId>org.imixs.workflow</groupId>
			<artifactId>imixs-archive-ocr</artifactId>
			<scope>compile</scope>
		</dependency>	
	
	