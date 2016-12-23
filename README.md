# imixs-archive

Imixs-Archive is a microservice providing a RestFull interface to archive documents from any application into a central archive system.
The Imixs-Archive service is part of the Imixs Docker Contaienr '[imixs/archive](https://github.com/imixs-docker/archive)'.



## Access Control
The access control of Imixs-Archive is completely managed by an Imixs-Workflow instance. When installing the imixs-archive as a docker container the http deamon should not be accessable form an external network. A user can request a document through the imixs-workflow instance. Imixs-Worklfow grants access to a process instance based on the ACL of the corresponding workitem. If a user is not allowed to access a process instance manged by the Imixs-Workflow system he is not allowed to access linked content form the imixs-archive instance..

