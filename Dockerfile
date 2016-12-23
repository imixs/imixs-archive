FROM imixs/wildfly:latest

# Imixs-Microservice Version 1.0.0
MAINTAINER ralph.soika@imixs.com

USER root

# Set the working directory to 'imixs' user home directory
WORKDIR /opt

# Add the hadoop distribution to /opt, and make imixs the owner of the extracted tar content
# Make sure the distribution is available from a well-known place
ENV HADOOP_VERSION 2.7.3
RUN curl http://mirror.softaculous.com/apache/hadoop/common/hadoop-$HADOOP_VERSION/hadoop-$HADOOP_VERSION.tar.gz | tar zx \
 && ln -s /opt/hadoop-$HADOOP_VERSION/ /opt/hadoop 


# change owner of /opt/hadoop/
RUN chown -R imixs:imixs /opt/hadoop/

USER imixs
#Hadoop variables
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/
ENV HADOOP_INSTALL=/opt/hadoop
ENV PATH=$PATH:$HADOOP_INSTALL/bin
ENV PATH=$PATH:$HADOOP_INSTALL/sbin
ENV HADOOP_HOME=$HADOOP_INSTALL
ENV HADOOP_MAPRED_HOME=$HADOOP_INSTALL
ENV HADOOP_COMMON_HOME=$HADOOP_INSTALL
ENV HADOOP_HDFS_HOME=$HADOOP_INSTALL
ENV HADOOP_YARN_HOME=$HADOOP_INSTALL
ENV HADOOP_CONF_DIR=$HADOOP_INSTALL/etc/hadoop


# add script files
ADD imixs-ocr.sh /opt/imixs/


