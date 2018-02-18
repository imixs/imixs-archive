package org.imixs.workflow.archive.cassandra;

import java.io.StringWriter;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.engine.PropertyService;
import org.imixs.workflow.xml.XMLItemCollection;
import org.imixs.workflow.xml.XMLItemCollectionAdapter;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

/**
 * Service to persist the content of a Imixs Document into a Cassandra keystore.
 * 
 * The service saves the content in XML format. The size of an XML
 * representation of a Imixs document is only slightly different in size from
 * the serialized map object. This is the reason why we do not store the
 * document map in a serialized object format.
 * 
 * @author rsoika
 * 
 */
@Stateless
public class ClusterService {

	public static String PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT = "archive.cluster.contactpoint";
	public static String PROPERTY_ARCHIVE_CLUSTER_KEYSPACE = "archive.cluster.keyspace";

	@Resource
	SessionContext ejbCtx;

	@EJB
	DocumentService documentService;

	@EJB
	PropertyService propertyService;

	/**
	 * Test the local connection
	 */
	public void testCluster() {

	}

	/**
	 * Helper method to get a session for the configured keyspace
	 */
	public Session connect() {

		String contactPoint = propertyService.getProperties().getProperty(PROPERTY_ARCHIVE_CLUSTER_CONTACTPOINT);
		String keySpace = propertyService.getProperties().getProperty(PROPERTY_ARCHIVE_CLUSTER_KEYSPACE);

		Cluster cluster = Cluster.builder().addContactPoint(contactPoint).build();
		cluster.init();
		Session session = cluster.connect(keySpace);
		return session;
	}

	public void save(ItemCollection itemCol, Session session) throws JAXBException {

		PreparedStatement ps1 = session
				.prepare("insert into document (id, type, created, modified, data) values (?, ?, ?, ?, ?)");

		BoundStatement bound = ps1.bind().setString("id", itemCol.getUniqueID()).setString("type", itemCol.getType())
				.setTimestamp("created", itemCol.getItemValueDate("$created"))
				.setTimestamp("modified", itemCol.getItemValueDate("$modified")).setString("data", getXML(itemCol));

		session.execute(bound);
	}

	/**
	 * This helper method creates the imixs-data table if not yet exists
	 */
	private void createTable(Session session) {
		String statement = "CREATE TABLE IF NOT EXISTS document (id text PRIMARY KEY, type text, created timestamp, modified timestamp, data text);";
		session.execute(statement);

	}

	/**
	 * Converts a ItemCollection into a xml string
	 * 
	 * @param itemcol
	 * @return
	 * @throws JAXBException
	 */
	private String getXML(ItemCollection itemCol) throws JAXBException {
		String result = null;
		// convert the ItemCollection into a XMLItemcollection...
		XMLItemCollection xmlItemCollection = XMLItemCollectionAdapter.putItemCollection(itemCol);

		// marshal the Object into an XML Stream....
		StringWriter writer = new StringWriter();
		JAXBContext context = JAXBContext.newInstance(XMLItemCollection.class);
		Marshaller m = context.createMarshaller();
		m.marshal(xmlItemCollection, writer);

		result = writer.toString();
		return result;

	}
}
