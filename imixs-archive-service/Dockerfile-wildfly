FROM imixs/wildfly:1.2.7

# Imixs-Microservice Version 1.0.2
MAINTAINER ralph.soika@imixs.com

# add configuration files
COPY ./src/docker/imixsrealm.properties ${WILDFLY_CONFIG}/
COPY ./src/docker/standalone.xml ${WILDFLY_CONFIG}/

# add application artifacts
COPY ./target/imixs-archive-service.war ${WILDFLY_DEPLOYMENT}/   