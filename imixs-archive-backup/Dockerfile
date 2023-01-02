FROM quay.io/wildfly/wildfly:27.0.1.Final-jdk11

LABEL description="Imixs-Archive-Backup"
LABEL maintainer="ralph.soika@imixs.com"

# Copy EclipseLink and Postgres Driver
COPY ./docker/configuration/wildfly/modules/ /opt/jboss/wildfly/modules/

# Setup configuration
COPY ./docker/configuration/wildfly/standalone.xml /opt/jboss/wildfly/standalone/configuration/

# Deploy artefact
ADD ./target/*.war /opt/jboss/wildfly/standalone/deployments/
WORKDIR /opt/jboss/wildfly

# Add the Wildfly start script 
USER root
ADD ./docker/configuration/wildfly_start.sh $JBOSS_HOME/ 
RUN chmod +x $JBOSS_HOME/wildfly_start.sh
USER jboss

# Expose the ports we're interested in
EXPOSE 8080 8787 9990

CMD ["/opt/jboss/wildfly/wildfly_start.sh"]

# Run with management interface
#CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]
