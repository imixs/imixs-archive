# Imixs-Archive-Hadoop

The Imixs-Archive-Hadoop project provides a API to store workitems into a Hadoop Cluster. Imixs-Archive-Hadoop uses the [Imixs-JCA-Hadoop Connector](https://github.com/imixs/imixs-jca/tree/master/imixs-jca-hadoop)

Imixs-Archive-Hadoop is communicating with a hadoop cluster via the [WebHDFS Rest API](https://hadoop.apache.org/docs/r2.8.0/hadoop-project-dist/hadoop-hdfs/WebHDFS.html). 

## Synchronous Mode Push

This implementation follows the architector of a synchronous push mode. By this strategy the archive process is directly coupled to the workflow process. This means that the archive process can be controlled by the workflow model. The implementation is realized by a Imixs-Plug-In which is directly controlled by the engine. The plug-in access the hadoop cluster via the Hadoop Rest API. In this scenario the plugin can store archive data, like the Checksum, immediately into the workitem. This is a tightly coupled archive strategy.

### Pros

* The archive process can be directly controlled by the workflow engine (via a plug-in)
* The data between hadoop and imixs-workflow is synchron at any time
* A workitem can store archive information in synchronous way (e.g. checksumm)

### Cons

* The process is time consuming and slows down the overall performance from the workflow engine
* The process is memory consuming
* The process have to be embedded into the running transaction which increases the complexity
* Hadoop must be accessible via the internet and additional security must be implemented on both sides.


# Implementation

The service is implemented a a stateful session EJB with a Plug-In. The statefull session EJB synchronizes the transaction and decided in the afterCommit(boolean) method either to comit or rolback the changes in hadoop. This approach is a little bit complex, time and memory consuming but has the advantage that the workitem is always synchron with the data in the hadoop cluster.  

## CDI Support

The HadoopService and the Archive Plugin support CDI. A bean.xml is located in the META-INF folder. Make sure that the client library is visible to your EJB modules. See the section 'Using shared libraries' in the [Imixs Deployment Guide](http://www.imixs.org/doc/deployment/deployment_guide.html). 



## HDFSWebClient

The HDFSWebClient code is based on the workf of [zxs/webhdfs-java-client](https://github.com/zxs/webhdfs-java-client]. 

## JUnit Tests

The libray can be tested with a single node hadoop cluster. 
For all integration tests just start the Docker hadoop container with the following command:

	docker run --name="hadoop" -d -h my-hadoop-cluster.local -p 50070:50070 -p 50075:50075  imixs/hadoop

Make sure that the hostname 'my-hadoop-cluster.local' is listed in your local test environment

See the [Imixs-Docker hadoop project](https://github.com/imixs/imixs-docker/tree/master/hadoop) for mor details.


