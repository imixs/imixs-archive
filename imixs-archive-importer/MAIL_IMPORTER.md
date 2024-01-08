# The Mail IMAP Importer

The CDI Bean `IMAPIImporterService` is a concrete implementation to import documents form a IMAP server. This module provides also services to convert Java Mail Message objects into HTML or PDF.

## The Import Folder

With the 'Selector' attribute you can define the IMAP folder from which the Mail Importer imports mails. If no selector is set the default IMAP Folder 'INBOX' is scanned. This is typically the default folder for most IMAP servers. 

## The Archive Folder

After the `IMAPIImporterService` has imported a single message successfully the message will be moved into a IMAP archive folder named 'imixs-archive'. This folder will be automatically created if it does not yet exist on the IMAP server.
You can change the name of the archive folder by setting the option `archive.folder`

	archive.folder=Invoice-Archive


## Filtering Mails by subject using regular expressions

It is possible to filter emails by a subject. Therefor a regular expression can be added by the option property named `subejct.regex` - e.g.:

	subject.regex=(order|offer|invoice*)

In this example only messages with the text 'order', 'offer' or starting with 'invoice' will be imported. 

## Detachment Mode

The *IMAPIImporterService* provides the ability to detach files from an mail message object. The behaviour can be configured by the option property named `detach.mode`. The following detach modes are supported:

 - PDF - only PDF files attached will be detached together with the origin .eml file to the workitem. This is the default mode.
 - ALL - all files will be detached and the email content will be attached as a HTML file to the workitem.
 - NONE - no files will be detached and the origin email object will be attached as a .eml file to the workitem.

To set the detachment mode add the option to the IMAP source:

	detach.mode=ALL

## Preserve Origin Message

In case of detach.mode=ALL, the option 'preserve.origin' defines if the origin email will be attached. 

	preserve.origin=false

If the option is set to false, the .eml file will not be attached. The default value is 'true'. 

**Note:** In case of the detach.mode = 'PDF' or 'NONE' the origin mail file will always be attached.




## Custom Mail Options

The *IMAPIImporterService* connects to an mail server via IMAPS per default. IMAPS (IMAP over SSL/TLS) is assigned the port number 993. You can overwrite the default protocol by the property "mail.store.protocol" via custom mail options in the document source options. In this way it is also possible to set additional custom mail options concerning various aspects of your mail/imap server.  See the following example, setting some extra java mail options:

	mail.store.protocol=imap
	mail.imap.ssl.enable=true
	mail.imap.port=587
	mail.imap.ssl.trust=*
	
You can find a list of all IMAP settings [here](https://www.tutorialspoint.com/javamail_api/javamail_api_imap_servers.htm)

## Authenticators

Connecting to a IMAP Message Store the IMAP Importer uses IMAPAuthentictor classes to access a mailbox using a specific authentication method.
The IMAPAuhtenticator can be defined by the optional Option 	`imap.authenticator`. If no authenticator is defined, the Service will default o the `org.imixs.archive.importer.mail.IMAPBasicAuthenticator` implementation which uses a BASIC authentication method. 

### IMAP Outlook Authenticator

For Outlook365 the IMAP Authenticator `org.imixs.archive.importer.mail.IMAPOutlookAuthenticator` can be used to authenticate with the OAuth2 method. The Authenticator expects the following additional Mail Options:

 * microsoft.tenantid - specifies the Microsoft Azure Tenant ID
 * microsoft.clientid - specifies the Microsoft Client ID (the Client Principal accessing the Mail box)

**Note:** In case of using the  IMAPOutlookAuthenticator the server name is ignored. The Server names to generate a OAuth Token and to open the Message Store are resolved internally by the Authenticator implementation. 

## Debug Mode

You can activate a debug mode to get more insides of the import processing by setting the option `debug`

	debug=true

## Gotenberg HTML PDF Converter

[Gotenberg](https://thecodingmachine.github.io/gotenberg/) is a Docker-powered stateless API for converting HTML, Markdown and Office documents to PDF. This service can be used to convert a Mail into a PDF document. This option applies only to the option 'detach.mode=ALL'.

To activate this feature add the following options to the import source:

	detach.mode=ALL
	gotenberg.service=http://gotenberg:3000

A Gotenberg service can be started as a Docker Container:

	  gotenberg: 
	    image: thecodingmachine/gotenberg:6
	    ports:
	      - "3000:3000" 




