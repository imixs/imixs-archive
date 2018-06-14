# The Archive Data Model

All data of a single Imixs-Workflow instance is archived into a Cassandra keystore. Each keystore consists of a set of tables storing the business process data and documents. To make the data of a Imixs-Workflow Instance high available, the data is replicated over several data nodes in the same Cassandra cluster. Each replica holds typically the full data of a single Imxis-Workflow instance. Thus, all data from one instance is stored redundantly over all data nodes in a cluster.  See the [Apache Cassandra Project](http://cassandra.apache.org/) for more information.

## The Table Model


The business data managed by an Imixs workflow instance can consist of a huge amount of data fields and can also contain documents. Therefore, a process instance can become several megabytes in size.

As Cassandra runs in the JVM, reading and writing those objects end up in the heap as byte arrays. 
Reading and writing those business data in a lot of concurrent requests can force situations where latency becomes an issue. 
On the read path, Cassandra build up an index of CQL rows within a CQL partition. This index scales with the width of the partition ON READ. In wide CQL partitions this will create JVM GC pressure. To solve this issue and guaranty best performance ON READ and ON WRITE we optimized table design used by _Imixs-Archive-Cassandra_.  

The business process data is chunked into 2MB chunks and stored in two separate data tables: 

	CREATE TABLE documents (
	document_id text,
	chunk_order int,
	chunk_id text,
	PRIMARY KEY (document_id, chunk_order))
	
	CREATE TABLE chunks (
	chunk_id text, 
	chunk blob,
	PRIMARY KEY(chunk_id))

When business process data is archived, the data will be split into 2MB chunks.
Each chunk is written into the chunks table, and the chunk_id with a hash is written into the documents table in an ordered sequence. 

When business process data is retrieved form the archive, _Imixs-Archive-Cassandra_  reassemble the data chunk by chunk by querying it from the chunk table. Each piece is optimized not to overwhelm the garbage collector of the VM. 

As a result of the table model the only partition here that can get large is document_id, but  it'd be incredibly unlikely that it get over 100MB per partition. So there is no need to worry about the index pain on the Cassandra read path.

### deduplication

_Imixs-Archive-Cassandra_ can also deduplicate chunks to reduce the size of a chunk.




