FROM payara/micro:5.201

LABEL description="Imixs-Microservice"
LABEL maintainer="ralph.soika@imixs.com"

# Deploy artefacts
COPY ./target/imixs-archive-service.war $DEPLOY_DIR

# add lauch options
CMD ["--deploymentDir", "/opt/payara/deployments", "--rootDir", "/opt/payara/config"]




