# Imixs-Archive UI

The sub-module Imixs-Archive-UI provides the ui functionality for the imixs-archive system to be integrated into enterprise application (see the Imixs-Office-Workflow project).


# Deployment

To deploy imixs-archive-ui into Imixs-Office-Workflow the following maven configuration is needed:

 1) Add the following artifact versions into the master pom.xml


		<!-- Imixs-Archive -->
		<org.imixs.archive.version>0.0.2-SNAPSHOT</org.imixs.archive.version>
	
 2) Add the following dependencies into the section dependencyManagement of the master pom.xml:


		<!-- Imixs-Archive -->
		<dependency>
			<groupId>org.imixs.workflow</groupId>
			<artifactId>imixs-archive-ui</artifactId>
			<version>${org.imixs.archive.version}</version>
			<scope>provided</scope>
		</dependency>
		

 3) Add the following dependencies into the pom.xml of the war module:

		<!-- Imixs-Archive -->
		<dependency>
			<groupId>org.imixs.workflow</groupId>
			<artifactId>imixs-archive-ui</artifactId>
			<scope>runtime</scope>
		</dependency>

