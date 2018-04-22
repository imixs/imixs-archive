# Imixs-Archive-Cassandra
The _"Imixs-Archive-Cassandra"_ project provides a service to store the data of Imixs-Workflow into a highly available Big Data Platform based on [Apache Cassandra](http://cassandra.apache.org/).

_Imixs-Archive-Cassandra_ is running on a Cassandra Cluster consisting of serveral data nodes. An Imixs-Archive Microservice retrieves the data from a Imixs Worklfow Instance automatically and restores the data on demand if necessary. All data of a single Imixs-Workflow instance is persisted into a Cassandra keystore. Each keystore consists of one table storing the document data. To make the data of a Imixs-Workflow Instance high available, the data is replicated over several data nodes in the same Cassandra cluster. Each replica holds typically the full data of a single Imxis-Workflow instance. Thus, all data from one instance is stored redundantly over all data nodes.  


## The Data Model

The data model of _Imixs-Archive-Cassandra_ is quite simple. We use only one data table with one secondary index:
	
	CREATE TABLE documents (
	   id text,
	   created text,
	   data text,
	   PRIMARY KEY (id)
	);
	
	CREATE INDEX ON imixs_dev.documents (created);


* The primary key is the unique id of a Imixs Workflow document.
* The creation date is stored in ISO format in the column 'created'. This column is index to restore documents by creation date.
* Each document is represented in XML and stored in the column 'data' which allows to read and extracted the data in a technology-neutral way. 

### Writing a document:

	INSERT INTO documents (id, created, data)
		    VALUES ('xxx-yyy-zzz','2018-03-30','<document><item>....</document>'
		);


### Selecting a document:

	SELECT * FROM document WHERE id ='xxx-yyy-zzz';	

**Note:** The size of an XML representation of a Imixs document is only slightly different in size from the serialized map object. This is the reason why we do not store the document map in a serialized object format.  



### Selecting documents by date:

So select documents by a creation date the index on the 'created' date is used. 

	SELECT id FROM imixs_dev WHERE created >'2017-02' AND created <'2018-04' ALLOW FILTERING;

It is recommended to only query the ID when searching for data by date. This increases the access speed of reading the data.


### Why do We Use a Secondary Index Instead of a Denormalized Data Model?

The data schema used by _Imixs-Archive-Cassandra_ is designed to provide a high level of data consistency and high performance for write and read access on documents. Searching for documents is not a goal of this platform. The primary key is the UniqueId of a document which is the same id as used in Imixs-Workflow. This makes it easy to synchronize both data stores. The secondary index for the creation date is only used to restore data on selecting documents by day. As the _Imixs-Archive-Cassandra_ is used to store data redundantly over several Cassandra data nodes reading over different data nodes is not needed. 
The alternative use of a MATERIALIZED VIEW is not recommended by the Cassandra project. Using a secondary denormalized index-table does not meet the requirements of this project in data consistency. 
	

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


	cqlsh> CREATE TABLE documents (
	   id text,
	   created text,
	   data text,
	   PRIMARY KEY (id)
	);
	
	cqlsh> CREATE INDEX ON imixs_dev.documents (created);
	
	
Select from document table:

	cqlsh> SELECT * FROM imixs_dev.document ;
	
	 id                                   | created                         | data | modified                        | type
	--------------------------------------+---------------------------------+------+---------------------------------+---------
	 77d02ca4-d96e-4052-9b59-b8ea6ce052aa | 2018-02-18 16:49:35.050000+0000 | null | 2018-02-18 16:49:35.050000+0000 | workitem
	
	(1 rows)



# Docker Support

To run the web UI locally with docker run:


	docker build --tag=imixs/imixs-archive-cassandra .


	docker-compose up


### Docker Compose

The project includes a test environment based on a docker stack including the following components:

* Imixs-Office-Workflow
* PostrgreSQL Database
* Imixs-Archive-Cassandra

To start the test environment run:

	docker-compose up

