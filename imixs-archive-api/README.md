# The Imixs-Snapshot-Architecture

The sub-module Imixs-Archive-API provides the core functionality and interfaces to archive the content of a running process instance during its processing life cycle into a so called *snapshot-workitem*.
A *snapshot workitem* is an immutable copy of a workitem (origin-workitem) including all the business data and file content of attached files. A *snapshot workitem* can be stored in the workflow data storage or in an external archive storage (e.g. Apache-Cassandra or Hadoop).

<br /><br /><img src="src/uml/snapshot-service.png" />


The snapshot process includes the following stages:

1. A workitem is processed by the Imixs-Workflow engine based on a BPMN 2.0 model within a local transaction. 
2. After processing is completed, the process instance is persisted into the local workflow storage by the DocumentService.
3. The DocumentService sends a notification event to the SnapshotService. 
4. The SnapshotService creates a immutable copy of the process instance - called snapshot-workitem.
5. The SnapshotService detaches the file content form the origin workitem. 
6. The SnapshotService updates the DMS file location of the origin workitem. 
7. The snapshot workitem is stored into the local workflow storage
8. The origin process instance is returned to the application and the local transaction is closed.
9. An external archive system polls new snapshot-workitems
10. An external archive system stores the snapshot-workitems into a archive storage. 


A snapshot-workitem holds a reference to the origin-workitem by its own $UniqueID which is 
always the $UniqueID from the origin-workitem suffixed with a timestamp. 
During the snapshot creation the snapshot $UniqueID is stored into the origin-workitem attribute '*$snapshotid*'. 


## Snapshot History

The snapshot-service will hold a snapshot history.  The snapshot history can be configured by the imixs property

	snapshot.history=1 
	
The *snapshot.history* defines how many snapshots will be stored into the local database. The default setting is '1' which means that only the latest snapshot will be stored.  A setting of '10' will store the latest 10 snapshot-workitems. 
When the history is set to '0', no snapshot-workitems will be removed by the service. This setting is used for external archive systems.  


## Attachments

Attachments can be part of an ItemCollection stored in the item named '$file'. The $file item contains a list of FileData objects, each holding the following core information about an attachment:

 * name - the file name
 * ContentType - the media type (e.g. application/pdf)
 * content - a byte array with the raw data of the file.
 
The FileData objects are automatically transfered into the snapshot-workitem. The file content is removed from the origin workitem and only stored in the snapshot. This behavior reduces the data size and significantly increases the performance when accessing business data. 

### File Meta Data 

The Imixs-Archive API stores additional metadata for each fileData object.

 * $created - creation date
 * $creator - userId of the current editor added the attachment
 * md5checksum - MD5 checksum allowing the verification of the data consistency
 * txtcomment - optional comment field
 * text - optional ocr text content of a document (see the module [imixs-archive-documents](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-documents)).

The method getAttribute(String name)  can be used ot access the meta data of a FileData object

    String md5=(String)fileData.getAttribute("md5checksum").get(0); 

An application can add optional attributes as well. 

The following additional file meta information is stored in the workitem as extra items:

 * $file.names - contains a list of all filenames attached to the workitem
 * $file.count - the number of files stored in a workitem.
 


## The Access Control (ACL)
The access to archive data, written into the Imixs-Archive, is controlled completely by the [Imixs-Workflow engine ACL](http://www.imixs.org/doc/engine/acl.html). Imixs-Workflow supports a multiple-level security model, that offers a great space of flexibility while controlling the access to all parts of a workitem. 

Each snapshot-workitem is flagged as '*$immutable=true*' and '*$noindex=true*'. This guarantees that the snapshot can not be changed subsequently by the workflow system or is searchable through the lucene index. 

## Rest API

The *SnapshotRestService* Rest API guarantees the transparent access to the archived documents.

	http://localhost:8080/office-workflow/rest-service/snapshot/[$UNIQUEID]/file/[FILENAME]


## NOSNAPSHOT AND SKIPSNAPSHOT Flags

It is possible to prohibit the creation of a snapshot when a document is saved. In this case the item '*$nosnapshot*' must be set to 'true'. This can be useful is some rare situations. Use this flag carefully! The item '*$nosnapshot*' is persisted and will avoid future snapshots until the flag is removed or set to false.

An alternative is the flg '*$skipsnapshot*'. This flag is temporarily and will be removed by the snapshot api. This flag is used by the snapshot restore mechanism which recovers a document entity by a snapshot instance. 


## $snapshot.history

A document can provide the item '*$snapshot.history*'. This optional item defines the maximum snapshots stored in an archive system.  See the [Imixs-Archive-Service](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-service) for more details.


## CDI Events

The communication between the service layers is implemented by the CDI Observer pattern. The CDI Events are tied to the transaction context of the Imixs-Workflow engine. 
See the [DocumentService](http://www.imixs.org/doc/engine/documentservice.html#CDI_Events) for further information. 


# How to Calculate the Size of a Imixs-Archive System?

To calculate the size of an Imixs-Archive system, the following factors are crucial: 

 * Number of tasks within a process flow.
 * Size of Metadata generated during a processing life cycle.
 * Size of documents attached to a process instance. 
 
 
The size for Imixs-Archive is calculated in the following example:
 
 1. The number of individual steps in a sample process includes 10 task
 2. The metadata of a single process instance is between  8KB and 16KB 
 3. The file content  of a single process instance  is between 0,5 MB  and 1 MB

Imixs-Archive generates a snapshot-workitem in each processing step. So the total size of all snapshot-workitems of a single process instance in this example can be up to  12 MB. This is an average value that can vary depending on the use case.

Thus, in this example a system processing 1 million process instances per year can claim a data volume of 12 TB each year.

**Note:** In this example calculation all snapshots are exported into an external archive system. So the size of the local database will not be affected and does not grow on each processing step!s



# Deployment

To deploy imixs-archive into Imixs-Office-Workflow the following maven configuration is needed:

		<!-- Imixs-Archive -->
		<dependency>
			<groupId>org.imixs.workflow</groupId>
			<artifactId>imixs-archive-api</artifactId>
			<version>2.0.1</version>
			<scope>compile</scope>
		</dependency>

		
# Testing

The imixs-archive-api module includes jUnit tests. The jUnit test class *org.imixs.archive.api.TestSnaptshotService* mocks the EJB *SnapshotService* and simulates the processing of a workitem within the [Imixs WorkflowMockEnvironment](http://www.imixs.org/doc/testing.html#WorkflowMockEnvironment). The test BPMN model '*TestSnapshotService.bpmn*' is used to simulate a workflow. 


# Migration

The SnapshotService replaces the now deprecated BlobWorkitem functionality prior to version 5.x from the DMSPlugin. For a migration only the SnapshotService need to be added. The SnapshotService automatically migrates the deprecated blob-workitems. 

No further migration step is necessary.

The Item '*dms*' with the file meta information is handled by the SnapshotService EJB. The DMSPlugin is deprecated. 

## How Restore Deprecated workitemlob Data

In case of a data migration via the backup/resource functionality, supported by the DocumentService, it is necessary to set the imixs.porperty

	snapshot.workitemlob_suport=true

This setting allows that deprecated workitemlob entities can be restored without an exception.

To restore old data it is recommended first to import the workitemlob data and later import the regular workitem data.




