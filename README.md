# Imixs-Archive

Imixs-Archive is an open source project designed to provide a transparent and sustaining solution for long-term archiving of business data. In this context, business data means not only documents but also the comprehensible documentation of business processes.
Imixs-Archive is a sub-project of the Human-Centric Workflow engine [Imixs-Workflow](http://www.imixs.org), which provides a powerful platform for the description and execution of business processes.

Imixs-Archive provides an API for a transparent data exchange with any kind of archive system. One of these systems supported by Imixs-Archive is [Apache Hadoop](http://hadoop.apache.org/).


## The API

The [sub-module Imixs-Archive-API](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-api) provides the core functionality and interfaces to generate, store and retrieve business data into an archive system. This api is platform independent and based on the Imixs-Workflow API.  

## Hadoop 

The [sub-module Imixs-Archive-Hadoop](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-hadoop) provides an adapter for the [Apache Hadoop Filesystem (hdfs)](http://hadoop.apache.org/). The adapter is based on HttpFS which can be used to transfer data between different versions of Hadoop clusters. HttpFS allows to access data in clusterd HDFS behind of a firewall which enables a restricted and secured archive architecture. 
As HttpFS is based on REST, this component does not have any additional hadoop libraries. In addition HttpFS has built-in security supporting Hadoop pseudo authentication, HTTP SPNEGO Kerberos and other pluggable authentication mechanisms to be used depending on the target architecture. 


## OCR

The [sub-module Imixs-Archive-OCR](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-ocr) provides a solution for OCR scans on documents which are part of Imxis-Archive. Imixs-Archive-OCR includes a fulltext search based on [Apache Lucene](http://lucene.apache.org/). This module can be combined with any Imixs-Workflow business application as also with standalone applications. 


## The Test Environment

Imixs-Archive provides a test environment based on Docker. 
[Imixs-Archive-Test](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-test) is a Docker based test environment for Imixs-Archive. The Test Environment consists of the following docker containers:

- PostgreSQL Database
- Hadoop Single Node Cluster
- Imixs-Office-Workflow

The Imixs-Archive Test Environment is for test purpose only. The Docker containers should only run in a system environment protected from external access. 


