# Imixs-Archive

Imixs-Archive is an open source project designed to provide a transparent and sustaining solution for long-term audit-proof archiving of business data. In this context, business data means not only documents but also the comprehensible documentation of business processes.
Imixs-Archive is a sub-project of the Human-Centric Workflow engine [Imixs-Workflow](http://www.imixs.org), which provides a powerful platform for the description and execution of business processes.


## What is audit-proof archiving?
Audit-proof archiving means that documents or business information can be searched, traced back to their origin, and stored securely against tampering. From an organizational perspective, a procedure for audit-proof archiving must be transparent for 
all members within an organization. The Imixs-Archive API combines these aspects together with the [Imixs-Workflow engine](http://www.imixs.org)  into a powerful and flexible business process management platform.
 
### Searching Information
Imixs-Workflow provides the foundation for creating, editing, and searching business data  on intelligible defined process descriptions. Each process instance, controlled by the Imixs-Workflow engine, can be searched through a full-text index. A query can be structured - according to predefined attributes, as well as unstructured - based on search terms in a full-text search.

### Tracing Back Information to its Origin
Any information controlled by Imixs-Workflow contains a detailed and consistently log from its creation to its archiving.  This protocol can be read by both, IT systems and humans. Business information can be stored in an open XML format which is independent from technical platform and storage solutions.  
 
### Protecting Information from Tampering
Based on a BPMN 2.0 process model, business data can be protected from changes at any time by well defined business rules within a business process.
Imixs-Workflow supports a fine grained access control on the level of a single process instance. This concept allows protecting data from tampering. In addition, Imixs-Archive supports a [snapshot concept](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-api) that automatically stores immutable business data protected from any further manipulation.


Imixs-Archive consists of independent modules which allow the transparent integration of any kind of archive or big data platform. 
One of the big data platforms supported by Imixs-Archive is [Apache Hadoop](http://hadoop.apache.org/).


## The Core API

The [sub-module Imixs-Archive-API](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-api) provides core functionality and interfaces to exchange business data with any kind of archive or big data platform. This API is platform independent and based on the Imixs-Workflow API.

[Imixs-Archive-API](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-api)

  
## Hadoop 

The [sub-module Imixs-Archive-Hadoop](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-hadoop) provides an adapter for the [Apache Hadoop Filesystem (hdfs)](http://hadoop.apache.org/). The adapter is based on the HttpFS Rest API. HttpFS can be used to transfer data in a restfull way between different versions of Hadoop clusters. HttpFS allows to access data in clustered HDFS behind of a firewall which enables a restricted and secured archive architecture. 
As HttpFS is based on REST, this component does not need any additional hadoop libraries. In addition HttpFS has built-in security supporting Hadoop pseudo authentication, HTTP SPNEGO Kerberos and other pluggable authentication mechanisms to be used depending on the target architecture. 

[Imixs-Archive-Hadoop](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-hadoop)


## OCR

The [sub-module Imixs-Archive-OCR](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-ocr) provides a solution for OCR scans on documents which are part of Imxis-Archive. Imixs-Archive-OCR includes a fulltext search based on [Apache Lucene](http://lucene.apache.org/). This module can be combined with any Imixs-Workflow business application as also with standalone applications. 

[Imixs-Archive-OCR](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-ocr)


## The Test Environment

Imixs-Archive provides a test environment based on Docker. The Test Environment consists of the following docker containers:

- PostgreSQL Database
- Hadoop Single Node Cluster
- Imixs-Office-Workflow

The Imixs-Archive Test Environment is for test purpose only. The Docker containers should only run in a system environment protected from external access. 

[Imixs-Archive-Test](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-test)

