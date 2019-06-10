package org.imixs.archive.service.cassandra;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.scheduler.SyncService;
import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;

//import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.LocalDate;
//import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;

/**
 * The SnapshotService is used to store a imixs snapshot into the cluster
 * keyspace.
 * 
 * @author rsoika
 * 
 */
@Stateless
public class DataService {

	public final static String ITEM_MD5_CHECKSUM = "md5checksum";

	private static final String REGEX_SNAPSHOTID = "([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}-[0-9]{13,15})";
	private static final String REGEX_OLD_SNAPSHOTID = "([0-9a-f]{8}-.*|[0-9a-f]{11}-.*)";

	private static Logger logger = Logger.getLogger(DataService.class.getName());

	// table columns
	public static final String COLUMN_SNAPSHOT = "snapshot";
	public static final String COLUMN_MODIFIED = "modified";
	public static final String COLUMN_UNIQUEID = "uniqueid";
	public static final String COLUMN_DATA = "data";
	public static final String COLUMN_MD5 = "md5";

	// cqlsh statements
	public static final String STATEMENT_UPSET_SNAPSHOTS = "insert into snapshots (snapshot, data) values (?, ?)";
	public static final String STATEMENT_UPSET_SNAPSHOTS_BY_UNIQUEID = "insert into snapshots_by_uniqueid (uniqueid, snapshot) values (?, ?)";
	public static final String STATEMENT_UPSET_SNAPSHOTS_BY_MODIFIED = "insert into snapshots_by_modified (modified, snapshot) values (?, ?)";

	public static final String STATEMENT_UPSET_DOCUMENTS = "insert into documents (md5, sort_id, data_id) values (?, ?, ?)";
	public static final String STATEMENT_UPSET_DOCUMENTS_DATA = "insert into documents_data (data_id, data) values (?, ?)";

	public static final String STATEMENT_UPSET_SNAPSHOTS_BY_DOCUMENT = "insert into snapshots_by_document (md5, snapshot) values (?, ?)";

	public static final String STATEMENT_SELECT_SNAPSHOT = "select * from snapshots where snapshot='?'";
	public static final String STATEMENT_SELECT_METADATA = "select * from snapshots where snapshot='0'";
	public static final String STATEMENT_SELECT_SNAPSHOT_ID = "select snapshot from snapshots where snapshot='?'";
	public static final String STATEMENT_SELECT_MD5 = "select md5 from documents where md5='?'";
	public static final String STATEMENT_SELECT_DOCUMENTS = "select * from documents where md5='?'";
	public static final String STATEMENT_SELECT_DOCUMENTS_DATA = "select * from documents_data where data_id='?'";

	public static final String STATEMENT_SELECT_SNAPSHOTS_BY_UNIQUEID = "select * from snapshots_by_uniqueid where uniqueid='?'";
	public static final String STATEMENT_SELECT_SNAPSHOTS_BY_MODIFIED = "select * from snapshots_by_modified where modified='?'";

	@EJB
	ClusterService clusterService;

	@EJB
	SyncService schedulerService;

	@Inject
	protected Event<ArchiveEvent> events;

	/**
	 * This method saves a ItemCollection into a specific KeySpace.
	 * <p>
	 * The method expects a valid session instance which must be closed by the
	 * client.
	 * 
	 * @param snapshot
	 *            - ItemCollection object
	 * @param session
	 *            - cassandra session
	 * @throws ArchiveException
	 */
	public void saveSnapshot(ItemCollection snapshot, Session session) throws ArchiveException {

		String snapshotID = snapshot.getUniqueID();

		if (!isSnapshotID(snapshotID)) {
			throw new IllegalArgumentException("unexpected '$snapshotid' fromat: " + snapshotID);
		}
		logger.finest("......save document" + snapshotID);

		if (!snapshot.hasItem("$modified")) {
			throw new IllegalArgumentException("missing item '$modified' for snapshot " + snapshotID);
		}

		// extract $snapshotid 2de78aec-6f14-4345-8acf-dd37ae84875d-1530315900599
		String originUnqiueID = getUniqueID(snapshotID);

		// extract $file content into the table 'documents'....
		extractDocuments(snapshot, session);

		session.execute(new SimpleStatement(STATEMENT_UPSET_SNAPSHOTS, snapshot.getUniqueID(),
				ByteBuffer.wrap(getRawData(snapshot))));

		session.execute(
				new SimpleStatement(STATEMENT_UPSET_SNAPSHOTS_BY_UNIQUEID, originUnqiueID, snapshot.getUniqueID()));

		// upset snapshots_by_modified....
		LocalDate ld = LocalDate.fromMillisSinceEpoch(snapshot.getItemValueDate("$modified").getTime());

		session.execute(new SimpleStatement(STATEMENT_UPSET_SNAPSHOTS_BY_MODIFIED, ld, snapshot.getUniqueID()));

		// Finally we fire the DocumentEvent ON_DOCUMENT_SAVE
		if (events != null) {
			events.fire(new ArchiveEvent(snapshot, ArchiveEvent.ON_ARCHIVE));
		} else {
			logger.warning("Missing CDI support for Event<ArchiveEvent> !");
		}

	}

	/**
	 * This helper method extracts the content of attached documents and stores the
	 * content into the documents table space. A document is uniquely identified by
	 * its md5 checksum.
	 * 
	 * @param itemCol
	 * @throws ArchiveException
	 */
	private void extractDocuments(ItemCollection itemCol, Session session) throws ArchiveException {
		// empty data...
		byte[] empty = {};
		List<FileData> files = itemCol.getFileData();
		for (FileData fileData : files) {
			// first verify if content is already stored.
			try {
				logger.finest("... extract fileData objects: " + files.size() + " fileData objects found....");

				if (fileData.getContent() != null && fileData.getContent().length > 0) {
					String md5 = fileData.generateMD5();

					// test if md5 already stored....
					String sql = STATEMENT_SELECT_MD5;
					sql = sql.replace("'?'", "'" + md5 + "'");

					logger.finest("......search MD5 entry: " + sql);

					ResultSet rs = session.execute(sql);
					Row row = rs.one();
					if (row == null) {
						// not yet stored so extract the conent
						storeDocument(md5, fileData.getContent(), session);
					} else {
						logger.finest(
								"......update fildata not necessary because object: " + md5 + " is already stored!");
					}

					// updset documents_by_snapshot.... (needed for deletion)
					session.execute(
							new SimpleStatement(STATEMENT_UPSET_SNAPSHOTS_BY_DOCUMENT, md5, itemCol.getUniqueID()));

					// remove file content from itemCol
					logger.finest("drop content for file '" + fileData.getName() + "'");
					itemCol.addFileData(new FileData(fileData.getName(), empty, fileData.getContentType(),
							fileData.getAttributes()));
				}
			} catch (NoSuchAlgorithmException e) {
				throw new ArchiveException(ArchiveException.MD5_ERROR,
						"can not compute md5 of document - " + e.getMessage());
			}
		}
	}

	/**
	 * This method stores a singel documetn identified by teh MD5 checksum.
	 * <p>
	 * The method splits the data into 1mb blocks stored in the table
	 * 'documents_data'
	 * 
	 * 
	 * @param md5
	 * @param data
	 * @param session
	 */
	private void storeDocument(String md5, byte[] data, Session session) {

		// split the data into 1md blocks....
		DocumentSplitter documentSplitter = new DocumentSplitter(data);
		Iterator<byte[]> it = documentSplitter.iterator();
		int sort_id = 0;
		while (it.hasNext()) {
			String data_id = WorkflowKernel.generateUniqueID();
			logger.finest("......write new 1mb data block: sort_id=" + sort_id + " data_id=" + data_id);
			byte[] chunk = it.next();
			// write 1MB chunk into cassandra....
			session.execute(new SimpleStatement(STATEMENT_UPSET_DOCUMENTS_DATA, data_id, ByteBuffer.wrap(chunk)));
			// write sort_id....
			session.execute(new SimpleStatement(STATEMENT_UPSET_DOCUMENTS, md5, sort_id, data_id));
			// increase sort_id
			sort_id++;
		}
		logger.finest("......stored filedata object: " + md5);
	}

	/**
	 * This helper method merges the content of attached documents into a
	 * itemCollection. A document is uniquely identified by its md5 checksum. The
	 * data of the document is split into 1md data blocks in the tabe
	 * 'documents_data'
	 * 
	 * @param itemCol
	 * @throws ArchiveException
	 */
	private void mergeDocumentData(ItemCollection itemCol, Session session) throws ArchiveException {
		List<FileData> files = itemCol.getFileData();
		for (FileData fileData : files) {
			// first verify if content is already stored.

			logger.finest("... merge fileData objects: " + files.size() + " fileData objects defined....");
			// add document if not exists
			if (fileData.getContent() == null || fileData.getContent().length == 0) {
				fileData = loadFileData(fileData, session);
				itemCol.addFileData(fileData);
			}

		}
	}

	/**
	 * This helper method loades the content of a document defned by a FileData
	 * object. A document is uniquely identified by its md5 checksum which is part
	 * of the FileData custom attributes. The data of the document is stored in
	 * split 1md data blocks in the tabe 'documents_data'
	 * 
	 * @param itemCol
	 * @throws ArchiveException
	 */
	public FileData loadFileData(FileData fileData, Session session) throws ArchiveException {

		// read md5 form custom attributes
		ItemCollection customAttributes = new ItemCollection(fileData.getAttributes());
		String md5 = customAttributes.getItemValueString(ITEM_MD5_CHECKSUM);

		// test if md5 exits...
		String sql = STATEMENT_SELECT_DOCUMENTS;
		sql = sql.replace("'?'", "'" + md5 + "'");

		logger.finest("......search MD5 entry: " + sql);

		ResultSet rs = session.execute(sql);
		// collect all dat blocks (which are sorted by its sort_id....
		Iterator<Row> resultIter = rs.iterator();
		ByteArrayOutputStream bOutput = new ByteArrayOutputStream(1024 * 1024);
		try {
			while (resultIter.hasNext()) {
				Row row = resultIter.next();

				int sort_id = row.getInt(1);
				String data_id = row.getString(2);
				logger.finest("......load 1mb data block: sort_id=" + sort_id + " data_id=" + data_id);

				// now we can load the data block....
				String sql_data = STATEMENT_SELECT_DOCUMENTS_DATA;
				sql_data = sql_data.replace("'?'", "'" + data_id + "'");
				ResultSet rs_data = session.execute(sql_data);
				Row row_data = rs_data.one();
				if (row_data != null) {
					logger.finest(
							"......merge data block: " + md5 + " sort_id: " + sort_id + " data_id: " + data_id + "...");
					// get byte data...
					ByteBuffer byteDataBlock = row_data.getBytes(1);
					bOutput.write(byteDataBlock.array());
				} else {
					logger.warning("Document Data missing: " + " MD5:" + md5 + " sort_id: " + sort_id + " data_id: "
							+ data_id);
				}
			}
			// now we have all the bytes...
			byte[] allData = bOutput.toByteArray();
			logger.finest("......collected full data block: " + md5 + " size: " + allData.length + "...");
			fileData = new FileData(fileData.getName(), allData, fileData.getContentType(), fileData.getAttributes());
			bOutput.close();

		} catch (IOException e) {
			throw new ArchiveException(ArchiveException.INVALID_DOCUMENT_OBJECT,
					"failed to load document data: " + e.getMessage(), e);
		}
		return fileData;

	}

	/**
	 * This method test if a snapshot recored with a given ID already exists.
	 * 
	 * @param snapshotID
	 * @return true if the snapshot exists.
	 */
	public boolean existSnapshot(String snapshotID, Session session) {
		String sql = STATEMENT_SELECT_SNAPSHOT_ID;
		sql = sql.replace("'?'", "'" + snapshotID + "'");
		logger.finest("......search snapshot id: " + sql);
		ResultSet rs = session.execute(sql);
		Row row = rs.one();
		return (row != null);
	}

	/**
	 * This method loads a snapshot form the cassandra cluster. The snapshot data
	 * includes also the accociated document data. In case you need only the
	 * snapshotdata without documents use loadSnapshot(id,false,session).
	 * 
	 * @param snapshotID
	 *            - snapshot id
	 * @param session
	 *            - cassandra session
	 * @return snapshot data including documents
	 * @throws ArchiveException
	 */
	public ItemCollection loadSnapshot(String snapshotID, Session session) throws ArchiveException {
		return loadSnapshot(snapshotID, true, session);
	}

	/**
	 * Thist method loads a snapshot form the cassandra cluster.
	 * <p>
	 * 
	 * @param snapshotID
	 *            - snapshot id
	 * @param mergeDocuments
	 *            - boolean, if true the accociated document data will be loaded and
	 *            merged into the snapshot data object.
	 * 
	 * @param session
	 *            - cassandra session
	 * @return snapshot data
	 * @throws ArchiveException
	 */
	public ItemCollection loadSnapshot(String snapshotID, boolean mergeDocuments, Session session)
			throws ArchiveException {
		ItemCollection snapshot = new ItemCollection();

		// select snapshot...

		String sql = STATEMENT_SELECT_SNAPSHOT;
		sql = sql.replace("'?'", "'" + snapshotID + "'");
		logger.finest("......search snapshot id: " + sql);
		ResultSet rs = session.execute(sql);
		Row row = rs.one();
		if (row != null) {
			// load ItemCollection object
			ByteBuffer data = row.getBytes(COLUMN_DATA);
			if (data.hasArray()) {
				snapshot = getItemCollection(data.array());

				// next we need to load the document data if exists...
				mergeDocumentData(snapshot, session);
			}
		} else {
			// does not exist - create empty object
			snapshot = new ItemCollection();
		}
		return snapshot;
	}

	/**
	 * This method loads the metadata object represended by an ItemCollection. The
	 * snapshot id for the metadata object is always "0". This id is reserverd for
	 * metadata only.
	 * <p>
	 * If no metadata object yet exists, the method returns an empty ItemCollection.
	 * <p>
	 * The method expects a valid session instance which must be closed by the
	 * client.
	 * 
	 * @return metadata object
	 * @throws ArchiveException
	 */
	public ItemCollection loadMetadata(Session session) throws ArchiveException {
		return loadSnapshot("0", session);
	}

	/**
	 * This method loads all exsting snapshotIDs for a given unqiueID.
	 * 
	 * @param uniqueID
	 * @return list of snapshots
	 */
	public List<String> loadSnapshotsByUnqiueID(String uniqueID, Session session) {
		List<String> result = new ArrayList<String>();
		String sql = STATEMENT_SELECT_SNAPSHOTS_BY_UNIQUEID;
		sql = sql.replace("'?'", "'" + uniqueID + "'");
		logger.finest("......search snapshot id: " + sql);
		ResultSet rs = session.execute(sql);

		// iterate over result

		Iterator<Row> resultIter = rs.iterator();

		while (resultIter.hasNext()) {
			Row row = resultIter.next();
			String snapshotID = row.getString(1);
			result.add(snapshotID);
		}

		return result;
	}

	/**
	 * This method loads all exsting snapshotIDs for a given date.
	 * 
	 * @param date
	 * @return list of snapshots
	 */
	public List<String> loadSnapshotsByDate(java.time.LocalDate date, Session session) {
		List<String> result = new ArrayList<String>();
		// select snapshotIds by day...
		String sql = DataService.STATEMENT_SELECT_SNAPSHOTS_BY_MODIFIED;
		sql = sql.replace("'?'", "'" + date + "'");
		logger.finest("......SQL: " + sql);
		ResultSet rs = session.execute(sql);
		// iterate over result
		Iterator<Row> resultIter = rs.iterator();
		while (resultIter.hasNext()) {
			Row row = resultIter.next();
			String snapshotID = row.getString(1);
			result.add(snapshotID);
		}
		return result;
	}

	/**
	 * This method saves the metadata represented by an ItemCollection. The snapshot
	 * id for the metadata object is always "0". This id is reserverd for metadata
	 * only.
	 * <p>
	 * The method expects a valid session instance which must be closed by the
	 * client.
	 * 
	 * @param itemCol
	 *            - metadata
	 * @throws ArchiveException
	 */
	public void saveMetadata(ItemCollection metadata, Session session) throws ArchiveException {
		// upset document....
		session.execute(new SimpleStatement(STATEMENT_UPSET_SNAPSHOTS, "0", ByteBuffer.wrap(getRawData(metadata))));
	}

	/**
	 * This method deletes a snapshot.
	 * <p>
	 * The method also deletes the documents and all relations
	 * 
	 * @param itemCol
	 * @param session
	 *            - cassandra session
	 * @throws ArchiveException
	 */
	public void deleteSnapshot(ItemCollection itemCol, Session session) throws ArchiveException {
		// TODO
		logger.warning("need to delete snapshot and references");

		// TODO
		logger.warning("need to delete document content if no longer refered by other snapshots");
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
	 * 
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
	 * <p>
	 * We also need to support the old snapshto format
	 * <code>4832b09a1a-20c38abd-1519421083952</code>
	 * 
	 * @param uid
	 * @return
	 */
	public static boolean isSnapshotID(String uid) {
		boolean valid = uid.matches(REGEX_SNAPSHOTID);

		if (!valid) {
			logger.fine("...validate old snapshot id format...");
			// check old snapshto format
			valid = uid.matches(REGEX_OLD_SNAPSHOTID);
		}

		return valid;
	}

	/**
	 * Returns the $uniqueID from a $SnapshotID
	 * 
	 * @param snapshotID
	 * @return $uniqueid
	 */
	public static String getUniqueID(String snapshotID) {
		if (snapshotID != null && snapshotID.contains("-")) {
			return snapshotID.substring(0, snapshotID.lastIndexOf("-"));
		}
		return null;

	}

	/**
	 * Returns the snapshot time n milis of a $SnapshotID
	 * 
	 * @param snapshotID
	 * @return long - snapshot time
	 */
	public static long getSnapshotTime(String snapshotID) {
		if (snapshotID != null && snapshotID.contains("-")) {
			String sTime = snapshotID.substring(snapshotID.lastIndexOf("-") + 1);
			return Long.parseLong(sTime);
		}
		return 0;

	}

	/**
	 * count total value size...
	 * 
	 * @param xmldoc
	 * @return
	 */
	public static long calculateSize(XMLDocument xmldoc) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos;
		try {
			oos = new ObjectOutputStream(baos);
			oos.writeObject(xmldoc);
			oos.close();
			return baos.size();
		} catch (IOException e) {
			logger.warning("...unable to calculate document size!");
		} finally {
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
					logger.warning("failed to close stream");
					e.printStackTrace();
				}
			}
		}

		return 0;

	}

	/**
	 * returns the date tiem from a date in iso format
	 * 
	 * @return
	 */
	public static String getSyncPointISO(long point) {
		SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
		Date date = new Date(point);
		return dt.format(date);
	}

}
