# The Imixs-Archive Service
The _"Imixs-Archive-Service"_ project allows to store the data of an Imixs-Workflow instance into a highly available Big Data Platform 
based on [Apache Cassandra](http://cassandra.apache.org/). 
The Service is connected to a Cassandra cluster consisting of multiple Data Nodes, which is a highly available and resilient storage solution. 

_Imixs-Archive-Service_ automatically pulls all business process data and documents on a scheduled basis into the Cassandra Cluster.
You can retrieve a single process instance based on a timeline or restore the entire archive. Restoring an entire archive can be used, for example, after a data loss or a Disaster recovery of an Imixs Workflow instance. 

The _Imixs-Archive Service_ runs on Jakarta EE and fits perfectly into a microservice infrastrcutre. All communication is based on the Imixs Rest API. The service can be run on Bare-metal server or in a containerized infrastructure. 

All the data is stored in a platform and technology neutral XML format. This guaranties the  cross-technology data exchange independent from a specific software version over a long period of time.   

The service prvides a Web UI to control and monitor the archive service:

	http://localhost:8080/
 
<img style="width:80%;" src="https://github.com/imixs/imixs-archive/raw/master/imixs-archive-service/imixs-archive-001.png" />	


 
# Docker Support

The project provides a Docker image available on [dockerhub](https://cloud.docker.com/u/imixs/repository/docker/imixs/imixs-archive-service) which can be used for test and production environments.

The following docker-compose.yml file shows a setup example:

	version: "3.2"
	services:
	
	  imixsarchiveservice:
	    image: imixs/imixs-archive-service
	    environment:
	      WILDFLY_PASS: adminadmin
	      ARCHIVE_CLUSTER_CONTACTPOINTS: "cassandra"
	      ARCHIVE_CLUSTER_KEYSPACE: "imixsdev"
	      ARCHIVE_SCHEDULER_DEFINITION: "hour=*"
	      WORKFLOW_SERVICE_ENDPOINT: http://imixs-workflow:8080/api
	      WORKFLOW_SERVICE_USER: "admin"
	      WORKFLOW_SERVICE_PASSWORD: "adminadmin"
	      WORKFLOW_SERVICE_AUTHMETHOD: "form"
	    ports:
	      - "8080:8080"
	
	  cassandra:
	     image: cassandra:3.11

In this example the _"Imixs-Archive-Service"_ connects to a Imixs-Workflow instance on the api endpoint http://imixs-workflow:8080/api.  
The service creaets a new cassandara keystore with the name 'imixsdev' and pulls the data every hour. 

To start the environment run:
	
	$ docker-compose up



## Test & Development

For test and development usage you can use the docker-compose-dev.yml file to start an extended dev-envionment including the following services:

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

The Imixs-Archive provides a denormalized data schema to optimize storrage and access of archive data witin a Cassandara cluster environment. 
Each process instance is stored as a Snapshot in the main table space named "_snapshots_". The primary and partion key for this table is the $snapshotid of the snapshot.  The data is stored in XML format. 

To access archived data the $uniqueid of the snapshot is mandatory.
	
### Select data from the snapshots table:

	cqlsh> SELECT * FROM imixs_dev.snapshots;
	
	 id                                   | data 
	--------------------------------------+-----------------------
	 77d02ca4-d96e-4052-9b59-b8ea6ce052aa | ... 
	
	(1 rows)
	
	
Read the section [Datamodel](docs/DATAMODEL.md) for detailed informatin about the Cassandra Data Schema.

**Note:** The Imixs-Archive-Service application creates the schemas in background. So a manual creation of schemas is not necessary. 

	

# Architecture & Configuration


## The Syncpoint

An Imixs Archive configuration holds a syncpoint. The syncpoint is the last successfull read form the source system in miliseconds. 
When the syncpoint is reset it is set to January 1, 1970 00:00:00 GMT.
After each successfull sync the syncpoint will be set to the modified timestamp of the latest synchronized process instance.  

**Note:** In case the time zone changes on the workflow server or the database server the syncpoint need to be reset. Otherwise, the offset can result in a loss of snapshot data. 


## Configuration

The Imixs-Archive Microservice is configured by envirnment variables. 
The following configuration parameters are mandatory:

 * ARCHIVE\_CLUSTER\_CONTACTPOINTS = one or many contact points of cassandra nodes within one cluster. 
 * ARCHIVE\_CLUSTER\_KEYSPACE = cassandra keyspace for the archive (will be created automatically if not exits)

The cluster replication can be configured by following optional parameters:

 * ARCHIVE\_SCHEDULER\_DEFINITION = cron defiition for scheduling (default = 'hour=*')
 * ARCHIVE\_CLUSTER\_REPLICATION\_FACTOR = defines the replication factor (default = 1)
 * ARCHIVE\_CLUSTER\_REPLICATION\_CLASS = replicator strategy (default = 'SimpleStrategy')
 
 
The workflow service endpoint to read data from is configured by the following parameters:

 * WORKFLOW\_SERVICE\_ENDPOINT = rest url to read workflow data
 * WORKFLOW\_SERVICE\_USER = user id to connnect rest service endpoint
 * WORKFLOW\_SERVICE\_PASSWORD = password to connnect rest service endpoint
 * WORKFLOW\_SERVICE\_AUTHMETHOD = authentication method for rest service enpoing (form,basic)



## Writing a Process Instance

To store a process instance into this data model the EJB ArchiveService encapsulates the process to store data into the data schema.

	ItemCollection workitem;
	....
	archiveService.write(workitem);


### Writing Statistic Data

During the archive process, the Imixs-Archive Service write statistical data into the 'meata-document'. This data can be used to analyse the amount of data in a singe Imixs-Workflow instance. 

	SELECT * FROM imixs_dev.snapshots where snapshot='0'";

	
## Read a Process Instances

To Imixs-ARchive Service provides service classes to read an archived process instance: 

	ItemCollection workitem=dataService.loadSnapshot(id);
	
This method expects the $snapshotID of an archived process instance. 
 	
The methods _loadSnapshotsByUnqiueID_ or _loadSnapshotsByDate_ can be used selecting first the SnapshotIDs by a given $uniqueid or modified date:


	// return all snaphostids for a given UniqueID
	List<String> ids=archiveService.findSnapshotsByUnqiueID(uniqueid);
	
	...
	// return all snapshotIDs for a given date 
	List<String> ids=archiveService.findSnapshotsByDate("2018-06-29");

 	

# Wildfly max-post-size

In wildfly server there is a default max-post-size of 24mb. This can be a problem if you need to restore snapshot with large data. But you can overwrite the max-post-size in the standalone xml file:

	<server name="default-server">
        	<!-- max-post-size="25485760" -->
            <http-listener name="default" max-post-size="104857600" socket-binding="http" redirect-socket="https" enable-http2="true"/>
            <https-listener name="https" socket-binding="https" security-realm="ApplicationRealm" enable-http2="true"/>
            <host name="default-host" alias="localhost">
                <location name="/" handler="welcome-content"/>
                <http-invoker security-realm="ApplicationRealm"/>
            </host>
        </server>

**Note:** This change need to be made on the server side of your imixs-workflow instance. The Imixs-Archive Service is not affected from this issue.         