# Imixs-Archive-Hadoop

The Imixs-Archive-Hadoop project provides an API to ingest Imixs-Workflow data into a Hadoop Cluster. The Imixs-Hadoop Scheduler Service automatically transfers the so-called snapshot-workitems into a Hadoop cluster. 
A snapshot-workitem is a core concept of the Imixs-Workflow engine and can be configured through the Imixs-Workflow Plug-In API. 




## HDFS Schema Design
The Hadoops's 'Schema-on-Read' model does not impose any requirements when loading data into hadoop. Nevertheless the Imixs-Archive-Hadoop provides a structured and organized data repository. All workflow data ingested into hadoop is partitioned by the creation YEAR and MONTH component of the process instance into a directory hirrarchie

    /data/[workflow-instance]/YEAR/MONTH/[SNAPSHOT-UNIQUEID].xml

See the following example 

    /data/company/2017/01/333444.1111.2222-21555122.xml
    
All data is stored in a semistructured XML format based on the Imixs-XML Schema.
With this schema design it is 



## Hadoop Rest API
The ingestion mechanism is based on the Hadoop Rest API 'HttpFS'.





