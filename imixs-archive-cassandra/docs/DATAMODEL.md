# The Archive Data Model

All data of a single Imixs-Workflow instance is archived into a Cassandra keystore. Each keystore consists of a set of tables storing the business process data and documents. To make the data of an Imixs-Workflow Instance high available, the data is replicated over several data nodes in the same Cassandra cluster. Each replica holds typically the full data of a single Imxis-Workflow instance. Thus, all data from one instance is stored redundantly over all data nodes in a cluster.  See the [Apache Cassandra Project](http://cassandra.apache.org/) for more information about how Cassandra stores data in a cluster.

## The Table Model

The business data managed by an Imixs Workflow instance can consist of a huge amount of data. This includes data fields as also documents attached to a process instance. Therefore, a process instance can become several megabytes in size.

As Cassandra runs in the JVM, reading and writing those objects end up in the heap as byte arrays. 
Reading and writing those business data in a lot of concurrent requests can force situations where latency becomes an issue. 
On the read path, Cassandra build up an index of CQL rows within a CQL partition. This index scales with the width of the partition ON READ. In wide CQL partitions this will create JVM GC pressure.

To solve this issue and guaranty best performance ON READ and ON WRITE we store the data of each process instance into a single partition. The partition is identified by the _$UniqueID_ provided by the Imixs-Workflow engine. By using this model, the amount of data of a single process instance should not exceed the size of 100MB. 
This restriction also occurs because a process instance must be managed within the application server also as a single object. So the heap size problem is valid not only to Cassandra.  In case you have to handle large media files with more than 100MB it is not recommended to store those files within a process instance. You can consider to [split the data in smaller chunks](https://ralph.blog.imixs.com/2018/06/29/cassandra-how-to-handle-large-media-files/) and store it in a separate data model.    

The tables "_document\_snapshots_" and "_document\_modified_" are used in this data model to query process instances by the $unqiueid or the modified date.  


	CREATE TABLE document (
	  id text,
	  data blob,
	  PRIMARY KEY (id)
	)
	
	CREATE TABLE document_snapshots (
	  uniqueid text,
	  snapshot text,
	  PRIMARY KEY(uniqueid, snapshot)
	)
	
	CREATE TABLE document_modified (
	  modified date,
	  id text,
	  PRIMARY KEY(modified, id)
	)





## Writing a Process Instance

To store a process instance into this data model the EJB ArchiveService can be used:


	ItemCollection workitem;
	....
	archiveService.write(workitem);

	
## Read Process Instances

You can read process instances directly 

	ItemCollection workitem=archiveService.read(id);
 	
or by selecting first IDs by $uniqueid or modified date:


	// return all snaphostids for a given UniqueID
	List<String> ids=archiveService.findSnapshotsByUnqiueID(uniqueid);
	
	...
	// return all snapshotIDs for a given date 
	List<String> ids=archiveService.findSnapshotsByDate("2018-06-29");

 	