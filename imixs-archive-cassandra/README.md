# Imixs-Archive-Cassandra
The Imixs-Archive-Cassandra project provides an adapter for [Apache Cassandra](http://cassandra.apache.org/) Big Data Platform.



## Overview

The Imixs-Archive-Cassandra project provides a service to persist Imixs documents into a Cassandra keystore. Each keystore consists of one table storing the document data. 

	CREATE TABLE imixs_dev.document (
	    id text PRIMARY KEY,
	    type text,
	    created timestamp,
	    modified timestamp,
	    data text
	)

Each document is represented in XML in the column 'data'. 
This allows to read and extracted the data in a technology-neutral way. 

**Note:** The size of an XML representation of a Imixs document is only slightly different in size from the serialized map object. This is the reason why we do not store the document map in a serialized object format.  


The Imixs-Archive-Cassandra is connected with a Imixs-Workflow instance via the Imixs-Rest API and runs as a single microservice. 


## Test Environment

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
	
Select from document table:

	cqlsh> SELECT * FROM imixs_dev.document ;
	
	 id                                   | created                         | data | modified                        | type
	--------------------------------------+---------------------------------+------+---------------------------------+---------
	 77d02ca4-d96e-4052-9b59-b8ea6ce052aa | 2018-02-18 16:49:35.050000+0000 | null | 2018-02-18 16:49:35.050000+0000 | workitem
	
	(1 rows)



### Docker Compose

The project includes a test environment based on a docker stack including the following components:

* Imixs-Office-Workflow
* PostrgreSQL Database
* Imixs-Archive-Cassandra

To start the test environment run:

	docker-compose up



	   
	   
	   

