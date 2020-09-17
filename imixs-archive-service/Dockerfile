FROM payara/micro:5.2020.4
#5.201

LABEL description="Imixs-Microservice"
LABEL maintainer="ralph.soika@imixs.com"

# Deploy artefacts
COPY ./target/imixs-archive-service.war $DEPLOY_DIR

#ENV MAX_HEAP 2g
#ENV MIN_HEAP 1g

# add lauch options
CMD ["--deploymentDir", "/opt/payara/deployments", "--rootDir", "/opt/payara/config"]




