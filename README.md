# imixs-scan

The imixs-scan solution provies a docker container to import scanned documents into the imixs workflow system.

## OCR

imxis-scan includes a OCR conversion for PDF and Images. The outcome will be transfered into the workitem property 'ocrContent'


## Open issues

The imixs-scan docker container is still under development. Here is a list of open issues:

* provide script to transfere the file to the Imixs-Workflow Rest API
* implement a working directory and move processed documents into an archive
