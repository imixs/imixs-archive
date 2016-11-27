FROM imixs/wildfly:latest

# Imixs-Microservice Version 1.0.0
MAINTAINER ralph.soika@imixs.com

USER root

# add script files
ADD imixs-ocr.sh /opt/imixs-scan/

