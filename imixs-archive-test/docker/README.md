# Imixs-Archive - Imixs-Office-Workflow - Test Environment

Imixs-Office-Workflow is provided as a Docker image for testing the Imixs-Archive system. 
The configuration is stored in /configuration/

You need to checkout Imixs-Office-Workflow from Github. 
This project provides the deployment unit.


The docker-compose.yml can be used to start the test environment

    cd ~/git/imixs-archive/imixs-archive-hadoop/src/main/docer
    docker-compose up

    
## Workflow Models

The folder /workflow/ contains BPMN Model for testing.


## Docker

The Test Environment consists of three Docker containers:

- imixs-archive-postgres : PostgreSQL Database (hostname 'postgres')
- imixs-archive-hadoop : Hadoop Singel Node Cluster (hostname 'hadoop.local')
- imixs-archive-office : Imixs-Office-Workflow

### Hadoop

The hadoop cluster exposes the ports 50070 and 50070. The cluster can be accessed from the outside with the URL:

	http://localhost:50070/
	 
To login into the hadoop cluster via shell run the docker command:

	docker exec -it docker_imixs-archive-hadoop_1 /bin/bash

### Imixs-Office-Workflow

The hadoop container is linked to the imixs-office-workflow container with the hostname 'hadoop'. This hostname can be used to access the hadoop cluster.
The configuration values about the hadoop cluster host name and the principal are both stored in the property file 'imixs-hadoop.properties'. See the following default configuration for 'imixs-hadoop.properties':
 
 
	hadoop.hdfs.defaultPrincipal=root
	hadoop.hdfs.baseURL=http://hadoop:50070/webhdfs/v1
 
 
	