# Imixs-Archive Signature API

The *Imixs-Archive Signature API* provides a service component to sign PDF documents attached to a Imixs-Workflow workitem. The implementation is based on the project [Apache PDFBox](https://pdfbox.apache.org/) which is providing a signing feature. On the [Github source repository](https://github.com/apache/pdfbox) several examples can be found how to sign a PDF document with PDFBox including visible signatures as used by this project. Avisual signature did not only sign a PDF document but also adds a visual element into the PDF document linked to the signature. A good overview how signing PDF files works can also be found [here](https://jvmfy.com/2018/11/17/how-to-digitally-sign-pdf-files/). 

## Signing a PDF Document

To sign a PDF document, a signature with an available algorithms is created based on the content of the PDF document. The signature is than written back into a new signed version of the origin document. The signature needs to know from what byte the content starts and where it ends, for being able to check if content not changed. To create a signature the  Crypto API [bouncycastle]( Crypto API) is used by this project.  

### Creating a keystore

The *Imixs-Archive Signature API* expects a keystore containing certificates and key pairs to create a signature. The keystore can be managed with the java command line tool *keytool* and is independent form this API and can be linked by an environment variable. 

From the keystore, a private key will be used to create the signature, and the public one will be exported in a certificate. The Keystore can keep several keys pairs, each of them is created with a proper alias to be able to distinguish them. To create keystore you  need to use the key and certificate management tool *keytool*. Keytool is provided with standard JDK, so usual no additional installation is necessary. 

To create a self-signed certificate with the alias 'imixs' run:


	$ keytool -genkey -alias imixs -keyalg RSA -sigalg SHA256withRSA -keysize 2048 -validity 3650 -keystore keystore.jks

The keystore expects a password. This password will be used later by the *Imixs-Archive Signature API* to open the keystore and to create a signature.

In the example above a 2048-bit RSA key pair valid for 365 days under the specified alias 'imixs' was generated. 
 The key pair is added into the keystore file with default ‘.jks’ extension. The keystore and certificate can be adjusted to your needs.
 
You can find more details about how to manage the keystore [here](docs/KEYTOOL.md). 
 
 
### The Signature Service

The *Imixs-Archive Signature API* provides a signing service which expects the following environment settings:

 * SIGNATURE_KEYSTORE_PATH - path from which the keystore is loaded
 * SIGNATURE_KEYSTORE_PASSWORD - the password used to unlock the keystore
 * SIGNATURE_KEYSTORE_TYPE - keystore file extension (defautl =.jks)
 * SIGNATURE_TSA_URL - an optional Time Stamping Authority (TSA) server



### The Signature Adapter

To integrate the *Imixs-Archive Signature API* the SignatureAdapter can be added to a Imixs BPMN model. The adapter automatically signs attached PDF documents. Find details about how to use an Adapter with Imixs-Workflow [here](https://www.imixs.org/doc/core/adapter-api.html).

	org.imixs.archive.signature.workflow.SignatureAdapter
	
	

	
	