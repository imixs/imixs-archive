# The Imixs-Snapshot-Architecture

The sub-module Imixs-Archive-API provides the core functionality and interfaces to archive the content of a running process instance during its processing life cycle into a so called _snapshot-workitem_.
A _snapshot workitem_ is an immutable copy of a workitem (origin-workitem) including all the business data and file content of attached files. A _snapshot workitem_ can be stored in the workflow data storage or in an external archive storage (e.g. Apache-Cassandra or Hadoop).

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
During the snapshot creation the snapshot $UniqueID is stored into the origin-workitem attribute '*$snapshotid\*'.

## Snapshot History

The snapshot-service will hold a snapshot history. The snapshot history can be configured by the imixs property

    snapshot.history=1

The _snapshot.history_ defines how many snapshots will be stored into the local database. The default setting is '1' which means that only the latest snapshot will be stored. A setting of '10' will store the latest 10 snapshot-workitems.
When the history is set to '0', no snapshot-workitems will be removed by the service. This setting is used for external archive systems.

## Attachments

Attachments can be part of an ItemCollection stored in the item named '$file'. The $file item contains a list of FileData objects, each holding the following core information about an attachment:

- name - the file name
- ContentType - the media type (e.g. application/pdf)
- content - a byte array with the raw data of the file.

The FileData objects are automatically transfered into the snapshot-workitem. The file content is removed from the origin workitem and only stored in the snapshot. This behavior reduces the data size and significantly increases the performance when accessing business data.

### File Meta Data

The Imixs-Archive API stores additional metadata for each fileData object.

- $created - creation date
- $creator - userId of the current editor added the attachment
- md5checksum - MD5 checksum allowing the verification of the data consistency
- txtcomment - optional comment field
- text - optional ocr text content of a document (see the module [imixs-archive-documents](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-documents)).

The method getAttribute(String name) can be used ot access the meta data of a FileData object

    String md5=(String)fileData.getAttribute("md5checksum").get(0);

An application can add optional attributes as well.

The following additional file meta information is stored in the workitem as extra items:

- $file.names - contains a list of all filenames attached to the workitem
- $file.count - the number of files stored in a workitem.

## The Access Control (ACL)

The access to archive data, written into the Imixs-Archive, is controlled completely by the [Imixs-Workflow engine ACL](http://www.imixs.org/doc/engine/acl.html). Imixs-Workflow supports a multiple-level security model, that offers a great space of flexibility while controlling the access to all parts of a workitem.

Each snapshot-workitem is flagged as '_$immutable=true_' and '_$noindex=true_'. This guarantees that the snapshot can not be changed subsequently by the workflow system or is searchable through the lucene index.

## Rest API

The _SnapshotRestService_ Rest API guarantees the transparent access to the archived documents.

    http://localhost:8080/office-workflow/rest-service/snapshot/[$UNIQUEID]/file/[FILENAME]

## NOSNAPSHOT AND SKIPSNAPSHOT Flags

It is possible to prohibit the creation of a snapshot when a document is saved. In this case the item '_$nosnapshot_' must be set to 'true'. This can be useful is some rare situations. Use this flag carefully! The item '_$nosnapshot_' is persisted and will avoid future snapshots until the flag is removed or set to false.

An alternative is the flg '_$skipsnapshot_'. This flag is temporarily and will be removed by the snapshot api. This flag is used by the snapshot restore mechanism which recovers a document entity by a snapshot instance.

## $snapshot.history

A document can provide the item '_$snapshot.history_'. This optional item defines the maximum snapshots stored in an archive system. See the [Imixs-Archive-Service](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-service) for more details.

## The SnapShot EventLog

The synchronization between a Imixs-Archive Service and a Imixs-Workflow Instance is based on the EvnetLog entry 'snapshot.add'. This event log entry is generated by the Imixs-Workflow Instance each time a new snapshot was created.

The Imixs-Archive Service polls the snapshot EventLog entries on a scheduled basis and pulls the snappshot data.

## Overwrite File File Data

The environment variable SNAPSHOT_OVERWRITEFILECONTENT can be used to protect existing file data to be overwritten. If the environment variable is set to 'false', than in case a file with the same name already exits, will be 'archived' with a time-stamp-sufix:

     '<FILE_NAME>.pdf' => '<FILE_NAME>-1514410113556.pdf'

This protects already archived file data and allows the 'versioning' of file content.

If the environment variable SNAPSHOT_OVERWRITEFILECONTENT is not set then it default to 'false' which means existing file data is protected.

### Temporary overwriting of File Data

During the processing life cycle this mechanism can be overwritten by providing the temporarily item '$snapshot.overwriteFileContent' with a list of filenames to be overwritten even if the environment variable is set to false. The item will automatically be cleared during the processing life cycle.

# How to Calculate the Size of a Imixs-Archive System?

To calculate the size of an Imixs-Archive system, the following factors are crucial:

- Number of tasks within a process flow.
- Size of Metadata generated during a processing life cycle.
- Size of documents attached to a process instance.

The size for Imixs-Archive is calculated in the following example:

1.  The number of individual steps in a sample process includes 10 task
2.  The metadata of a single process instance is between 8KB and 16KB
3.  The file content of a single process instance is between 0,5 MB and 1 MB

Imixs-Archive generates a snapshot-workitem in each processing step. So the total size of all snapshot-workitems of a single process instance in this example can be up to 12 MB. This is an average value that can vary depending on the use case.

Thus, in this example a system processing 1 million process instances per year can claim a data volume of 12 TB each year.

**Note:** In this example calculation all snapshots are exported into an external archive system. So the size of the local database will not be affected and does not grow on each processing step!s

# Snapshot Compactor Service

The SnapshotCompactorService is responsible to delete snapshot entities from the database after a grace period. The period is defined in years and has a hard coded minimum of 1 year.

| Parameter                               | Type    | Description                                                             |
| --------------------------------------- | ------- | ----------------------------------------------------------------------- |
| ARCHIVE_SERVICE_ENDPOINT                | url     | archive service endpoint                                                |
| ARCHIVE_SNAPSHOT_COMPACTOR_GRACE_PERIOD | years   | grace period in years after a snapshot will be deleted (default 1 year) |
| ARCHIVE_SNAPSHOT_COMPACTOR_ENABLED      | boolean | true=enabled                                                            |
| ARCHIVE_SNAPSHOT_COMPACTOR_INTERVAL     | seconds | compactor interval (default 14400 = 4 hours)                            |
| ARCHIVE_SNAPSHOT_COMPACTOR_INITIALDELAY | seconds | initial delay (default 30 sec)                                          |

The SnapshotCompactorService runs as a backend timer service. The default interval is 4 hours.

**Note:** The SnapshotCompactorService needs a `ARCHIVE_SERVICE_ENDPOINT` to be defined!

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

The imixs-archive-api module includes jUnit tests. The jUnit test class _org.imixs.archive.api.TestSnaptshotService_ mocks the EJB _SnapshotService_ and simulates the processing of a workitem within the [Imixs WorkflowMockEnvironment](http://www.imixs.org/doc/testing.html#WorkflowMockEnvironment). The test BPMN model '_TestSnapshotService.bpmn_' is used to simulate a workflow.

# Migration

The SnapshotService replaces the now deprecated BlobWorkitem functionality prior to version 5.x from the DMSPlugin. For a migration only the SnapshotService need to be added. The SnapshotService automatically migrates the deprecated blob-workitems.

No further migration step is necessary.

The Item '_dms_' with the file meta information is handled by the SnapshotService EJB. The DMSPlugin is deprecated.

## How Restore Deprecated workitemlob Data

In case of a data migration via the backup/resource functionality, supported by the DocumentService, it is necessary to set the imixs.porperty

    snapshot.workitemlob_suport=true

This setting allows that deprecated workitemlob entities can be restored without an exception.

To restore old data it is recommended first to import the workitemlob data and later import the regular workitem data.
