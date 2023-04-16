# Imixs-Archive
[![Java CI with Maven](https://github.com/imixs/imixs-archive/actions/workflows/maven.yml/badge.svg)](https://github.com/imixs/imixs-archive/actions/workflows/maven.yml)
[![Join a discussion](https://img.shields.io/badge/discuss-on%20github-4CB697)](https://github.com/imixs/imixs-workflow/discussions)
[![License](https://img.shields.io/badge/license-GPL-blue.svg)](https://github.com/imixs/imixs-archive/blob/master/LICENSE)

*Imixs-Archive* is an open source project designed to provide a transparent and sustaining solution for document management and long-term audit-proof archiving of business data. In this context, business data means not only documents but also the comprehensible documentation of business processes.
*Imixs-Archive* includes various modules and services integrated into the [Imixs-Workflow project](https://www.imixs.org). The archive core technology is based on [Apache Cassandra](http://cassandra.apache.org/) which offers a highly available Big Data Platform.


<img src="https://github.com/imixs/imixs-archive/raw/master/docs/imixs-archive-architecture.png"/>



## Architecture

The project pursues the following main objectives:

 - Archive business data in a highly available Big Data Platform
 - Optical Character Recognition (OCR) and text extraction
 - Input and Output management of Documents
 - Digital Signatures
 - Retrieve archived business data 
 - Big Data Analysis
 - Data recovery after a data loss 
 

 
### Imixs-Archive-API

The sub-module *Imixs-Archive-API* provides the core API to store business data into snapshots to be archived by the Imixs-Archive Service. This Core API is platform independent and based on the Imixs-Workflow API. The API can be bundled with an Imixs-Workflow instance. 

[Imixs-Archive-API](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-api)

### Imixs-Archive-Service

The *Imixs-Archive-Service* provides an independent microservice archiving all data from a Imixs-Workflow instance into an [Apache Cassandra Cluster](http://cassandra.apache.org/). The *Imixs-Archive Service* provides methods to restore a single process instance or to export or to restore the entire archive. Restoring an entire archive can be used, for example, after a data loss or a Disaster recovery. 

[Imixs-Archive-Service](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-service)


### Imixs-Archive-Documents

*Imixs-Archive-Documents* provides Plugins and Adapter classes to extract textual information from attached documents - including Optical character recognition - during the processing life cycle of a workitem. This information can be used for further processing or to search for documents.

[Imixs-Archive-Documents](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-documents)

The `OCRDocumentService` provides a service component to extract textual information from documents attached to a Workitem. The text extraction is based on [Apache Tika](https://tika.apache.org/). 

[Imixs-Archive-OCR](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-ocr)

### Imixs-Archive-Importer

*Imixs-Archive-Importer* provides a generic import service to be used to import documents form various external sources like a FTP server or a IMAP account. 

[Imixs-Archive-Importer](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-importer)

The `CSVImportService` also provides a generic function to import datasets from CSV files located on a FTP server. 

# What is audit-proof archiving?
Audit-proof archiving means that documents or business information can be searched, traced back to their origin, and stored securely against tampering. From an organizational perspective, a procedure for audit-proof archiving must be transparent for 
all members within an organization. The Imixs-Archive API combines these aspects together with the [Imixs-Workflow engine](http://www.imixs.org)  into a powerful and flexible business process management platform.
 
## Searching Information
Imixs-Workflow provides the foundation for creating, editing, and searching business data  on intelligible defined process descriptions. Each process instance, controlled by the Imixs-Workflow engine, can be searched through a full-text index. A query can be structured - according to predefined attributes, as well as unstructured - based on search terms in a full-text search.

## Tracing Back Information to its Origin
Any information controlled by Imixs-Workflow contains a detailed and consistently log from its creation to its archiving.  This protocol can be read by both, IT systems and humans. Business information is archived in an open XML format which is independent from technical platform and storage solutions.  
 
## Protecting Information from Tampering
Based on a BPMN 2.0 process model, business data can be protected from changes at any time by well defined business rules within a business process.
Imixs-Workflow supports a fine grained access control on the level of a single process instance. This concept allows protecting data from tampering. In addition, Imixs-Archive supports a [snapshot concept](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-api) that automatically stores immutable business data protected from any further manipulation.

## Signing Information
Based on X509 Certificates documents (PDF) can be signed to guarantee their authenticity. [Imixs-Archive-Signature](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-signature) provides method to sign documents. This service includes a certificate authority (CA) integrated into the Imixs-Archive platform.  


 