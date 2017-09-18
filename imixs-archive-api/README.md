# Imixs-Archive API

The sub-module Imixs-Archive-API provides the core functionality and interfaces to generate, store and retrieve business data into an archive system. This api is platform independent and based on the Imixs-Workflow API.  


## Concepts

Imixs-Archive is mainly based on the 'Workflow Push' strategy where the archive process is directly coupled to the workflow process. This means that the archive process can be controlled by the workflow model. 


### The Snapshot-Architecture

Imixs-Workflow provides a build-in snapshot mechanism to archive the content of a workitem into a snapshot-workitem. 
A snapshot workitem is a copy of the current workitem (origin-workitem) including all the file content of attached files. The origin-workitem only holds a reference ($snapshotID) to the snapshot-workitem to load attached file data. 
See the Snapshot-Concept for further details. 


Attached files will be linked from the snapshot-workitem to the origin-workitem.

The snapshot process includes the following stages:

1. create a copy of the origin workitem instance
2. compute a snapshot $uniqueId based on the origin workitem suffixed with a timestamp.
3. change the type of the snapshot-workitem with the prefix 'archive-'
4. If an old snapshot already exists, Files are compared to the current $files and, if necessary, stored in the Snapshot applied
5. remove the file content form the origin-workitem 
6. store the snapshot uniqeId into the origin-workitem as a reference ($snapshotID)
7. remove deprecated snapshots
 
A snapshot-workitem holds a reference to the origin-workitem by its own $UniqueID which is 
always the $UniqueID from the origin-workitem suffixed with a timestamp. 
During the snapshot creation the snapshot $UniqueID is stored into the origin-workitem. 

The ArchiveLocalPlugin implements the ObserverPlugin interface and is tied to the transaction context of the imixs-workflow engine. The process of creating a new snapshot workitem is aware of the current transaction in a transparent way and will automatically role back any snapshots workitems in case of a EJB Exception. 
 


### The Access Control
The access to archive data, written into the Imixs-Archive, is controlled completely by the [Imixs-Workflow engine](http://www.imixs.org). Imixs-Workflow supports a multiple-level security model, that offers a great space of flexibility while controlling the access to all parts of a workitem. 




