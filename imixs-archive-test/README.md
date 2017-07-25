# imixs-archive Test Environment

Imixs-Archive-Test provides a Docker based test environment for Imixs-Archive. The Test Environment consists of the following docker containers:

- PostgreSQL Database
- Hadoop Single Node Cluster
- Imixs-Office-Workflow


You need to checkout Imixs-Office-Workflow from Github. 


The docker-compose.yml can be used to start the test environment

    cd ~/git/imixs-archive/imixs-archive-test
    docker-compose up


### Build the docker image
To build the docker image run

	docker build --tag=imixs/imixs-office-workflow .


### JCA Hadoop Connector

Imixs Archive uses a JCA connector to communicate with the Hadoop Cluster. To install the connector follow the installation guide on 
[Imixs-JCA-Hadoop](https://github.com/imixs/imixs-jca/tree/master/imixs-jca-hadoop)

The configuration for the connector is part of the wildfly standalone.xml:

	<subsystem xmlns="urn:jboss:domain:resource-adapters:4.0">
	         <resource-adapters>
	             <resource-adapter id="imixs-jca-hadoop">
	                    <archive>imixs-jca-hadoop.rar</archive>
	                    <transaction-support>LocalTransaction</transaction-support>
	                    <connection-definitions>
	                        <connection-definition class-name="org.imixs.workflow.hadoop.jca.store.GenericManagedConnectionFactory" jndi-name="java:/jca/org.imixs.workflow.hadoop" enabled="true" use-java-context="true" pool-name="hadoop" use-ccm="true">
	                            <config-property name="rootDirectory">
	                                ./store/
	                            </config-property>
	                            <pool>
	                                <min-pool-size>0</min-pool-size>
	                                <max-pool-size>10</max-pool-size>
	                                <prefill>false</prefill>
	                                <use-strict-min>false</use-strict-min>
	                                <flush-strategy>FailingConnectionOnly</flush-strategy>
	                            </pool>
	                            <security>
	                                <application/>
	                            </security>
	                        </connection-definition>
	                    </connection-definitions>
	                </resource-adapter>
	            </resource-adapters>
	    </subsystem>

    
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
	hadoop.hdfs.baseURL=http://hadoop.local:50070/webhdfs/v1
 
 
	
