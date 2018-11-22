# Imixs-Archive

_Imixs-Archive_ is an open source project designed to provide a transparent and sustaining solution for long-term audit-proof archiving of business data. In this context, business data means not only documents but also the comprehensible documentation of business processes.
Imixs-Archive is a sub-project of the Human-Centric Workflow engine [Imixs-Workflow](http://www.imixs.org), which provides a powerful platform to describe and execute business processes.

## Architecture

The project pursues the following main objectives:

 - Archive business data in a highly available Big Data Platform
 - Retrieve archived business data 
 - Analyze archived business data 
 - Data recovery after a data loss 
 
 
### Apache Cassandra  

Imixs-Archive is based based on [Apache Cassandra](http://cassandra.apache.org/) which offers a highly available Big Data Platform .
_"Imixs-Archive"_  provides an optimized data model to archive process instances into a Cassandra cluster.

[Apache Cassandra](http://cassandra.apache.org/)

 
### The Core API

The [sub-module Imixs-Archive-API](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-api) provides core functionality and interfaces to exchange business data with any kind of archive or big data platform. 
This API is platform independent and based on the Imixs-Workflow API. The API is bundled with an Imixs-Workflow instance. 

[Imixs-Archive-API](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-api)

### The Microservice

The _"Imixs-Archive-Service"_ project provides an MicrosService to store the data of Imixs-Workflow into a highly available Big Data Platform based on [Apache Cassandra](http://cassandra.apache.org/). The service runs  typically in a Cassandra Cluster consisting of serveral data nodes. The Imixs-Archive Service is build on Jakarta EE and automatically pulls the data from an Imixs Workflow Instance into the Cassandra Cluster based on a scheduler. The archive process includes all business process data and documents. 
 
The Imixs-Archive Service can also be used to retrieve a single archived process instance or to restore the entire archive. Restoring an entire archive can be used, for example, after a data loss or a Disaster recovery of an Imixs Workflow instance. 

[Imixs-Archive-Service](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-service)




## What is audit-proof archiving?
Audit-proof archiving means that documents or business information can be searched, traced back to their origin, and stored securely against tampering. From an organizational perspective, a procedure for audit-proof archiving must be transparent for 
all members within an organization. The Imixs-Archive API combines these aspects together with the [Imixs-Workflow engine](http://www.imixs.org)  into a powerful and flexible business process management platform.
 
### Searching Information
Imixs-Workflow provides the foundation for creating, editing, and searching business data  on intelligible defined process descriptions. Each process instance, controlled by the Imixs-Workflow engine, can be searched through a full-text index. A query can be structured - according to predefined attributes, as well as unstructured - based on search terms in a full-text search.

### Tracing Back Information to its Origin
Any information controlled by Imixs-Workflow contains a detailed and consistently log from its creation to its archiving.  This protocol can be read by both, IT systems and humans. Business information is archived in an open XML format which is independent from technical platform and storage solutions.  
 
### Protecting Information from Tampering
Based on a BPMN 2.0 process model, business data can be protected from changes at any time by well defined business rules within a business process.
Imixs-Workflow supports a fine grained access control on the level of a single process instance. This concept allows protecting data from tampering. In addition, Imixs-Archive supports a [snapshot concept](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-api) that automatically stores immutable business data protected from any further manipulation.



 