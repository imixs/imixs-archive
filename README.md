# imixs-archive

Imixs-Archive is a microservice providing a RestFull interface to store documents from any application into a central archive system.
The Imixs-Archive service can be used in conjunction with [Imixs-Workflow](http://www.imixs.org).


## Data Consistency 

Imixs-Archive guarantees the consistency of the stored data by calculating a MD5 checksum for each document stored into the archive. The checksum is part of the access-URL returned by the archive system after a document was stored. If the access-URL specified later by the client to read the data did not match, an error code is returned. 


## Access Control
The access to data, written into the Imixs-Archive, should be ideally managed completely by the [Imixs-Workflow](http://www.imixs.org) engine. Imixs-Workflow supports a multiple-level security model, that offers a great space of flexibility while controlling the access to all parts of a workitem. 


## Imixs OCR

Imixs-Archive provides a shell script to perform a OCR scan on documents managed by the system.  
This script is based on the tesseract library. The script automatically converts PDF files into a TIF format, so this 
script can be used for images as also for PDF files.  The text result is stored into a file ${FILENAME}.txt





# Docker
The Imixs Docker Container '[imixs/archive](https://github.com/imixs-docker/archive)' can be used to run a Imixs-Archive on a Docker host.

When installing the imixs-archive as a docker container the http deamon should not be accessible form an external network. A user can request a document through the imixs-workflow instance. Imixs-Worklfow grants access to a process instance based on the ACL of the corresponding workitem. If a user is not allowed to access a process instance managed by the Imixs-Workflow system, he is not allowed to access linked content form the imixs-archive instance.

Furthermore the Imixs-Archive rest service can be protected using the JAAS framework. 


## Development

During development the imixs/archive docker container can be used with mounting an external deployments/ folder:

	docker run --name="archive" -d -p 8080:8080 -p 9990:9990 \
         -e WILDFLY_PASS="adminadmin" \
         -v ~/git/imixs-archive/deployments:/opt/wildfly/standalone/deployments/:rw \
         imixs/archive

Logfiles can be monitored with 

	docker logs -f archive



