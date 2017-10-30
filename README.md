# Imixs-Archive

Imixs-Archive is an open source project designed to provide a transparent and sustaining solution for long-term archiving of business data. In this context, business data means not only documents but also the comprehensible documentation of business processes.
Imixs-Archive is a sub-project of the Human-Centric Workflow engine [Imixs-Workflow](http://www.imixs.org), which provides a powerful platform for the description and execution of business processes.

Imixs-Archive consists of independent modules which allow the transparent integration of any kind of archive or big data platform. 
One of the big data platforms supported by Imixs-Archive is [Apache Hadoop](http://hadoop.apache.org/).


## The API

The [sub-module Imixs-Archive-API](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-api) provides core functionality and interfaces to exchange business data with any kind of archive or big data platform. This API is platform independent and based on the Imixs-Workflow API.

[Imixs-Archive-API](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-api)
  
## The UI

The [sub-module Imixs-Archive-UI](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-ui) provides ui functionality to be integrated into enterprise application (see the Imixs-Office-Workflow project).

[Imixs-Archive-UI](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-ui)
  
  
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

