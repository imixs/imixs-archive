FROM imixs/wildfly:latest
COPY ./target/imixs-archive-1.0.0.war ${WILDFLY_DEPLOYMENT}/

