# Imixs-Archive-Exporter

The _Imixs-Archive-Exporter Service_ is a microservice to export documents form a Imixs-Workflow data into an external storage like a FTP server or a local filesystem. This export service is decoupled from the a Imixs-Workflow Instance and can be run in a independent server environment. You can run this microservice on different hardware or in a external cluster.

<img src="https://github.com/imixs/imixs-archive/raw/master/docs/imixs-backup-screen.png"/>

# Configuration

The Expoter Service can be run in a container environment. To connect the exporter service with your workflow instance the following environment parameters are mandatory:

      WORKFLOW_SERVICE_ENDPOINT: [REST-API-ENDPOINT-OF-WORKFLOW-INSTANCE]
      WORKFLOW_SERVICE_USER: [BACKUP-USER]
      WORKFLOW_SERVICE_PASSWORD: [PASSWORD]
      WORKFLOW_SERVICE_AUTHMETHOD: [AUTHMETHOD]

      EXPORT_FTP_HOST: [FTP-SERVER-ADDRESS]
      EXPORT_FTP_PATH: [DIRECTORY]
      EXPORT_FTP_PORT: "21"
      EXPORT_FTP_USER: [USER]
      EXPORT_FTP_PASSWORD: [PASSWORD]

**Note:** The EXPORT-USER must have manager access.

The following envrionent variables are optional and depend on the Jakarta EE Runtime:

- METRICS_ENDPOINT = endpoint for metrics API (default = http://localhost:9990/metrics)
- HEALTH_ENDPOINT = endpoint for health API (default = http://localhost:9990/health)

To connect a Imixs-Workflow instance with the Exporter Service you need to add the Imixs-Archive API as a dependency.

```xml
  <dependency>
   <groupId>org.imixs.workflow</groupId>
   <artifactId>imixs-archive-api</artifactId>
   <version>${org.imixs.archive.version}</version>
   <scope>provided</scope>
  </dependency>
```

Next set the environment parameter `backup.service.endpoint` to activate the backup.

      EXPORT_SERVICE_ENDPOINT: [REST-API-ENDPOINT-OF-EXPORTSERVICE]

On each save event a new event log entry `snapshot.export` will be created. The backup Service periodically check this event log entries and stores the corresponding snapshot into the backup space.

# Development

Imixs-Archive-Exporter runs as a self-contained microservice with a modern Web UI based on JDK 11 and Jakarta EE Faces 4.0. The client interacts with the Imixs-Workflow Engine via the Imixs-Rest API and the [Imixs-Melman library](https://github.com/imixs/imixs-melman).

To build the imixs-admin client manually from sources run the maven command:

      $ mvn clean install

The .war file can be deployed into any Jakarta EE Application server.

## Build the Docker Image

To build the imixs-admin Docker image manually run:

      $ mvn clean install -Pdocker

To start it from your local docker environment:

docker-compose up

### Debug

The docker-compose file automaticaly enables the wildfly debug port 8787

To activate hotdeploy during development run:

      $ mvn manik-hotdeploy:hotdeploy
