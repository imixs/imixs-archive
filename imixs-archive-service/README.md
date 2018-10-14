# The Imixs-Archive Microservice
The _"Imixs-Archive-Service"_ project provides a MicroSerivce to store the data of Imixs-Workflow into a highly available Big Data Platform based on [Apache Cassandra](http://cassandra.apache.org/). _Imixs-Archive-Service_ runs  typically in a Cassandra Cluster consisting of serveral data nodes. The Imixs-Archive Service runs on Jakarta EE and automatically pulls the data from an Imixs Workflow Instance into the Cassandra Cluster. This includes all business process data and documents. 

The Imixs-Archive Service can be used to retrieve a single archived process instance or to restore the entire archive. Restoring an entire archive can be used, for example, after a data loss or a Disaster recovery of an Imixs Workflow instance. 

The Imixs-Archive-Service is connected with a Imixs-Workflow instance via the Imixs-Rest API and runs as a single microservice. 

## Syncpoint

An Imixs Archive configuration holds a syncpoint. The syncpoint is the last successfull read form the source system in miliseconds. 
When the syncpoint is reset it is set to January 1, 1970 00:00:00 GMT.
After each successfull sync the syncpoint will be set to the modidfied timestamp of the synced workitem. 


## Configuration

The Imixs-Archive Microservice is configured by envirnment variables or an optional property file (imixs.properties). 
The following configuration parameters are mandatory:

 * ARCHIVE\_CLUSTER\_CONTACTPOINTS = one or many contact points of cassandra nodes within one cluster. 
 * ARCHIVE\_CLUSTER\_REPLICATION\_FACTOR = defines the replication factor 
 * ARCHIVE\_CLUSTER\_KEYSPACE = cassandra keyspace for the archive
 * ARCHIVE\_SCHEDULER\_DEFINITION = cron defiition for scheduling
 * ARCHIVE\_CLUSTER\_REPLICATION\_FACTOR = 
 * ARCHIVE\_CLUSTER\_REPLICATION\_CLASS = 
 
 
# Docker Support

The project includes a test environment based on a docker stack including the following components:

* Cassandra - local cluster
* Imixs-Archive-Service - Web Front-End (ports: 8080, 9990, 8787)

To start the environment run:
	
	$ docker-compose up

You can start the Imixs-Archive Web UI from the following URL:

	http://localhost:8080/

	 	 	
Alternativly you can use the docker-compose-dev.yml file to start an extended development envionment including the following services:

* Cassandra - local cluster
* Imixs-Archive-Service - Web Front-End (ports: 8080, 9990, 8787)
* Imixs-Office-Workflow - Web Application (ports: 8081, 9991, 8788)
* Imixs Admin Client (ports: 8082)
* PostgreSQL - Database

To start the dev environment run: 

	$ docker-compose -f docker-compose-dev.yml up

You can start the Imixs-Office-Workflow applcation from the following URL:

	http://localhost:8081/

The Imixs-Admin client can be started by the URL:
		
	http://localhost:8082/


## Build with Maven 

If you have not yet a Imixs-Archive-Service container, you can build the application from sources and create the docker image use the maven command:

	$ mvn clean install -Pdocker-build



# The Imixs-Archive Data Schema

The Imixs-Archive provides a denormalized data schema to optimize storrage and access of archive data. 
Snapshot data is stored in the main table space named "_snapshots_". The primary and partion key for this table is the $uniqueid of the snapshot. 

**Note:** The Imixs-Archive-Service application creates the schemas in background. So a manual creation of schemas is not necessary. 

To access archived data the $uniqueid of the snapshot is mandatory.

	
### Select data from the snapshots table:

	cqlsh> SELECT * FROM imixs_dev.snapshots;
	
	 id                                   | data 
	--------------------------------------+-----------------------
	 77d02ca4-d96e-4052-9b59-b8ea6ce052aa | null 
	
	(1 rows)
	
	

Read the section [Datamodel](docs/DATAMODEL.md) for detailed informatin.

	
# The ArchiveService


## Writing a Process Instance

To store a process instance into this data model the EJB ArchiveService encapsulates the process to store data into the data schema.

	ItemCollection workitem;
	....
	archiveService.write(workitem);


### Writing Statistic Data

During the archive process, the Imixs-Archive Service write statistical data. This data can be used to analyse the amount of data in a singe Imixs-Workflow instance. 



	
## Read Process Instances

To read an archived process instance directly the read() method can be used: 

	ItemCollection workitem=archiveService.read(id);
	
THis method expects the $snapshotID of an archived process instance. 
 	
The method _findSnapshotsByUnqiueID_ or _findSnapshotsByDate_ can be used selecting first the SnapshotIDs by a given $uniqueid or modified date:


	// return all snaphostids for a given UniqueID
	List<String> ids=archiveService.findSnapshotsByUnqiueID(uniqueid);
	
	...
	// return all snapshotIDs for a given date 
	List<String> ids=archiveService.findSnapshotsByDate("2018-06-29");

 	


