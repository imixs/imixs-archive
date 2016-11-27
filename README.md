# imixs-archive

Imixs-Archive provides a flexible open source solution to archive electronic documents which are managed by a [Imixs-Workflow system](http://www.imixs.org) (e.g. [Imixs-Office-Workflow](http://www.office-workflow.de)).
The imixs-archive solution provies a docker container including the following features:

* OCR conversion of incomming PDF and images
* creating a new process instance based on a Imixs-Workflow model definition

The Imixs-Archive systeem is typically protected from external access. This means a document can be stored into the archive but not accessed form outside the container. This is to prevent the archive from any external modification.


## Access Control
The access control of Imixs-Archive is completly managed by the Imixs-Workflow instance. When installing the imixs-archive as a docker container the http deamon should not be accessable form an external network. A user can request a document through the imixs-workflow instance. Imixs-Worklfow grants access to a process instance based on the ACL of the corresponding workitem. If a user is not allowed to access a process instance manged by the Imixs-Workflow system he is not allowed to access linked content form the imixs-archive instance..


## OCR

imxis-archive includes a OCR conversion for PDF and Images. The outcome will be transfered into the workitem property 'ocrContent'


## Open issues

The imixs-archive docker container is still under development. Here is a list of open issues:

* provide script to transfere the file to the Imixs-Workflow Rest API
* implement a working directory and move processed documents into an archive
