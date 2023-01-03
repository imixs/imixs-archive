# Imixs-Archive-Backup

The Imixs-Archive-Backup service is an independent microservice used to backup Imixs-Archive data into a external FTP server. This microservice can be run independent from a Imixs-Workflow Instance on separate hardware.


# Configuration

The Backup Service can be configured in a Imixs-Workflow instance by adding the Imixs-Archive API and setting the environment parameter `backup.service.endpoint`. If this parameter points to a Imixs-Backup service instance, than on each save event a new event log entry `snapshot.backup` will be created. The backup Service periodically check this event log entries and stores the corresponding snaptshot into the backup space.

## Backup Workflow

The backup workflow depends on the archive infrastructure. In case of a simple setup without a Cassandra Archive Service installed, the backup request is generated directly after a new Snapshot Workitem was stored. The snapshot will be backuped and remains in the database.

In case of using the [Cassandra Archive Service](../imixs-archive-service/README.md) the backup request is generated after the snapshot was successful transferred into the Cassandra cluster. After the snapshot was be backuped successful, the snapshot will be removed form the database. This concept reduces the size of the database as file data is no longer stored in the database but in the Cassandra cluster.


# Development

Imixs-Archive-Backup  runs as a self-contained microservice with a modern Web UI based on JDK 11 and Jakarta EE Faces 4.0. The client interacts with the Imixs-Workflow Engine via the Imixs-Rest API and the [Imixs-Melman library](https://github.com/imixs/imixs-melman). 

To build the imixs-admin client manually from sources run the maven command:

	$ mvn clean install

The .war file can be deployed into any Jakarta EE Application server.

## Build the Docker Image

To build the imixs-admin Docker image manually run:

	$ mvn clean install -Pdocker

To start it from your local docker environment:

	$ docker-compose up

### Debug

The docker-compose file automaticaly enables the wildfly debug port 8787
