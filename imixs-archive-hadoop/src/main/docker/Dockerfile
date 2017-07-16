FROM imixs/wildfly:1.1.0

# Setup configuration
COPY ./configuration/imixsrealm.properties ${WILDFLY_CONFIG}/
COPY ./configuration/standalone.xml ${WILDFLY_CONFIG}/

# Deploy artefact
COPY ./imixs-office-workflow-ear/target/imixs-office-workflow-ear-*.ear ${WILDFLY_DEPLOYMENT}/
