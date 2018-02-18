# Imixs-Archive-Cassandra
The Imixs-Archive-Cassandra project provides an adapter for [Apache Cassandra](http://cassandra.apache.org/) Big Data Platform.



## Test Environment

For local dev tests an Apache Cassandra test environment can be setup with Docker. 

Starting a Cassandra Docker container:

	$ docker run --name cassandra-dev -it -p 9042:9042 cassandra:latest

This container can now be used for junit tests as provided in the project. 

### cqlsh

To run a cqlsh (Cassandra Query Language Shell) against your Cassandra Dev container run:

	$ docker exec -it cassandra-dev cqlsh
	Connected to Test Cluster at 127.0.0.1:9042.
	[cqlsh 5.0.1 | Cassandra 3.11.1 | CQL spec 3.4.4 | Native protocol v4]
	Use HELP for help.
	cqlsh>


### Docker Compose

The project includes a test environment based on a docker stack including the following components:

* Imixs-Office-Workflow
* PostrgreSQL Database
* Imixs-Archive-Cassandra

To start the test environment run:

	docker-compose up



### Create a dev keyspace with cqlsh

	cqlsh> CREATE KEYSPACE imixs_dev WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};
	



	   
	   
	   

