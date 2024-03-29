# Imixs-Archive-Exporter

The _Imixs-Archive-Exporter Service_ is a microservice to export documents form a Imixs-Workflow data into an external storage like a FTP server or a local filesystem. This export service is decoupled from the a Imixs-Workflow Instance and can be run in a independent server environment. You can run this microservice on different hardware or in a external cluster.

<img src="https://github.com/imixs/imixs-archive/raw/master/docs/imixs-archive-exporter-screen.png"/>

The Exporter Service can be run as an independent Microservice connecting to an Imixs-Workflow Instance. The exporter processes EventLog entries generated by the Workflow Instance to store file attachments into a separate data source like a local filesystem or a FTP server.

# Export EventLog Entries

The Imixs-Archive-Exporter is controlled by corresponding [EventLog Entries](https://www.imixs.org/doc/engine/eventlogservice.html). An EventLog entity describes a unique event created during the processing life-cycle of a workitem or the update life-cycle of a Document. An eventLog entry for the Imixs-Archive-Exporter can be created by a BPMN Event result:

```xml
<eventlog name="file.export">
	<ref><itemvalue>$uniqueid</itemvalue></ref>
	<timeout>60000</timeout>
        <document>
             <path>my-documents/</path>
             <filter>\.pdf$</filter>
        </document>
 </eventlog>
```

On each corresponding workflow event a new event log entry `file.export` will be created. The Imixs-Archive-Exporter periodically check this event log entries and exports the corresponding files into a configured data space.

**Note:** To process new eventLog entries you need to add the `EventLogPlugin` to your workflow model!

```
org.imixs.workflow.engine.plugins.EventLogPlugin
```

## Path

The optional event parameter `path` can be used to specify the target path for the file to be exported.

## Filter

The optional event parameter `filter` can be used to specify a regular expression to match the file name. This option can be used to export only a part of file from a workitem. For example the regular expression `\.pdf$` exports only files with the suffix '.pdf'.

# Configuration

The Exporter Service can be run in a container environment. To connect the exporter service can be configured via environment variables:

| Environment Param           | Description                                                                          | Example                 |
| --------------------------- | ------------------------------------------------------------------------------------ | ----------------------- |
| WORKFLOW_SERVICE_ENDPOINT   | The Rest-API endpoint of a Imixs Workflow Instance providing export eventLog entries | http://my-host:8080/api |
| WORKFLOW_SERVICE_USER       | Workflow user to access the Workflow Instance. This user must have manager access.   |                         |
| WORKFLOW_SERVICE_PASSWORD   | Workflow user password                                                               |                         |
| WORKFLOW_SERVICE_AUTHMETHOD | Authentication method (form/basic)                                                   | form                    |
| EVENTLOG_TOPIC              | The EventLog topic (default = file.export), can be overwritten                       | file.export             |
| EVENTLOG_DEADLOCK           | EventLog timeout in ms (default = 60000)                                             |                         |
| EXPORT_PATH                 | Target directory to write files                                                      |                         |

The following environment variables are optional and depend on the Jakarta EE Runtime:

| Environment Param   | Description                 | Example                       |
| ------------------- | --------------------------- | ----------------------------- |
| METRICS_ENDPOINT    | endpoint for metrics API    | http://localhost:9990/metrics |
| HEALTH_ENDPOINT     | endpoint for health API     | http://localhost:9990/health  |
| EXPORT_FTP_HOST     | Optional: FTP Server host   | my-ftp-server                 |
| EXPORT_FTP_PORT     | Optional: FTP Server port   | 21                            |
| EXPORT_FTP_USER     | Optional: FTP user          |                               |
| EXPORT_FTP_PASSWORD | Optional: FTP user password |                               |

The Imixs-Workflow instance is not needed to be configured. Only a service user account need to be provided.

# Monitoring

The Imixs-Archive-Exporter provides a Health and Metrics Endpoint that can be used to monitor the status of the service.

The Health-Check endpoint 'http://localhost:9991/health' indicates if the server and exporter service are running.

The Metric endpoint `http://localhost:9991/metric` provides detailed information about the processing status. The metrics are provided in a prometeus format and can be analyzed with corresponding Tools like Grafana.

To run an example with A Prometeus and Grafana Service see the Docker-Compose Example `docker-compose-monitor.yml`. You can start the example with

      $ docker-compose -f docker/docker-compose-monitor.yml

The Prometeus Dashboard can be loaded via : http://localhost:9090

<img src="https://github.com/imixs/imixs-archive/raw/master/docs/imixs-archive-exporter-prometheus.png"/>

The Grafana Dashboard can be loaded via : http://localhost:3000/login

# Development

Imixs-Archive-Exporter runs as a self-contained microservice with a modern Web UI based on JDK 11 and Jakarta EE Faces 4.0. The client interacts with the Imixs-Workflow Engine via the Imixs-Rest API and the [Imixs-Melman library](https://github.com/imixs/imixs-melman).

To build the imixs-admin client manually from sources run the maven command:

      $ mvn clean install

The .war file can be deployed into any Jakarta EE Application server.

## Build the Docker Image

To build the imixs-admin Docker image manually run:

      $ mvn clean install -Pdocker

To start it from your local docker environment:

      $ docker-compose up

**Note:** The docker image is based on the official Wildfly Image but with a extended configuration regarding the Eclipse Microprofile configuration. You can change the configuration by editing the file `configuration/wildfly/standalone.xml`.

### Debug

The docker-compose file automatically enables the wildfly debug port 8787

To activate hotdeploy during development run:

      $ mvn manik-hotdeploy:hotdeploy
