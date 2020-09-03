# The Imixs-Archive Service
The *"Imixs-Archive-Service"* project stores the data of an Imixs-Workflow instance into a highly available Big Data Platform based on [Apache Cassandra](http://cassandra.apache.org/). 
The Service is part of a Cassandra cluster consisting of multiple Data Nodes, which is a highly available and resilient storage solution. 

The *Imixs-Archive Service* runs on Jakarta EE and fits perfectly into a microservice infrastructure. All communication is based on the Imixs Rest API. The service can be run on Bare-metal server or in a containerized infrastructure. 

All the data is stored in a platform and technology neutral XML format. This guaranties the  cross-technology data exchange independent from a specific software version over a long period of time.   


## Rest API
The Rest API which is part the *Imixs-Archive-Service* provides methods to update and access snapshot data:

 * PUT /archive/snapshot/ - stores a snapshot into the archive
 * GET /archive/snapshot/{id} - loads a snapshot from the archive
 * GET /archive/snapshot/{id}/file/{file} - loads the file content from a snapshot by its filename 
 * GET /archive/md5/{md5} - loads the file content by its MD5 checksum (recommended)


## The SyncService

The SyncService pulls a Snapshot data from a remote workflow instance into the cassandra archive. The service uses an asynchronous mechanism based on the Imixs EventLog.

The service connects to an Imixs-Workflow instance by the Rest Client to read new snapshot data.
The service is triggered by the SyncScheduler implementing a ManagedScheduledExecutorService.
 
**Note:** A EventLog entry in the remote workflow instance is only created in case the ARCHIVE_SERVICE_ENDPOINT is defined by the remote system. See also the *SnapshotService* EJB in the [imixs-archive-api](../imixs-archive-api/README.md). 
 
 
## The ResyncService

The *Imixs-Archive-Service* provides a feature to automatically resync all business process data and documents into the Cassandra Cluster. 
You can retrieve a single process instance based on a timeline or restore the entire archive. Restoring an entire archive can be used, for example, after a data loss or a Disaster recovery of an Imixs Workflow instance. 

The Sync Service provides a Web UI to control and monitor the archive service:

	http://localhost:8080/
 
<img src="https://github.com/imixs/imixs-archive/raw/master/imixs-archive-service/imixs-archive-001.png" width="800"/>	


 
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
	      WORKFLOW_SERVICE_ENDPOINT: http://imixs-workflow:8080/api
	      WORKFLOW_SERVICE_USER: "admin"
	      WORKFLOW_SERVICE_PASSWORD: "adminadmin"
	      WORKFLOW_SERVICE_AUTHMETHOD: "form"
	    ports:
	      - "8080:8080"
	
	  cassandra:
	     image: cassandra:3.11

In this example the *"Imixs-Archive-Service"* connects to a Imixs-Workflow instance on the api endpoint http://imixs-workflow:8080/api.  
The service creates a new cassandara keystore with the name 'imixsdev' and pulls the data every hour. 

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

You can start the Imixs-Office-Workflow application from the following URL:

	http://localhost:8081/

The Imixs-Admin client can be started by the URL:
		
	http://localhost:8082/



## Security

There are several ways how you can secure your Cassandra Cluster. For example you can activate a [Node-to-node encryption](https://docs.datastax.com/en/archived/cassandra/3.0/cassandra/configuration/secureSSLNodeToNode.html), a [Client-to-node encryption](https://docs.datastax.com/en/archived/cassandra/3.0/cassandra/configuration/secureSSLClientToNode.html) and a [Password Authenticator](https://docs.datastax.com/en/archived/cassandra/3.0/cassandra/configuration/secureConfigNativeAuth.html). If you do so you need to provide the Imixs-Archive Service with the necessary information. This can be done by setting the corresponding environment variables:


      ARCHIVE_CLUSTER_AUTH_USER: "imixs"
      ARCHIVE_CLUSTER_AUTH_PASSWORD: "adminadmin"
      ARCHIVE_CLUSTER_SSL: "true"
      ARCHIVE_CLUSTER_SSL_TRUSTSTOREPATH: "/cassandra.truststore"
      ARCHIVE_CLUSTER_SSL_TRUSTSTOREPASSWORD: "adminadmin"

In this example for a client authentication the user 'imixs' with password 'adminadmin' is provided. And for a secure communication the SSL connection between the client and the cluster is activated. In this case a valid truststore with a password need to be provided. See also [DataStax Java Driver SSL](https://docs.datastax.com/en/developer/java-driver/3.9/manual/ssl/).

Find also information about Cassandara and SSL [here](https://ralph.blog.imixs.com/2020/06/22/setup-a-public-cassandra-cluster-with-docker/)

## Build with Maven 

If you have not yet a Imixs-Archive-Service container, you can build the application from sources and create the docker image use the maven command:

	$ mvn clean install -Pdocker-build



# The Imixs-Archive Data Schema

The Imixs-Archive provides a denormalized data schema to optimize storage and access of archive data within a Cassandara cluster environment. 
Each process instance is stored as a Snapshot in the main table space named "*snapshots*". The primary and partition key for this table is the *$snapshotid* of the snapshot.  The data is stored in XML format. 

## Use the cqlsh commandline tool

To examine the data in your cassandra cluster you can use the *cqlsh* command line tool. With Docker you can run the command:

	$ docker exec -it cassandra cqlsh

replace \[container-dev\] with the id or name of your cassandra docker container. See the following example to select a snapshot data by $uniqueid:

	cqlsh> SELECT * FROM imixsdev.snapshots_by_uniqueid where uniqueid='<uniqueid>' 
	 id                                   | data 
	--------------------------------------+-----------------------
	 77d02ca4-d96e-4052-9b59-b8ea6ce052aa | ... 
	
	(1 rows)

Replace the <uniqueid> with a valid $uniqueid

Read the section [Datamodel](docs/DATAMODEL.md) for detailed information about the Cassandra Data Schema.

**Note:** The Imixs-Archive-Service application creates the schemas in background. So a manual creation of schemas is not necessary. 


# Architecture & Configuration


## The Syncpoint

An Imixs Archive configuration holds a syncpoint. The syncpoint is the last successful read form the source system in milliseconds. 
When the syncpoint is reset it is set to January 1, 1970 00:00:00 GMT.
After each successful sync the syncpoint will be set to the modified timestamp of the latest synchronized process instance.  

**Note:** In case the time zone changes on the workflow server or the database server the syncpoint need to be reset. Otherwise, the offset can result in a loss of snapshot data. 


## Configuration

The Imixs-Archive Microservice is configured by environment variables. 
The following configuration parameters are mandatory:

 * ARCHIVE\_CLUSTER\_CONTACTPOINTS = one or many contact points of cassandra nodes within one cluster. 
 * ARCHIVE\_CLUSTER\_KEYSPACE = cassandra keyspace for the archive (will be created automatically if not exits)

The cluster replication can be configured by following optional parameters:

 * ARCHIVE\_SCHEDULER\_DEFINITION = cron definition for scheduling (default = 'hour=*')
 * ARCHIVE\_CLUSTER\_REPLICATION\_FACTOR = defines the replication factor (default = 1)
 * ARCHIVE\_CLUSTER\_REPLICATION\_CLASS = replicator strategy (default = 'SimpleStrategy')
 
 
The workflow service endpoint to read data from is configured by the following parameters:

 * WORKFLOW\_SERVICE\_ENDPOINT = rest url to read workflow data
 * WORKFLOW\_SERVICE\_USER = user id to connect rest service endpoint
 * WORKFLOW\_SERVICE\_PASSWORD = password to connect rest service endpoint
 * WORKFLOW\_SERVICE\_AUTHMETHOD = authentication method for rest service endpoint (form,basic)



## Creating a Snaphot

To create a snapshot from a process instance the EJB ArchiveService method 'save' encapsulates the process to store data into the data schema.

	ItemCollection workitem;
	....
	dataService.save(workitem);


### $snapshot.history

The optional item '*$snapshot.history*' can be set to define the maximum count of historical snapshots stored in the archive system.  
During the save method the dataService will automatically delete older snapshots exceeding the snapshot history. If no $snapshot.hisotry is defined or 0 than no historical snapshots will be deleted. 

### Writing Statistic Data

During the archive process, the Imixs-Archive Service write statistical data into the 'meata-document'. This data can be used to analyze the amount of data in a singe Imixs-Workflow instance. 

	SELECT * FROM imixs_dev.snapshots where snapshot='0'";

	
## Read a Process Instances

To Imixs-ARchive Service provides service classes to read an archived process instance: 

	ItemCollection workitem=dataService.loadSnapshot(id);
	
This method expects the $snapshotID of an archived process instance. 
 	
The methods *loadSnapshotsByUnqiueID* or *loadSnapshotsByDate* can be used selecting first the SnapshotIDs by a given $uniqueid or modified date:


	// return all snaphostids for a given UniqueID
	List<String> ids=archiveService.findSnapshotsByUnqiueID(uniqueid);
	
	...
	// return all snapshotIDs for a given date 
	List<String> ids=archiveService.findSnapshotsByDate("2018-06-29");



## Cluster Setup with Docker Swarm

To setup a Cassandra Cluster with Docker Swarm read the section [Cluster Setup](docs/CLUSTER.md).

If you run a Cassandra Cluster with multiple nodes it is recommended to setup the replication factor for a keyspace to the number of nodes. 

To change the replication factors (RF) of the security keyspaces:

    ALTER KEYSPACE my_keyspace WITH REPLICATION= {'class' : 'SimpleStrategy','replication_factor' : '2'};


## Wildfly - max-post-size

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