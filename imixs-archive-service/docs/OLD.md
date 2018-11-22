


# Deprecated Model







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
	
