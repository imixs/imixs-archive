# Cluster Setup for Docker Swarm

Running a Apache Cassandra Cluster with Docker-Swarm is quite easy using the official Docker Image. Docker-Swarm allows you to setup several docker worker nodes running on different hardware or virtual servers. Take a look at the following example docker-compose.yml file:

	version: "3.2"
	
	networks:
	  cluster_net:
	    external:
	      name: cassandra-net  
	  
	services:  
	
	  ################################################################
	  # The Casandra cluster 
	  #   - cassandra-node1
	  ################################################################        
	  cassandra-001:
	    image: cassandra:3.11
	    environment:
	      CASSANDRA_BROADCAST_ADDRESS: "cassandra-001"
	    deploy:
	      restart_policy:
	        condition: on-failure
	        max_attempts: 3
	        window: 120s
	      placement:
	        constraints:
	          - node.hostname == node-001
	    volumes:
	        - /mnt/cassandra:/var/lib/cassandra 
	    networks:
	      - cluster_net
	
	  ################################################################
	  # The Casandra cluster 
	  #   - cassandra-node2
	  ################################################################        
	  cassandra-002:
	    image: cassandra:3.11
	    environment:
	      CASSANDRA_BROADCAST_ADDRESS: "cassandra-002"
	      CASSANDRA_SEEDS: "cassandra-001"
	    deploy:
	      restart_policy:
	        condition: on-failure
	        max_attempts: 3
	        window: 120s
	      placement:
	        constraints:
	          - node.hostname == node-002
	    volumes:
	        - /mnt/cassandra:/var/lib/cassandra 
	    networks:
	      - cluster_net

Each cassandra service runs on a specific host within the docker-swarm. You can not use the build-in scaling feature of docker-swarm because 
you typically need to define a separate data volume for each service. See the section ‘volumes’.

There are two important environment variables defining the Cassandra Cluster:


**CASSANDRA_BROADCAST_ADDRESS** defines a container name for each cassandra node within the cassandra cluster. This name matches the service name. As both services run in the same network ‘cluster_net’ the both cassandara nodes find each user via the service name.

**CASSANDRA_SEEDS** defines the seed node which need to be defined for the second service only. This is necessary even if a cassandra cluster is ‘master-less’.


## Change Replication Factor

To change the replication factors (RF) of a keyspaces alter the keyspace:

	ALTER KEYSPACE my_keyspace WITH REPLICATION= {'class' : 'SimpleStrategy','replication_factor' : '2'};


# Check cluster status

You can obtain information about the cluster using the commandline tool 'nodetool' 


	$ nodetool status
	datacenter: datacenter1
	========================
	Status=Up/Down
	|/ State=Normal/Leaving/Joining/Moving
	--  Address     Load       Tokens       Owns (effective)  Host ID                               Rack
	UN  10.0.6.66   335.61 KiB  256          39.6%            73d7741f-40a8-4f8f-8f49-073ce37e2c23  rack1
	UN  10.0.4.215  199.95 KiB  256          41.2%            0fa77708-f5a7-4160-93ac-09944fd4c66c  rack1
	UN  10.0.7.42   227.53 KiB  256          41.7%            cd6c98b5-1551-4dff-8fa6-feeb11da32ed  rack1
	
This will show you the status (UN= Up and Normal  UJ= Up and Joining), IP Address and the current load.

You can find more information [here](https://docs.bitnami.com/google-templates/infrastructure/cassandra/get-started/check-cluster-replication-status/).  

