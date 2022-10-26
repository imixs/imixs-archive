# The CSV Importer

The CDI bean *CSVImporterService* is a generic implementation to import documents form a CSV File located on a FTP server. The implementation access the FTP server via FSTP on the default port 990. 

The selector attribute is used to define the path to the target file with the file extension '.csv'. For each entry in the CSV file a new document instance will be created. Currently no workflow processing is supported.

The *CSVImporterService* verifies the content of a CSV file by a MDA checksum. Only if the MDA checksum has changed the import process will be started. The MDA will be printed into the log and can be verified by a administrator. 

## The CSV Options

The following option entries are mandatory:

 - **type** - defines the document type of the stored entries 
 - **key** - defines the key item to be used to select the entity by a unique key.
 
The options 'type' and 'key' are mandatory. 
 
**Note:** The *CSVImporterService* does currently not support combined keys. It must be ensured that the CSV files contains a column with unique keys. 




