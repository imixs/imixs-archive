# Imixs-Archive-OCR

*Imixs-Archive-OCR* provides a service component to extract textual information from documents attached to a Workitem. The text extraction is based on [Apache Tika](https://tika.apache.org/). To use this module a Tika Server endpoint must exist. 
You can run a Tika Server with the [official Docker image](https://hub.docker.com/r/apache/tika):

	$ docker run -d -p 9998:9998 apache/tika:1.24.1-full

Tika extracts text from over a thousand different file types including PDF and office documents and supports *Optical character recognition (OCR)* based on the [Tesseract project](https://github.com/tesseract-ocr/tesseract). The text content extracted by this service is stored in the $file attribute 'text' and can be used for further  analysis, verifying or processing within a business process. The project is decoupled form the Imixs-Workflow Engine so that it can be used independently in other projects too. 


### The OCRService

The *OCRService* extracts the textual information from file attachments calling the Tika Rest API Service endpoint. The following environment variables are supported:
 
  * OCR\_SERVICE\_ENDPOINT - defines the Rest API end-point of the Tika server (mandetory).
  * OCR\_STRATEGY - Which strategy to use for OCR (AUTO|NO_OCR|OCR_AND_TEXT_EXTRACTION|OCR_ONLY) 

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



## How to Install

To include the imixs-archive-ocr service the following maven dependency can be added:


		<!-- Imixs-Archive OCRService -->	
		<dependency>
			<groupId>org.imixs.workflow</groupId>
			<artifactId>imixs-archive-ocr</artifactId>
			<scope>compile</scope>
		</dependency>	
	
	