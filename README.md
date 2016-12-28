# imixs-archive

Imixs-Archive is a microservice providing a RestFull interface to store documents from any application into a central archive system.
The Imixs-Archive service is based on the Hadoop Distributed File System (HDFS) and can be used in conjunction with [Imixs-Workflow](http://www.imixs.org).

## Hadoop Distributed File System (HDFS)

HDFS is a Java-based distributed file system that allows the reliable and persistent storage as well as fast access to large volumes of data. HDFS follows the "write once, read many" paradigm (WORM). It is designed that data is written ideally only once and then often be read out. Modifying data after writing is hardly possible.

## Data Consistency 

Imixs-Archive guarantees the consistency of the stored data by calculating a MD5 checksum for each document stored into the archive. The checksum is part of the access-URL returned by the archive system after a document was stored. If the access-URL specified later by the client to read the data did not match, an error code is returned. 


## Access Control
The access to data, written into the Imixs-Archive, should be ideally managed completely by the [Imixs-Workflow](http://www.imixs.org) engine. Imixs-Workflow supports a multiple-level security model, that offers a great space of flexibility while controlling the access to all parts of a workitem. 


# Docker
The Imixs Docker Container '[imixs/archive](https://github.com/imixs-docker/archive)' can be used to run a Imixs-Archive on a Docker host.

When installing the imixs-archive as a docker container the http deamon should not be accessible form an external network. A user can request a document through the imixs-workflow instance. Imixs-Worklfow grants access to a process instance based on the ACL of the corresponding workitem. If a user is not allowed to access a process instance managed by the Imixs-Workflow system, he is not allowed to access linked content form the imixs-archive instance.

# Hadoop

Make sure that the hdfs is started:

    /hadoop/sbin/start-dfs.sh 

Make sure that JAVA_HOME is set in the hadoop-env.sh file

    # The java implementation to use.
    #export JAVA_HOME=${JAVA_HOME}
    export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/

   
  
See also tutorial [Hadoop Single Node Cluster](http://www.michael-noll.com/tutorials/running-hadoop-on-ubuntu-linux-single-node-cluster/)


# Development

During development the imixs/archive docker container can be used with mounting an external deployments/ folder:

	docker run --name="archive" -d -p 8080:8080 -p 9990:9990 \
         -e WILDFLY_PASS="adminadmin" \
         -v ~/git/imixs-archive/deployments:/opt/wildfly/standalone/deployments/:rw \
         imixs/archive

Logfiles can be monitored with 

	docker logs -f archive



