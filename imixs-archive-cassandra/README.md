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

## Syncpoint

An Imixs Archive configuration holds a syncpoint. The syncpoint is the last successfull read form the source system in miliseconds. 
When the syncpoint is reset it is set to January 1, 1970 00:00:00 GMT.
After each successfull sync the syncpoint will be set to the modidfied timestamp of the synced workitem. 



# Docker Support

The project includes a test environment based on a docker stack including the following components:

* Imixs-Archive-Cassandra - Web Front-End
* Cassandra - local cluster

To start the environment run:
	
	$ docker-compose up


Alternativly you can use the docker-compose-dev.yml file to start an extended development envionment including the following services:

* Imixs-Archive-Cassandra - Web Front-End
* Cassandra - local cluster
* Imixs-Office-Workflow - Web Application
* PostgreSQL - Database

To start the dev environment run: 

	$ docker-compose -f docker-compose-dev.yml up



## Build with Maven 

If you have not yet a Imixs-Archive-Cassandra container, you can build the application from sources and create the docker image use the maven command:

	$ mvn clean install -Pdocker-build


# The Cassandra Query Language Shell 

With the  Cassandra Query Language Shell (cqlsh) you can evaluate a cassandra cluster form the console. This is the native way to access cassandra. You can create keyspaces as also table schemas and you can query data from you tables. 

## How to access the Cassandra Cluster with Docker

To run cqlsh from your started docker environment run:

	$ docker exec -it cassandra-dev cqlsh
	Connected to Test Cluster at 127.0.0.1:9042.
	[cqlsh 5.0.1 | Cassandra 3.11.1 | CQL spec 3.4.4 | Native protocol v4]
	Use HELP for help.
	cqlsh>

## CQL Examples
The following section contains some basic cqlsh commands. For full description see the [Cassandra CQL refernce](https://docs.datastax.com/en/dse/6.0/cql/). 

**show key spaces:**

Show all available keyspaces:

	cqlsh> DESC KEYSPACES;	
	
**Switch to Keysapce:**

Select a keyspace be name to interact with this keyspace:

	cqlsh> use imixsarchive ;
	
**Show tables in a keyspace:**	

Show tables schemas in current keyspace: 

	cqlsh:imixsarchive> DESC TABLES;
	
**Drop Keyspace: ** 

Drop the keyspace: 

	DROP KEYSPACE [IF EXISTS] keyspace_name

### Create a dev keyspace with cqlsh

	cqlsh> CREATE KEYSPACE imixs_dev WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};
	
	
### Create the Data Schema

The following section shows the commands to create a imixs-archive table schema manually. 

	CREATE TABLE IF NOT EXISTS snapshots (
		id text, 
		data blob, 
		PRIMARY KEY (id))
	
	CREATE TABLE IF NOT EXISTS snapshots_by_uniqueid (
		uniqueid text,
		snapshot text, 
		PRIMARY KEY(uniqueid, snapshot))
	
	CREATE TABLE IF NOT EXISTS snapshots_by_modified (
		modified date,
		id text,
		PRIMARY KEY(modified, id));

**Note:** The imixs-archive-cassandra application creates the schemas in background. So a manual creation of schemas is not necessary. 
	
### Select from document table:

	cqlsh> SELECT * FROM imixs_dev.snapshots;
	
	 id                                   | data 
	--------------------------------------+-----------------------
	 77d02ca4-d96e-4052-9b59-b8ea6ce052aa | null 
	
	(1 rows)



