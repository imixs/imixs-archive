# Imixs-Archive-Backup

The Imixs-Archive-Backup Service is a microservice designed to backup Imixs-Workflow data to external storage systems. Operating independently from your primary Imixs-Workflow instance, this service can run on separate hardware or in external clusters, providing robust data protection through physical and logical isolation.

## Features

- **Resilience and Availability:** By running independently of your primary system, the backup service ensures data accessibility even when your main workflow instance experiences failures or becomes unavailable.

- **Enhanced Security:** The service supports deployment in secure, isolated environments, making it particularly valuable for protecting sensitive or confidential workflow data from unauthorized access.

- **Disaster Recovery:** The backup architecture enables complete workflow instance restoration following catastrophic events, natural disasters, or primary data corruption, ensuring business continuity.

- **Flexible Deployment:** Deploy the microservice across different hardware configurations or cloud environments to match your specific security and availability requirements.

<img src="https://github.com/imixs/imixs-archive/raw/master/docs/imixs-backup-screen.png"/>

## Integration

The _Imixs-Archive-Backup Service_ is based on the [Imixs-Archive-Snapshot API](../imixs-archive-api/README.md) and can be integrated into the Imixs-Archive solution in different ways.

**Backup without Archive**

In case of a simple setup without a Cassandra Archive Service installed, the backup request is generated directly after a new Snapshot Workitem has been stored. The snapshot will be backuped and remains in the database.

<img src="https://github.com/imixs/imixs-archive/raw/master/docs/imixs-backup.png"/>

To connect a Imixs-Workflow instance directly with the Backup Service you need to set the environment parameter `backup.service.endpoint` to activate the backup.

| Environment Variable    | Description                                   | Example                      |
| ----------------------- | --------------------------------------------- | ---------------------------- |
| BACKUP_SERVICE_ENDPOINT | the rest API endpoint of your backup instance | http://imixs-backup:8080/api |

Also the Imixs-Archive API is a mandatory dependency.

```xml
  <dependency>
   <groupId>org.imixs.workflow</groupId>
   <artifactId>imixs-archive-api</artifactId>
   <version>${org.imixs.archive.version}</version>
   <scope>provided</scope>
  </dependency>
```

The archive api automatically generates on each save event a new event log entry `snapshot.backup` to trigger the backup.

**Backup from a Archive**

In case of using the [Cassandra Archive Service](../imixs-archive-service/README.md) the backup request is generated after the snapshot was successful transferred into the Cassandra cluster. After the snapshot was be backuped successful, the snapshot will be removed form the database. This concept reduces the size of the database as file data is no longer stored in the database but in the Cassandra cluster.

<img src="https://github.com/imixs/imixs-archive/raw/master/docs/imixs-archive-backup.png"/>

To connect the Imixs-Archive Service with the Backup Service you need to set the environment parameter `backup.service.endpoint` at the archive service to activate the backup.

| Environment Variable    | Description                                   | Example                      |
| ----------------------- | --------------------------------------------- | ---------------------------- |
| BACKUP_SERVICE_ENDPOINT | the rest API endpoint of your backup instance | http://imixs-backup:8080/api |

The archive api automatically generates on each save event a new event log entry `snapshot.backup` to trigger the backup.

### Configuration

The Backup Service can be run in a container environment. To connect the backup service with your workflow instance the following environment parameters are mandatory:

| Environment Variable        | Description                                     | Example                               |
| --------------------------- | ----------------------------------------------- | ------------------------------------- |
| WORKFLOW_SERVICE_ENDPOINT   | the rest API endpoint of your workflow instance | http://imixs-office-workflow:8080/api |
| WORKFLOW_SERVICE_USER       | user id of the backup user                      | can be an system account              |
| WORKFLOW_SERVICE_PASSWORD   | backup user password                            |
| WORKFLOW_SERVICE_AUTHMETHOD | Auth method                                     | form                                  |
| BACKUP_FTP_HOST             | host name of the ftp service                    | ftp.foo.com                           |
| BACKUP_FTP_PORT             | port number of the ftp service                  | 21                                    |
| BACKUP_FTP_PATH             | root directory                                  | /my-backups                           |
| BACKUP_FTP_USER             | ftp user id                                     |                                       |
| BACKUP_FTP_PASSWORD         | ftp user password                               | user must have manager access         |

The backup Service periodically checks for the event log entries `snapshot.backup` and stores the corresponding snapshot into the backup space.

## Restore

To start a full restore you can use the restore feature. This feature will check each backuped snapshot against the workflow instance. If a backup snapshot does not exists in the workflow instance the restore function will restore the data of this snapshot.

<img src="https://github.com/imixs/imixs-archive/raw/master/docs/restore-01.png"/>

## Full Backup

A backup of a snapshot data is created immediately after the snapshot was created or archived. This is typically the moment when you save data in your workflow instance.
The backup requests are queued in an event log. This means that even in the situation of a temporary outage of your backup service, the backups will be generated later. If you still require a complete synchronization (e.g., after setting up a new backup system later), you can start a full backup. This transfers all unsaved snapshot data into the backup storage system.

<img src="https://github.com/imixs/imixs-archive/raw/master/docs/fullbackup-01.png"/>

## Backup Mirrors

# Development

Imixs-Archive-Backup runs as a self-contained microservice with a modern Web UI based on JDK 11 and Jakarta EE Faces 4.0. The client interacts with the Imixs-Workflow Engine via the Imixs-Rest API and the [Imixs-Melman library](https://github.com/imixs/imixs-melman).

To build the imixs-admin client manually from sources run the maven command:

      $ mvn clean install

The .war file can be deployed into any Jakarta EE Application server.

## Build the Docker Image

To build the imixs-admin Docker image manually run:

      $ mvn clean install -Pdocker

To start it from your local docker environment:

      $ docker-compose up

## Debug

The docker-compose file automatically enables the wildfly debug port 8787

To activate hotdeploy during development run:

      $ mvn manik-hotdeploy:hotdeploy
