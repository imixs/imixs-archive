# Imixs-Archive-Importer

The Imixs-Archive Importer module provides a generic import service to be used to import documents form various external sources like a FTP server or a IMAP account. 

The Importer service is based on the Imixs-Scheduler API to perform imports on a scheduled base. The scheduler sends CDI events which can be consumed specific importer implementations. In this way the Importer service is highly extensible. The following import sources are supported:

 - FTP - import form a ftp server 
 - IMAP - import form an email box via IMAP
 
 
# Scheduling

The Imixs DocumentImportScheduler uses a calendar-based syntax for scheduling based on the EJB 3.1 Timer Service specification. The syntax takes its roots from the Unix cron utility. This is an example to run the scheduler all 15 minutes


	minute=*/15
	hour=*


## The Source Objects

Each source object provides at least the following properties to be used by a observer implementation:

 - server - server address including protocol
 - port - optional server port
 - user - optional user id to access the server
 - password
 - task - the task id a new workitem is assigned to
 - event - the event id a new workitem is processed 
 - workflowgroup - the workflow group the new workitem should be assigend to
 - modelversion - optional modelversion the new workitem is assigend to
 - selector - an optional selector to specify the source (e.g. a path on a ftp server)
 
 
 ### Options
 
 A source object can provide additional options provided in the item 'options'. The DocumentImportService provides a a convenient method to get a Properties object for all options:
 
	// get properties form source object
	Properties properties = documentImportService.getOptionsProperties(ItemCollection source);
    

 
## The Web UI

The Importer adapter provides a JSF Web UI component to be used for jsf applications. This ui component is optional and can be implement in customized way. 

<img src="./doc/images/webui-01.png" size="600px"/>

### The DocumentImportController

The CDI Bean DocumentImportController is used to display and select data sources. The controller can be extend for individual source definitions. 

<img src="./doc/images/webui-02.png" size="600px" />




# FTP Importer

The FTP Importer Service can be used to import documents form a FTP server. 


# Mail Importer

The Mail Importer Service is used to import documents form a IMAP server. This module provides also services to convert Java Mail Message objects into HTML or PDF.

## Detachment Mode

The Mail Importer Service provides the ability to detach files from an mail message object. The behaviour can be configured by the option property named "DETACH_MODE". The following detach modes are supported:

 - PDF - only PDF files attached will be detached together with the origin .eml file to the workitem. This is the default mode.
 - ALL - all files will be detached and the email content will be attached as a HTML file to the workitem.
 - NONE - no files will be detached and the origin email object will be attached as a .eml file to the workitem.

To set the detachment mode add the DETACH_MODE to the option list of the IMAP source:

	DETACH_MODE=ALL


## The Archive Folder

After the Mail Importer has imported a single message successfully the message will be moved into a IMAP archive folder named 'imixs-archive'. This folder will be automatically created if it does not yet exist on the IMAP server.
You can change the name of the archive folder by setting the option 'ARVICE_FOLDER'

	ARCHIVE_FOLDER=Invoice-Archive

	
	
## Gotenberg HTML PDF Converter

[Gotenberg](https://thecodingmachine.github.io/gotenberg/) is a Docker-powered stateless API for converting HTML, Markdown and Office documents to PDF. This service can be used to convert a Mail into PDF. To activate this feature add the following options to the import source:

	DETACH_MODE=ALL
	GOTENBERG_SERVICE=http://gotenberg:3000



