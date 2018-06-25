# Imixs-Archive-Cassandra
The _"Imixs-Archive-Cassandra"_ project provides an Archive Service to store the data of Imixs-Workflow into a highly available Big Data Platform based on [Apache Cassandra](http://cassandra.apache.org/). The project pursues the following main objectives:

 - Archive business data in a highly available Big Data Platform
 - Retrieve archived business data 
 - Analyze archived business data 
 - Data recovery after a data loss 

_Imixs-Archive-Cassandra_ runs  typically in a Cassandra Cluster consisting of serveral data nodes. The Imixs-Archive Service automatically pulls the data from an Imixs Workflow Instance into the archive. This includes all business process data and documents. 

The Imixs-Archive Service can be used to retrieve a single archived process instance or to restore the entire archive. Restoring an entire archive can be used, for example, after a data loss or a Disaster recovery of an Imixs Workflow instance. 


_"Imixs-Archive-Cassandra"_  provides an optimized data model to archive process instances into a Cassandra cluster. Read the section [datamodel](docs/DATAMODEL.md) for further information.


# The Imixs-Archive Microservice



The Imixs-Archive-Cassandra project provides an adapter for Apache Cassandra Big Data Platform.

The Imixs-Archive-Cassandra is connected with a Imixs-Workflow instance via the Imixs-Rest API and runs as a single microservice. 


# How to Setup a Test Environment

For local dev tests an Apache Cassandra test environment can be setup with Docker. 

Starting a Cassandra Docker container:

	$ docker run --name cassandra-dev -it -p 9042:9042 cassandra:latest

This container can now be used for junit tests as provided in the project. 

### cqlsh

To run a cqlsh (Cassandra Query Language Shell) against your Cassandra Dev container run:

	$ docker exec -it cassandra-dev cqlsh
	Connected to Test Cluster at 127.0.0.1:9042.
	[cqlsh 5.0.1 | Cassandra 3.11.1 | CQL spec 3.4.4 | Native protocol v4]
	Use HELP for help.
	cqlsh>



### Create a dev keyspace with cqlsh

	cqlsh> CREATE KEYSPACE imixs_dev WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};
	
	
### Create the Data Schema

	CREATE TABLE documents (
	document_id text,
	chunk_order int,
	chunk_id text,
	PRIMARY KEY (document_id, chunk_order))
	
	CREATE TABLE documents_data (
	chunk_id text, 
	chunk blob,
	PRIMARY KEY(chunk_id))
	
	CREATE TABLE documents_meta (
	modified date,
	document_id text,
	document_hash text,
	PRIMARY KEY(modified, document_id));
	
	
Select from document table:

	cqlsh> SELECT * FROM imixs_dev.document ;
	
	 id                                   | created                         | data | modified                        | type
	--------------------------------------+---------------------------------+------+---------------------------------+---------
	 77d02ca4-d96e-4052-9b59-b8ea6ce052aa | 2018-02-18 16:49:35.050000+0000 | null | 2018-02-18 16:49:35.050000+0000 | workitem
	
	(1 rows)



# Docker Support

The project includes a test environment based on a docker stack including the following components:

* Imixs-Archive-Cassandra - Web Front-End
* Cassandra - local cluster


## Build

To build the environment run the maven command:

	$ mvn clean install -Pdocker-build

To start the environment run:
	
	$ docker-compose up


