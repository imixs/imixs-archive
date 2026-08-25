# Define default wildfly version
ARG WILDFLY_VERSION=29.0.1.Final-jdk17

# Base image
FROM quay.io/wildfly/wildfly:${WILDFLY_VERSION}

# Re-declare ARG after FROM (this is important - ARGs before FROM aren't available after FROM without redeclaring)
ARG WILDFLY_VERSION

LABEL description="Imixs-Archive-Backup"
LABEL maintainer="ralph.soika@imixs.com"

# Setup configuration
COPY ./docker/configuration/wildfly/${WILDFLY_VERSION}/standalone.xml /opt/jboss/wildfly/standalone/configuration/

# Deploy artefact
ADD ./target/*.war /opt/jboss/wildfly/standalone/deployments/

WORKDIR /opt/jboss

CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]
