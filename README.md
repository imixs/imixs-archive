# imixs-archive

Imixs-Archive is a sub project of Imixs-Workflow providing a solution for long-term archiving of business data.
Imixs-Archive can be combined with the Worklfow Suite Imixs-Office-Workflow as also with individual business applications based on the Imixs-Workflow engine. The archive data is transferred to a Hadoop cluster. The Imixs-Archive project provides various functions for the exchange of data with a Hadoop culster.

The goal of this project is to provide a open and transparent technology for long-term archiving of business data based on the Imixs-Workflow project.


## Hadoop 

Imixs-Archive is based on the [Hadoop technology](http://hadoop.apache.org/) and provides a submodule to plugin hadoop into the Imixs-Workflow engine.

* [Imixs-Archive-Hadoop](https://github.com/imixs/imixs-archive/tree/master/imixs-archive-hadoop)


### Docker

The [Imixs-Docker/hadoop project](https://github.com/imixs/imixs-docker/tree/master/hadoop) provides a Docker image to run Haddop in a Docker container. This container can be used to test the hadoop in combination with Imixs-Archive. **NOTE:** The Imixs-Docker/hadoop container is for test purpose only. The container should only run in a system environment protected from external access. 






# Concepts

Imixs-Archive is mainly based on the 'Workflow Push' strategy where the archive process is directly coupled to the workflow process. This means that the archive process can be controlled by the workflow model. The Imixs-Archiveplug-in communicates with the hadoop cluster via the Hadoop Rest API. During the archive process, the Checksum computed by hadoop is immediately stored into the source workitem. This is a tightly coupled archive strategy which guaranties a transactional secure archive process.


## Data Consistency 

Imixs-Archive guarantees the consistency of the stored data by calculating a MD5 checksum for each document stored into the archive. The checksum is part of the access-URL returned by the archive system after a document was stored. If the access-URL specified later by the client to read the data did not match, an error code is returned. 


## Access Control
The access to data, written into the Imixs-Archive, should be ideally managed completely by the [Imixs-Workflow](http://www.imixs.org) engine. Imixs-Workflow supports a multiple-level security model, that offers a great space of flexibility while controlling the access to all parts of a workitem. 

