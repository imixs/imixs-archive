# The FTP Importer

The CDI bean *FTPImporterService* is a concrete implementation to import documents form a FTP server. The implementation access the FTP server via FSTP on the default port 990. 

The selector attribute is used to define an optional directory. If no selector is specified the documents are read from the / root directory. 

**Note:** The *FTPImporterService* expects a secure FTPS connection. 
FTPS is FTP using the [SSL/TLS protocol](https://en.wikipedia.org/wiki/Secure_Sockets_Layer) for encryption. FTPS adds support for the Transport Layer Security (TLS) and the Secure Sockets Layer (SSL) cryptographic protocols. This is different from  from the SCP/SFTP which is not supported. If your FTP data source does not support FTPS the *FTPImporterService* can not be used.
