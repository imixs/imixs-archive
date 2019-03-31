# The Imixs-Snapshot-Architecture

The sub-module Imixs-Archive-API provides the core functionality and interfaces to archive the content of a running process instance during its processing life cycle into a so called _snapshot-workitem_.
A _snapshot workitem_ is an immutable copy of a workitem (origin-workitem) including all the business data and file content of attached files. A _snapshot workitem_ can be stored in the workflow data storge or in an external archive storage (e.g. Apache-Cassandra or Hadoop).

<br /><br /><img src="src/uml/snapshot-service.png" />


The snapshot process includes the following stages:

1. A workitem is processed by the Imixs-Workflow engine based on a BPMN 2.0 model. 
2. After processing is completed, the process instance is persisted into the local workflow storage by the DocumentService.
3. The DocumentService sends a notification event to the SnapshotService. 
4. The SnapshotService creates a immutable copy of the process instance - called snapshot-workitem.
5. The SnapshotService detaches the file content form the origin workitem. 
6. The SnapshotService updates the DMS file location of the origin workitem. 
7. The snapshot workitem is stored into the local workflow storage
8. The origin process instance is returned to the application
9. An external archive system polls new snapshot-workitems
10. An external archive system stores the snapshot-workitems into a archive storage. 


A snapshot-workitem holds a reference to the origin-workitem by its own $UniqueID which is 
always the $UniqueID from the origin-workitem suffixed with a timestamp. 
During the snapshot creation the snapshot $UniqueID is stored into the origin-workitem attribute '_$snapshotid_'. 


## Snapshot History

The snapshot-service will hold a snapshot history.  The snaphsot history can be configured by the imixs property

	snapshot.history=1 
	
The _snapshot.history_ defines how many snapshots will be stored into the local database. The default setting is '1' which means that only the latest snapshot will be stored.  A setting of '10' will store the latest 10 snaphsot-workitems. 
When the history is set to '0', no snapshot-workitems will be removed by the service. This setting is used for external archive systems.  


## DMS 

The Imixs-Snapshot-Architecture includes a feature to store metadata about file attachments (documents) in an item named '_dms_'. 
Each document attached to an Imixs Workitem is automatically stored in the latest snapshot-workitem and removed from the origin workitem.  
This reduces the data size and significantly increases the performance when accessing business data. The '_dms_' attribute holds a set of attributes for each attachment:

 * $created - creation date
 * $creator - userId who added the document
 * md5checksum - MD5 checksum
 * txtcomment - optional comment field
 * content - optional textual representation (see the [Imixs-Adapter-Documents Project](https://github.com/imixs/imixs-adapters/tree/master/imixs-adapters-documents))
 
The MD5 checksum allows the verification of the data consistency. In addition an application can add optional attributes as well. 
 
The _SnapshotRestService_ guarantees the transparent access to the archived documents.

	http://localhost:8080/office-workflow/rest-service/snapshot/[$UNIQUEID]/file/[FILENAME]

To access the metadata the class DMSHandler can be used to extract metadata for a specific document:

	ItemCollection dmsEntry = DMSHandler.getDMSEntry(fileName,workitem);

## The Access Control (ACL)
The access to archive data, written into the Imixs-Archive, is controlled completely by the [Imixs-Workflow engine ACL](http://www.imixs.org/doc/engine/acl.html). Imixs-Workflow supports a multiple-level security model, that offers a great space of flexibility while controlling the access to all parts of a workitem. 

Each snapshot-workitem is flagged as '_$immutable=true_' and '_$noindex=true_'. This guarantees that the snapshot can not be changed subsequently by the workflow system or is searchable through the lucene index. 

## NOSNAPSHOT AND SKIPSNAPSHOT Flags

It is possible to prohibit the creation of a snapshot when a document is saved. In this case the item "$nosnapshot" must be set to 'true'. This can be useful is some rare situations. Use this flag carefully! The item "$nosnapshot" is persisted and will avoid future snapshots until the flag is removed or set to false.

An Alternaitve is the flg "$skipsnapshot". This flag is temporarily and will be removed by the snapshot api. This flag is used by the snapshot restore mechanism which recovers a document entity by a snapshot instance. 


## CDI Events

The communication between the service layers is implemented by the CDI Observer pattern. The CDI Events are tied to the transaction context of the imixs-workflow engine. 
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

 1) Add the following artifact versions into the master pom.xml


		<!-- Imixs-Archive -->
		<org.imixs.archive.version>0.0.2-SNAPSHOT</org.imixs.archive.version>

 2) Add the following dependencies into the section dependencyManagement of the master pom.xml:


		<!-- Imixs-Archive -->
		<dependency>
			<groupId>org.imixs.workflow</groupId>
			<artifactId>imixs-archive-api</artifactId>
			<version>${org.imixs.archive.version}</version>
			<scope>provided</scope>
		</dependency>

		

 3) Add the following dependencies into the pom.xml of the ear module (optional web module if no ear is used.

		<!-- Imixs-Archive -->
		<dependency>
			<groupId>org.imixs.workflow</groupId>
			<artifactId>imixs-archive-api</artifactId>
			 <scope>compile</scope>
		</dependency>
		
		
These dependencies will add the necessary libraries into the /lib folder of the ear module (optional the web module).
The imixs-archive-api should be added directly as a jar module together with the Imixs EJB module (engine, marty), so
that these ejbs are accessable from the workflow engine:


	...
	<plugin>
		<groupId>org.apache.maven.plugins</groupId>
		<artifactId>maven-ear-plugin</artifactId>
		<version>2.6</version>
		<configuration>
			.....
			<modules>	
				....
				<JarModule>
					<groupId>org.imixs.workflow</groupId>
					<artifactId>imixs-archive-api</artifactId>
					<bundleDir>/</bundleDir>
				</JarModule>  
				...
			</modules>
			...
		</configuration>
	</plugin>
	...

	
# Testing

The imixs-archive-api module includes jUnit tests. The jUnit test class _org.imixs.archive.api.TestSnaptshotService_ mocks the EJB _SnapshotService_ and simulates the processing of a workitem within the [Imixs WorkflowMockEnvironment](http://www.imixs.org/doc/testing.html#WorkflowMockEnvironment). The test BPMN model '_TestSnapshotService.bpmn_' is used to simulate a workflow. 


# Migration

The SnapshotService replaces the now deprecated BlobWorkitem functionality from the DMSPlugin. For a migration only the SnapshotService need to be added. The SnapshotService automatically migrates the deprecated blob-workitems. 

No further migration step is necessary.

The Item 'dms' with the file meta information is handled by the SnapshotService EJB. The DMSPlugin is deprecated. 

## How Restore Deprecated workitemlob Data

In case of a data migration via the backup/resource functionality, supported by the DocumentService, it is necessary to set the imixs.porperty

	snapshot.workitemlob_suport=true

This setting allows that deprecated workitemlob entities can be restored without an exception.

To restore old data it is recommended first to import the workitemlob data and later import the regular workitem data.




