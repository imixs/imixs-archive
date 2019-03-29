# The Archive Data Model

All data of a single Imixs-Workflow instance is archived into a Cassandra keystore. Each keystore consists of a set of tables storing the business process data and documents. To make the data of an Imixs-Workflow Instance high available, the data is typically replicated over several data nodes in the same Cassandra cluster. Each replica holds the full data of a single Imxis-Workflow instance. Thus, all data from one instance is stored redundantly over all data nodes in a cluster. See the [Apache Cassandra Project](http://cassandra.apache.org/) for more information about how Cassandra stores data in a cluster.

## The Table Model

The business data managed by an Imixs Workflow instance can consist of a huge amount of data. This includes data fields as also documents attached to a process instance. Therefore, a single process instance can become several megabytes in size.

As Cassandra runs in the JVM, reading and writing those objects end up in the heap as byte arrays. 
Reading and writing those business data in a lot of concurrent requests can force situations where latency becomes an issue. 
On the read path, Cassandra build up an index of CQL rows within a CQL partition. This index scales with the width of the partition ON READ. In wide CQL partitions this will create JVM GC pressure.

To solve this issue and guaranty best performance ON READ and ON WRITE we store the data of each process instance into a single partition. The partition is identified by the _$UniqueID_ provided by the Imixs-Workflow engine. By using this model, the amount of data of a single process instance should not exceed the size of 100MB. 
This restriction also occurs because a process instance must be managed within the application server also as a single object. So the heap size problem is valid not only to Cassandra.  In case you have to handle large media files, with more than 100MB, it is not recommended to store those files within a process instance. You can consider to [split the data in smaller chunks](https://ralph.blog.imixs.com/2018/06/29/cassandra-how-to-handle-large-media-files/) and store it in a separate data model.    

The Imixs-Archive provides a denormalized data schema to optimize storrage and access of archive data. 
Snapshot data is stored in the main table space named "_snapshots_". The primary and partion key for this table is the $uniqueid of the snapshot. 

The tables "_snapshots\_by\_uniqueid_" and "_snapshots\_by\_modified_" are used to access archived data by the uniqueid of a running process instance or by modifed date. 

AImixs Worklfow instance can also include attached documents. These documents are detached from the workflow instance before the it is stored into the _snapshots_ table and persisted in a separate document table called '_documents_'. This table space uses the MD5checksum of a document as the primary key. This means one and the same document attached to several snapshots is only stored once in the cassandra cluster. 

The table schema is defined as followed: 


	CREATE TABLE IF NOT EXISTS snapshots (
		snapshot text, 
		data blob, 
		PRIMARY KEY (snapshot))
	
	CREATE TABLE IF NOT EXISTS snapshots_by_uniqueid (
		uniqueid text,
		snapshot text, 
		PRIMARY KEY(uniqueid, snapshot))
	
	CREATE TABLE IF NOT EXISTS snapshots_by_modified (
		modified date,
		snapshot text,
		PRIMARY KEY(modified, snapshot));

	CREATE TABLE IF NOT EXISTS documents (
		md5 text,
		data blob,
		PRIMARY KEY(md5));
		
	CREATE TABLE IF NOT EXISTS snapshots_by_document (
		md5 text,
		snapshot text, 
		PRIMARY KEY(md5, snapshot));



**Note:** The imixs-archive-cassandra application creates the schemas in background. So a manual creation of schemas is not necessary. 





# The Cassandra Query Language Shell - CQL

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

	DROP KEYSPACE IF EXISTS keyspace_name;

### Create a dev keyspace with cqlsh

	cqlsh> CREATE KEYSPACE imixs_dev WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};



### Inspect the Content of the Core-Archive
The Core-Archive keyspace provides data about syncpoints of each archive:

	 cqlsh> use imixsarchive;
	 cqlsh> select * from snapshots;
	 
The data shown here is the serialized information of an XMLDocument for each configuration. Use the Web-UI to inspect the data. 

	 

### Inspect the Content of an Archive

To see the latest upsets of an archive use:

 
	cqlsh> use my_archive;
	cqlsh> select * from snapshots;
	 

	 
### Delete data from a table

TRUNCATE removes all data from the specified table immediately and irreversibly, and removes all data from any materialized views derived from that table.

Delete all data from a keyspace:

	cqlsh>TRUNCATE snapshots_by_document;
	cqlsh>TRUNCATE documents;
	cqlsh>TRUNCATE snapshots_by_modified;
	cqlsh>TRUNCATE snapshots_by_uniqueid;
	cqlsh>TRUNCATE snapshots;
	
	
