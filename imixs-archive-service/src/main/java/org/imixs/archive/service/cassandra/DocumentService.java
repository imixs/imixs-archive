package org.imixs.archive.service.cassandra;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.scheduler.SchedulerService;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

/**
 * The DocumentService is used to store a imixs document into the cluster
 * keyspace.
 * 
 * @author rsoika
 * 
 */
@Stateless
public class DocumentService {

	private static final String REGEX_SNAPSHOTID = "([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}-[0-9]{13,15})";
	private static Logger logger = Logger.getLogger(DocumentService.class.getName());

	// table columns
	public static final String COLUMN_SNAPSHOT = "snapshot";
	public static final String COLUMN_MODIFIED = "modified";
	public static final String COLUMN_UNIQUEID = "uniqueid";
	public static final String COLUMN_DATA = "data";

	// cqlsh statements
	public static final String STATEMENT_UPSET_SNAPSHOTS = "insert into snapshots (snapshot, data) values (?, ?)";
	public static final String STATEMENT_UPSET_SNAPSHOTS_BY_UNIQUEID = "insert into snapshots_by_uniqueid (uniqueid, snapshot) values (?, ?)";
	public static final String STATEMENT_UPSET_SNAPSHOTS_BY_MODIFIED = "insert into snapshots_by_modified (modified, snapshot) values (?, ?)";

	@EJB
	ClusterService clusterService;

	@EJB
	SchedulerService schedulerService;

	/**
	 * This method saves a ItemCollection into a specific KeySpace.
	 * 
	 * @param itemCol
	 * @param session
	 * @throws ArchiveException
	 */
	public void saveDocument(ItemCollection itemCol) throws ArchiveException {

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
		//String[] snapshotSegments = snapshotID.split("-");
		// String snapshotDigits = snapshotSegments[snapshotSegments.length - 1];
		String originUnqiueID = snapshotID.substring(0, snapshotID.lastIndexOf("-"));

		Session session = null;
		try {
			// get session from archive....
			session = clusterService.getArchiveSession();

			// upset document....
			statement = session.prepare(STATEMENT_UPSET_SNAPSHOTS);
			bound = statement.bind().setString(COLUMN_SNAPSHOT, itemCol.getUniqueID()).setBytes(COLUMN_DATA,
					ByteBuffer.wrap(getRawData(itemCol)));
			session.execute(bound);

			// upset document_snapshots....
			statement = session.prepare(STATEMENT_UPSET_SNAPSHOTS_BY_UNIQUEID);
			bound = statement.bind().setString(COLUMN_UNIQUEID, originUnqiueID).setString(COLUMN_SNAPSHOT,
					itemCol.getUniqueID());
			session.execute(bound);

			// upset document_modified....
			LocalDate ld = LocalDate.fromMillisSinceEpoch(itemCol.getItemValueDate("$modified").getTime());
			statement = session.prepare(STATEMENT_UPSET_SNAPSHOTS_BY_MODIFIED);
			bound = statement.bind().setDate(COLUMN_MODIFIED, ld).setString(COLUMN_SNAPSHOT, itemCol.getUniqueID());
			session.execute(bound);

		} finally {
			if (session != null) {
				session.close();
			}
		}

	}

	/**
	 * Converts a ItemCollection into a XMLDocument and returns the byte data.
	 * 
	 * @param itemCol
	 * @return
	 * @throws ArchiveException
	 */
	public static byte[] getRawData(ItemCollection itemCol) throws ArchiveException {
		byte[] data = null;
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
			throw new ArchiveException(ArchiveException.INVALID_DOCUMENT_OBJECT, e.getMessage(), e);
		}

		return data;
	}

	/**
	 * Converts a byte array into a XMLDocument and returns the ItemCollection
	 * object.
	 * @throws ArchiveException 
	 *
	 */
	public static ItemCollection getItemCollection(byte[] source) throws ArchiveException {

		ByteArrayInputStream bis = new ByteArrayInputStream(source);
		try {
			JAXBContext context;
			context = JAXBContext.newInstance(XMLDocument.class);
			Unmarshaller m = context.createUnmarshaller();
			Object jaxbObject = m.unmarshal(bis);
			if (jaxbObject == null) {
				throw new RuntimeException("readCollection error - wrong xml file format - unable to read content!");
			}
			XMLDocument xmlDocument = (XMLDocument) jaxbObject;
			return XMLDocumentAdapter.putDocument(xmlDocument);
		} catch (JAXBException e) {
			throw new ArchiveException(ArchiveException.INVALID_DOCUMENT_OBJECT, e.getMessage(), e);
		}

		
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

	
	/**
	 * Returns the sycpoint based on the last syncronized document 
	 * @return
	 */
	public long getSyncpoint() {
		logger.info("*** TODO compute syncpoint");
		
		return 0;
	}
	
	
	
	/**
	 * This method initializes the scheduler.
	 * 
	 */
	public boolean startScheduler() {
		Session session = null;
		try {
			logger.info("...init imixsarchive keyspace ...");
			session = clusterService.getArchiveSession();
			if (session != null) {
				// start archive schedulers....
				logger.info("...starting schedulers...");
				schedulerService.start();
				return true;
			} else {
				logger.warning("...Failed to initalize imixsarchive keyspace!");
				return false;
			}

		} catch (Exception e) {
			logger.warning("...Failed to initalize imixsarchive keyspace: " + e.getMessage());
			return false;

		} finally {
			if (session != null) {
				session.close();
			}
		}
	}

}
