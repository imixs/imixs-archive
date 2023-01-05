# Imixs-Archive-Backup

The *Imixs-Archive-Backup Service* is a microservice to backup the Imixs-Workflow data into an external storage. This backup service is decoupled from the a Imixs-Workflow Instance and can be run in a independent server environment. In this way the *Imixs-Archive-Backup Service* provides an additional level of protection for your workflow data. 

You can run this microservice on different hardware or in a external cluster. If your primary system fails or becomes unavailable, you can still access the backup system to restore your data. 
This backup architecture provides an additional level of security for your data, as it can be configured to store backups in a secure and isolated location. This can be especially important for sensitive or confidential data that you want to protect from unauthorized access.
The *Imixs-Archive-Backup Service* provides a recovery mechanism to set up a workflow instance even after a natural disaster or other catastrophic event that has corrupted your primary data.

<img src="https://github.com/imixs/imixs-archive/raw/master/docs/imixs-backup-screen.png"/>

## Integration

The *Imixs-Archive-Backup Service* can be integrated into the Imixs-Archive solution in different ways. 

**Backup without Archive**

In case of a simple setup without a Cassandra Archive Service installed, the backup request is generated directly after a new Snapshot Workitem was stored. The snapshot will be backuped and remains in the database.

<img src="https://github.com/imixs/imixs-archive/raw/master/docs/imixs-backup.png"/>

**Backup from a Archive**

In case of using the [Cassandra Archive Service](../imixs-archive-service/README.md) the backup request is generated after the snapshot was successful transferred into the Cassandra cluster. After the snapshot was be backuped successful, the snapshot will be removed form the database. This concept reduces the size of the database as file data is no longer stored in the database but in the Cassandra cluster.

<img src="https://github.com/imixs/imixs-archive/raw/master/docs/imixs-archive-backup.png"/>



# Configuration

The Backup Service can be run in a container environment. To connect the backup service with your workflow instance the following environment parameters are mandatory:

      WORKFLOW_SERVICE_ENDPOINT: [REST-API-ENDPOINT-OF-WORKFLOW-INSTANCE]   
      WORKFLOW_SERVICE_USER: [BACKUP-USER]
      WORKFLOW_SERVICE_PASSWORD: [PASSWORD]
      WORKFLOW_SERVICE_AUTHMETHOD: [AUTHMETHOD]
      
      BACKUP_FTP_HOST: [FTP-SERVER-ADDRESS]
      BACKUP_FTP_PATH: [DIRECTORY]
      BACKUP_FTP_PORT: "21"
      BACKUP_FTP_USER: [USER]
      BACKUP_FTP_PASSWORD: [PASSWORD]

**Note:** The BACKUP-USER must have manager access.

To connect a Imixs-Workflow instance with the Backup Service you need to add the Imixs-Archive API  as a dependency.

		<dependency>
			<groupId>org.imixs.workflow</groupId>
			<artifactId>imixs-archive-api</artifactId>
			<version>${org.imixs.archive.version}</version>
			<scope>provided</scope>
		</dependency>

Next set the environment parameter `backup.service.endpoint` to activate the backup. 

      BACKUP_SERVICE_ENDPOINT: [REST-API-ENDPOINT-OF-BACKUPSERVICE]  

On each save event a new event log entry `snapshot.backup` will be created. The backup Service periodically check this event log entries and stores the corresponding snaptshot into the backup space.



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
