# imixs-archive

Imixs-Archive provides a flexible open source solution to archive electronic documents which are managed by a [Imixs-Workflow system](http://www.imixs.org) (e.g. [Imixs-Office-Workflow](http://www.office-workflow.de)).
The imixs-archive solution provies a docker container including the following features:

* OCR conversion of incomming PDF and images
* creating a new process instance based on a Imixs-Workflow model definition

The Imixs-Archive systeem is typically protected from external access. This means a document can be stored into the archive but not accessed form outside the container. This is to prevent the archive from any external modification.




## OCR

imxis-scan includes a OCR conversion for PDF and Images. The outcome will be transfered into the workitem property 'ocrContent'


## Open issues

The imixs-scan docker container is still under development. Here is a list of open issues:

* provide script to transfere the file to the Imixs-Workflow Rest API
* implement a working directory and move processed documents into an archive
