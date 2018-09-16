package org.imixs.workflow.archive.cassandra.services;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

/**
 * The DocumentService is used to store a imixs document into the cluster keyspace.
 * 
 * @author rsoika
 * 
 */
@Stateless
public class DocumentService {


	private static final String REGEX_SNAPSHOTID = "([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}-[0-9]{13,15})";
	private static Logger logger = Logger.getLogger(DocumentService.class.getName());



	@EJB
	ClusterService clusterService;

	/**
	 * This method saves a ItemCollection into a specific KeySpace.
	 * 
	 * @param itemCol
	 * @param session
	 * @throws ImixsArchiveException
	 */
	public void saveDocument(ItemCollection itemCol, String keyspace) throws ImixsArchiveException {
		byte[] data = null;
		PreparedStatement statement = null;
		BoundStatement bound = null;
		
		
		String snapshotID = itemCol.getUniqueID();

		
		if (!isSnapshotID(snapshotID)) {
			throw new IllegalArgumentException("invalid item '$snapshotid'");
		}
		logger.finest("......save document" + snapshotID);

		if (!itemCol.hasItem("$modified")) {
			throw new IllegalArgumentException("missing item '$modified'");
		}

		// extract $snapshotid 2de78aec-6f14-4345-8acf-dd37ae84875d-1530315900599
		String[] snapshotSegments = snapshotID.split("-");
		String snapshotDigits = snapshotSegments[snapshotSegments.length - 1];
		String originUnqiueID = snapshotID.substring(0, snapshotID.lastIndexOf("-"));

		// create byte array from XMLDocument...
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			JAXBContext context;
			context = JAXBContext.newInstance(XMLDocument.class);
			Marshaller m = context.createMarshaller();
			XMLDocument xmlDocument = XMLDocumentAdapter.getDocument(itemCol);
			m.marshal(xmlDocument, outputStream);
			data = outputStream.toByteArray();
		} catch (JAXBException e) {
			throw new ImixsArchiveException(ImixsArchiveException.INVALID_DOCUMENT_OBJECT, e.getMessage(), e);
		}

		// get session from archive....
		Session session = clusterService.getArchiveSession(keyspace);

		// upset document....
		statement = session.prepare("insert into snapshots (id, data) values (?, ?)");
		bound = statement.bind().setString("id", itemCol.getUniqueID()).setBytes("data", ByteBuffer.wrap(data));
		session.execute(bound);

		// upset document_snapshots....
		statement = session.prepare("insert into snapshots_by_uniqueid (uniqueid, snapshot) values (?, ?)");
		bound = statement.bind().setString("uniqueid", originUnqiueID).setString("snapshot", snapshotDigits);
		session.execute(bound);

		// upset document_modified....
		LocalDate ld = LocalDate.fromMillisSinceEpoch(itemCol.getItemValueDate("$modified").getTime());
		statement = session.prepare("insert into snapshots_by_modified (date, id) values (?, ?)");
		bound = statement.bind().setDate("date", ld).setString("uniqueid", originUnqiueID).setString("id",
				itemCol.getUniqueID());
		session.execute(bound);
	}

	/**
	 * This method returns true if the given id is a valid Snapshot id (UUI +
	 * timestamp
	 * 
	 * @param uid
	 * @return
	 */
	public static boolean isSnapshotID(String uid) {
		return uid.matches(REGEX_SNAPSHOTID);
	}

}
