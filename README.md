# Imixs-Archive

Imixs-Archive is an open source project designed to provide a transparent and sustaining solution for long-term archiving of business data. In this context, business data means not only documents but also the comprehensible documentation of business processes.
Imixs-Archive is a sub-project of the Human-Centric Workflow engine [Imixs-Workflow](http://www.imixs.org), which provides a powerful platform for the description and execution of business processes.

Imixs-Archive provides an API for a transparent data exchange with any kind of archive system. One of these systems supported by Imixs-Archive is [Apache Hadoop](http://hadoop.apache.org/).


## The API

The sub-module Imixs-Archive-API provides the core functionality and interfaces to generate, store and retrieve business data into an archive system. This api is platform independent and based on the Imixs-Workflow API.  

## Hadoop 

The sub-module Imixs-Archive-Hadoop provides an adapter for the [Apache Hadoop Filesystem (hdfs)](http://hadoop.apache.org/). The adapter is based on HttpFS which can be used to transfer data between different versions of Hadoop clusters. HttpFS allows to access data in clusterd HDFS behind of a firewall which enables a restricted and secured archive architecture. 
As HttpFS is based on REST, this component does not have any additional hadoop libraries. In addition HttpFS has built-in security supporting Hadoop pseudo authentication, HTTP SPNEGO Kerberos and other pluggable authentication mechanisms to be used depending on the target architecture. 


## Docker

The [Imixs-Docker/hadoop project](https://github.com/imixs/imixs-docker/tree/master/hadoop) provides a Docker image to run Haddop in a Docker container. This container can be used to test Imixs-Archive in combination with a Hadoop single-node-cluster.
**NOTE:** The Imixs-Docker/hadoop container is for test purpose only. The container should only run in a system environment protected from external access. 


