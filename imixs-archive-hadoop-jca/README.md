# Imixs-Archive-Hadoop JCA 

Imixs-Archive-Hadoop-JCA provides a JCA connector.
Work is based on Adam Biens GenericJCA example:

https://github.com/dlee0113/java_ee_patterns_and_best_practices/tree/master/GenericJCA


# Build

The Maven project defines the profile 'wildfly' which includes the 'maven-rar-plugin' to generate the rar file. 

To build the rar file run the maven command:

	mvn clean install rar:rar -Pwildfly

The wildfly profile also contains a wildfly deploymetnt plugin for a autodeployment into a wildfly server. 
To build the rar file with the autodeployment option sfor wildfly run the maven command:

	mvn clean install rar:rar wildfly:deploy -Pwildfly


# Installation

See: https://docs.jboss.org/author/display/WFLY10/Resource+adapters


Deploment Strategy: https://docs.jboss.org/author/display/WFLY8/Developer+Guide#DeveloperGuide-DeploymentModuleNames



# Tutorial for Wildfly 10

see: http://www.mastertheboss.com/jboss-frameworks/ironjacamar/create-your-first-jca-connector-tutorial


## Example 

https://github.com/maroph/xadisk
