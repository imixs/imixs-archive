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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.imixs.archive.service.ArchiveException;
import org.imixs.archive.service.resync.ResyncService;
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
import com.datastax.driver.core.SimpleStatement;

import jakarta.ejb.Stateless;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

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
    public final static String ITEM_SNAPSHOT_HISTORY = "$snapshot.history"; // optional historical snapshots
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
    public static final String STATEMENT_SELECT_SNAPSHOTS_BY_DOCUMENT = "select * from snapshots_by_document where md5='?'";

    public static final String STATEMENT_SELECT_SNAPSHOTS_BY_UNIQUEID = "select * from snapshots_by_uniqueid where uniqueid='?'";
    public static final String STATEMENT_SELECT_SNAPSHOTS_BY_MODIFIED = "select * from snapshots_by_modified where modified='?'";

    // public static final String STATEMENT_DELETE_SNAPSHOTS_BY_MODIFIED = "DELETE
    // FROM snapshots_by_modified where modified='?' and snapshot='?' IF EXISTS";
    public static final String STATEMENT_DELETE_SNAPSHOTS = "delete from snapshots where snapshot='<snapshot>'";
    public static final String STATEMENT_DELETE_SNAPSHOTS_BY_MODIFIED = "delete from snapshots_by_modified where modified='<modified>' and snapshot='<snapshot>'";
    public static final String STATEMENT_DELETE_SNAPSHOTS_BY_UNIQUEID = "delete from snapshots_by_uniqueid where uniqueid='<uniqueid>' and snapshot='<snapshot>'";

    public static final String STATEMENT_DELETE_SNAPSHOTS_BY_DOCUMENT = "delete from snapshots_by_document where md5='<md5>' and snapshot='<snapshot>'";
    public static final String STATEMENT_DELETE_DOCUMENTS_DATA = "delete from documents_data where data_id='<data_id>'";
    public static final String STATEMENT_DELETE_DOCUMENTS = "delete from documents where md5='<md5>' and sort_id=<sort_id>";

    @Inject
    ClusterService clusterService;

    @Inject
    ResyncService schedulerService;

    @Inject
    protected Event<ArchiveEvent> events;

    /**
     * This method saves a ItemCollection into a specific KeySpace.
     * <p>
     * The method expects a valid session instance which must be closed by the
     * client.
     * 
     * @param snapshot - ItemCollection object
     * @param session  - cassandra session
     * @throws ArchiveException
     */
    public void saveSnapshot(ItemCollection snapshot) throws ArchiveException {
        boolean debug = logger.isLoggable(Level.FINE);
        String snapshotID = snapshot.getUniqueID();

        if (!isSnapshotID(snapshotID)) {
            throw new ArchiveException(ArchiveException.INVALID_DOCUMENT_OBJECT,
                    "unexpected '$snapshotid' format: '" + snapshotID + "'");
        }
        if (debug) {
            logger.finest("......save document" + snapshotID);
        }
        if (!snapshot.hasItem("$modified")) {
            throw new ArchiveException(ArchiveException.INVALID_DOCUMENT_OBJECT,
                    "missing item '$modified' for snapshot " + snapshotID);
        }

        // verify if this snapshot is already stored - if so, we do not overwrite
        // the origin data. See issue #40
        // For example this situation also occurs when restoring a remote snapshot.
        if (existSnapshot(snapshotID)) {
            // skip!
            logger.fine("...snapshot '" + snapshot.getUniqueID() + "' already exits....");
            return;
        }

        // extract $snapshotid 2de78aec-6f14-4345-8acf-dd37ae84875d-1530315900599
        String originUnqiueID = getUniqueID(snapshotID);

        // extract $file content into the table 'documents'....
        extractDocuments(snapshot);

        clusterService.getSession().execute(new SimpleStatement(STATEMENT_UPSET_SNAPSHOTS, snapshot.getUniqueID(),
                ByteBuffer.wrap(getRawData(snapshot))));

        clusterService.getSession().execute(
                new SimpleStatement(STATEMENT_UPSET_SNAPSHOTS_BY_UNIQUEID, originUnqiueID, snapshot.getUniqueID()));

        // upset snapshots_by_modified....
        LocalDate ld = LocalDate.fromMillisSinceEpoch(snapshot.getItemValueDate("$modified").getTime());

        clusterService.getSession()
                .execute(new SimpleStatement(STATEMENT_UPSET_SNAPSHOTS_BY_MODIFIED, ld, snapshot.getUniqueID()));

        cleanupSnaphostHistory(snapshot);

        // Finally we fire the ArchiveEvent ON_ARCHIVE
        if (events != null) {
            events.fire(new ArchiveEvent(snapshot, ArchiveEvent.ON_ARCHIVE));
        } else {
            logger.warning("Missing CDI support for Event<ArchiveEvent> !");
        }

    }

    /**
     * This method test if a snapshot recored with a given ID already exists.
     * 
     * @param snapshotID
     * @return true if the snapshot exists.
     */
    public boolean existSnapshot(String snapshotID) {
        String sql = STATEMENT_SELECT_SNAPSHOT_ID;
        sql = sql.replace("'?'", "'" + snapshotID + "'");
        logger.finest("......search snapshot id: " + sql);
        ResultSet rs = clusterService.getSession().execute(sql);
        Row row = rs.one();
        return (row != null);
    }

    /**
     * This method loads a snapshot form the cassandra cluster. The snapshot data
     * includes also the accociated document data. In case you need only the
     * snapshotdata without documents use loadSnapshot(id,false,session).
     * 
     * @param snapshotID - snapshot id
     * @param session    - cassandra session
     * @return snapshot data including documents
     * @throws ArchiveException
     */
    public ItemCollection loadSnapshot(String snapshotID) throws ArchiveException {
        return loadSnapshot(snapshotID, true);
    }

    /**
     * Thist method loads a snapshot form the cassandra cluster.
     * <p>
     * 
     * @param snapshotID     - snapshot id
     * @param mergeDocuments - boolean, if true the accociated document data will be
     *                       loaded and merged into the snapshot data object.
     * 
     * @param session        - cassandra session
     * @return snapshot data
     * @throws ArchiveException
     */
    public ItemCollection loadSnapshot(String snapshotID, boolean mergeDocuments) throws ArchiveException {
        boolean debug = logger.isLoggable(Level.FINE);

        ItemCollection snapshot = new ItemCollection();
        // select snapshot...
        String sql = STATEMENT_SELECT_SNAPSHOT;
        sql = sql.replace("'?'", "'" + snapshotID + "'");
        if (debug) {
            logger.finest("......search snapshot id: " + sql);
        }
        ResultSet rs = clusterService.getSession().execute(sql);
        Row row = rs.one();
        if (row != null) {
            // load ItemCollection object
            ByteBuffer data = row.getBytes(COLUMN_DATA);
            if (data != null && data.hasArray()) {
                snapshot = getItemCollection(data.array());

                // next we need to load the document data if exists...
                mergeDocumentData(snapshot);
            } else {
                logger.warning("no data found for snapshotId '" + snapshotID + "'");
            }
        } else {
            // does not exist - create empty object
            snapshot = new ItemCollection();
        }
        return snapshot;
    }

    /**
     * This method loads all existing snapshotIDs for a given unqiueID.
     * <p>
     * 
     * @param uniqueID
     * @param maxCount   - max search result
     * @param descending - sort result descending
     * 
     * @return list of snapshots
     */
    public List<String> loadSnapshotsByUnqiueID(String uniqueID, int maxCount, boolean descending) {
        boolean debug = logger.isLoggable(Level.FINE);
        List<String> result = new ArrayList<String>();
        String sql = STATEMENT_SELECT_SNAPSHOTS_BY_UNIQUEID;

        // reverse order by?
        if (descending) {
            sql = sql + " ORDER BY snapshot DESC";
        }

        // set LIMIT?
        if (maxCount > 0) {
            sql = sql + " LIMIT " + maxCount;
        }

        // set uniqueid
        sql = sql.replace("'?'", "'" + uniqueID + "'");
        if (debug) {
            logger.finest("......search snapshot id: " + sql);
        }
        ResultSet rs = clusterService.getSession().execute(sql);

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
     * @return list of snapshots or an empty list if no snapshots exist for the
     *         given date
     */
    public List<String> loadSnapshotsByDate(java.time.LocalDate date) {
        boolean debug = logger.isLoggable(Level.FINE);
        List<String> result = new ArrayList<String>();
        // select snapshotIds by day...
        String sql = DataService.STATEMENT_SELECT_SNAPSHOTS_BY_MODIFIED;
        sql = sql.replace("'?'", "'" + date + "'");
        if (debug) {
            logger.finest("......SQL: " + sql);
        }
        ResultSet rs = clusterService.getSession().execute(sql);
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
     * This helper method loades the content of a document defned by a FileData
     * object. A document is uniquely identified by its md5 checksum which is part
     * of the FileData custom attributes. The data of the document is stored in
     * split 1md data blocks in the tabe 'documents_data'
     * 
     * @param itemCol
     * @throws ArchiveException
     */
    public FileData loadFileData(FileData fileData) throws ArchiveException {
        // read md5 form custom attributes
        ItemCollection customAttributes = new ItemCollection(fileData.getAttributes());
        String md5 = customAttributes.getItemValueString(ITEM_MD5_CHECKSUM);
        // now we have all the bytes...
        byte[] allData = loadFileContent(md5);
        if (allData == null) {
            return null;
        } else {
            return new FileData(fileData.getName(), allData, fileData.getContentType(), fileData.getAttributes());
        }
    }

    /**
     * This helper method loads the content of a document defned by its MD5
     * checksum. The data of the document is stored in chunked 1md data blocks in
     * the table 'documents_data'
     * 
     * @param itemCol
     * @throws ArchiveException
     */
    public byte[] loadFileContent(String md5) throws ArchiveException {

        if (md5 == null || md5.isEmpty()) {
            return null;
        }
        boolean debug = logger.isLoggable(Level.FINE);
        // test if md5 exits...
        String sql = STATEMENT_SELECT_DOCUMENTS;
        sql = sql.replace("'?'", "'" + md5 + "'");

        if (debug) {
            logger.finest("......search MD5 entry: " + sql);
        }

        ResultSet rs = clusterService.getSession().execute(sql);
        // collect all data blocks (which are sorted by its sort_id)....
        Iterator<Row> resultIter = rs.iterator();
        ByteArrayOutputStream bOutput = new ByteArrayOutputStream(1024 * 1024);
        try {
            while (resultIter.hasNext()) {
                Row row = resultIter.next();

                int sort_id = row.getInt(1);
                String data_id = row.getString(2);
                if (debug) {
                    logger.finest("......load 1mb data block: sort_id=" + sort_id + " data_id=" + data_id);
                }
                // now we can load the data block....
                String sql_data = STATEMENT_SELECT_DOCUMENTS_DATA;
                sql_data = sql_data.replace("'?'", "'" + data_id + "'");
                ResultSet rs_data = clusterService.getSession().execute(sql_data);
                Row row_data = rs_data.one();
                if (row_data != null) {
                    if (debug) {
                        logger.finest("......merge data block: " + md5 + " sort_id: " + sort_id + " data_id: " + data_id
                                + "...");
                    }
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
            if (debug) {
                logger.finest("......collected full data block: " + md5 + " size: " + allData.length + "...");
            }
            return allData;

        } catch (IOException e) {
            throw new ArchiveException(ArchiveException.INVALID_DOCUMENT_OBJECT,
                    "failed to load document data: " + e.getMessage(), e);
        } finally {
            if (bOutput != null) {
                try {
                    bOutput.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
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
    public ItemCollection loadMetadata() throws ArchiveException {
        return loadSnapshot("0");
    }

    /**
     * This method saves the metadata represented by an ItemCollection. The snapshot
     * id for the metadata object is always "0". This id is reserverd for metadata
     * only.
     * <p>
     * The method expects a valid session instance which must be closed by the
     * client.
     * 
     * @param itemCol - metadata
     * @throws ArchiveException
     */
    public void saveMetadata(ItemCollection metadata) throws ArchiveException {
        // upset document....
        clusterService.getSession()
                .execute(new SimpleStatement(STATEMENT_UPSET_SNAPSHOTS, "0", ByteBuffer.wrap(getRawData(metadata))));
    }

    /**
     * This method deletes a single snapshot instance.
     * <p>
     * The method also deletes the documents and all relations
     * 
     * @param snapshotID - id of the snapshot
     * @throws ArchiveException
     */
    public void deleteSnapshot(String snapshotID) throws ArchiveException {

        logger.finest("......delete snapshot and documents for:" + snapshotID);
        String uniqueID = this.getUniqueID(snapshotID);
        ItemCollection snapshot = loadSnapshot(snapshotID, false);

        String sql = STATEMENT_DELETE_SNAPSHOTS;
        sql = sql.replace("'<snapshot>'", "'" + snapshotID + "'");
        clusterService.getSession().execute(sql);

        sql = STATEMENT_DELETE_SNAPSHOTS_BY_UNIQUEID;
        sql = sql.replace("'<uniqueid>'", "'" + uniqueID + "'");
        sql = sql.replace("'<snapshot>'", "'" + snapshotID + "'");
        clusterService.getSession().execute(sql);

        long modifiedTime = 0;
        if (snapshot != null) {
            Date modified = null;
            modified = snapshot.getItemValueDate("$modified");
            if (modified == null) {
                logger.warning("Snapshot Object '" + snapshotID + "' has no '$modified' item!");
                // try to build the time form the snapshot
                modifiedTime = this.getSnapshotTime(snapshotID);
            } else {
                modifiedTime = modified.getTime();
            }
        } else {
            logger.warning("Snapshot Object '" + snapshotID + "' not found in archive!");
        }

        LocalDate ld = LocalDate.fromMillisSinceEpoch(modifiedTime);
        sql = STATEMENT_DELETE_SNAPSHOTS_BY_MODIFIED;
        sql = sql.replace("'<modified>'", "'" + ld + "'");
        sql = sql.replace("'<snapshot>'", "'" + snapshotID + "'");
        clusterService.getSession().execute(sql);

        deleteDocuments(snapshot);

    }

    /**
     * Converts a ItemCollection into a XMLDocument and returns the byte data.
     * 
     * @param itemCol
     * @return
     * @throws ArchiveException
     */
    public byte[] getRawData(ItemCollection itemCol) throws ArchiveException {
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
    public ItemCollection getItemCollection(byte[] source) throws ArchiveException {

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
    public boolean isSnapshotID(String uid) {
        boolean valid = uid.matches(REGEX_SNAPSHOTID);
        boolean debug = logger.isLoggable(Level.FINE);
        if (!valid) {
            if (debug) {
                logger.fine("...validate old snapshot id format...");
            }
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
    public String getUniqueID(String snapshotID) {
        if (snapshotID != null && snapshotID.contains("-")) {
            return snapshotID.substring(0, snapshotID.lastIndexOf("-"));
        }
        return null;

    }

    /**
     * Returns the snapshot time n millis of a $SnapshotID
     * 
     * @param snapshotID
     * @return long - snapshot time
     */
    public long getSnapshotTime(String snapshotID) {
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
    public long calculateSize(XMLDocument xmldoc) {

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
    public String getSyncPointISO(long point) {
        SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        Date date = new Date(point);
        return dt.format(date);
    }

    /**
     * This method deletes older snapshots exceeding the optional $snapshot.history.
     * If no $snapshot.hisotry is defined or is 0 than no historical snapshots will
     * be deleted.
     * 
     * @param snapshot
     * @throws ArchiveException
     */
    private void cleanupSnaphostHistory(ItemCollection snapshot) throws ArchiveException {
        boolean debug = logger.isLoggable(Level.FINE);
        int snapshotHistory = snapshot.getItemValueInteger(ITEM_SNAPSHOT_HISTORY);
        if (snapshotHistory > 0) {
            if (debug) {
                logger.finest("......$snapshot.history=" + snapshotHistory);
            }
            String uniqueid = this.getUniqueID(snapshot.getUniqueID());

            // find old snapshots...
            String sql = STATEMENT_SELECT_SNAPSHOTS_BY_UNIQUEID;
            // descending
            sql = sql + " ORDER BY snapshot DESC";
            // set LIMIT to history
            sql = sql + " LIMIT " + (snapshotHistory + 1);

            // set uniqueid
            sql = sql.replace("'?'", "'" + uniqueid + "'");
            if (debug) {
                logger.finest("......search snapshots for id: " + sql);
            }
            ResultSet rs = clusterService.getSession().execute(sql);

            // iterate over result to get last snapshotID
            Iterator<Row> resultIter = rs.iterator();
            int snapshotcount = 0;
            String lastestSnapshotID = null;
            while (resultIter.hasNext()) {
                Row row = resultIter.next();
                lastestSnapshotID = row.getString(1);
                snapshotcount++;
            }

            // if the size of the Resultset is smaller than the snapshotHistory we can skip
            if (snapshotcount < snapshotHistory) {
                return;
            }

            // now we need to check if we have more snapshots - start from the latest
            // snapshot
            sql = STATEMENT_SELECT_SNAPSHOTS_BY_UNIQUEID;
            sql = sql + " AND snapshot<='" + lastestSnapshotID + "'";

            // descending
            sql = sql + " ORDER BY snapshot ASC";

            // set LIMIT to 100
            sql = sql + " LIMIT 100";

            // set uniqueid
            sql = sql.replace("'?'", "'" + uniqueid + "'");
            if (debug) {
                logger.finest("......search snapshot id: " + sql);
            }
            rs = clusterService.getSession().execute(sql);
            int deletions = 0;
            resultIter = rs.iterator();
            while (resultIter.hasNext()) {
                Row row = resultIter.next();
                String id = row.getString(1);
                deleteSnapshot(id);
                deletions++;
            }
            if (deletions >= 2) {
                // we do only log if more than one old history snapshot exist in the archive.
                // during normal life cycle we do not print any message...
                logger.info("...deleted " + deletions + " deprecated snapshots form history (" + uniqueid + ")");
            }
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
    private void extractDocuments(ItemCollection itemCol) throws ArchiveException {
        boolean debug = logger.isLoggable(Level.FINE);
        // empty data...
        byte[] empty = {};
        List<FileData> files = itemCol.getFileData();
        for (FileData fileData : files) {
            // first verify if content is already stored.
            try {
                if (debug) {
                    logger.finest("... extract fileData objects: " + files.size() + " fileData objects found....");
                }

                if (fileData.getContent() != null && fileData.getContent().length > 0) {
                    String md5 = fileData.generateMD5();

                    // test if md5 already stored....
                    String sql = STATEMENT_SELECT_MD5;
                    sql = sql.replace("'?'", "'" + md5 + "'");
                    if (debug) {
                        logger.finest("......search MD5 entry: " + sql);
                    }
                    ResultSet rs = clusterService.getSession().execute(sql);
                    Row row = rs.one();
                    if (row == null) {
                        // not yet stored so extract the content
                        storeDocument(md5, fileData.getContent());
                    } else {
                        if (debug) {
                            logger.finest("......update fildata not necessary because object: " + md5
                                    + " is already stored!");
                        }
                    }

                    // updset documents_by_snapshot.... (needed for deletion)
                    clusterService.getSession().execute(
                            new SimpleStatement(STATEMENT_UPSET_SNAPSHOTS_BY_DOCUMENT, md5, itemCol.getUniqueID()));

                    // remove file content from itemCol
                    if (debug) {
                        logger.finest("drop content for file '" + fileData.getName() + "'");
                    }
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
     * This helper method deletes the content of attached documents A document is
     * uniquely identified by its md5 checksum.
     * 
     * @param itemCol
     * @throws ArchiveException
     */
    private void deleteDocuments(ItemCollection itemCol) throws ArchiveException {

        if (itemCol == null) {
            // no data!
            return;
        }
        boolean debug = logger.isLoggable(Level.FINE);
        List<FileData> files = itemCol.getFileData();
        for (FileData fileData : files) {
            // first verify if content is already stored.
            try {
                if (debug) {
                    logger.finest(
                            "......delete fileData ref and objects: " + files.size() + " fileData objects found....");
                }

                if (fileData.getContent() != null && fileData.getContent().length > 0) {
                    String md5 = fileData.generateMD5();

                    // delete documents_by_snapshot.... (needed for deletion)
                    String sql = STATEMENT_DELETE_SNAPSHOTS_BY_DOCUMENT;
                    sql = sql.replace("'<md5>'", "'" + md5 + "'");
                    sql = sql.replace("'<snapshot>'", "'" + itemCol.getUniqueID() + "'");
                    clusterService.getSession().execute(sql);

                    // now the question is: do we have other snapshots referring this md5 ?
                    sql = DataService.STATEMENT_SELECT_SNAPSHOTS_BY_DOCUMENT;
                    sql = sql.replace("'?'", "'" + md5 + "'");
                    if (debug) {
                        logger.finest("......SQL: " + sql);
                    }
                    ResultSet rs = clusterService.getSession().execute(sql);
                    // iterate over result
                    Iterator<Row> resultIter = rs.iterator();
                    if (resultIter.hasNext()) {
                        // we have other snapshots refering this document so we can skipp!
                        return;
                    } else {
                        // we have no other refrerences - this means we can delete the document data!
                        // test if md5 exits...
                        String sql_sub = STATEMENT_SELECT_DOCUMENTS;
                        sql_sub = sql_sub.replace("'?'", "'" + md5 + "'");
                        if (debug) {
                            logger.finest("......search MD5 entry: " + sql_sub);
                        }

                        ResultSet rs_sub = clusterService.getSession().execute(sql_sub);
                        // collect all data blocks (which are sorted by its sort_id....
                        Iterator<Row> resultIterSub = rs_sub.iterator();
                        List<String> dataIDs = new ArrayList<String>();
                        List<Integer> sortIDs = new ArrayList<Integer>();
                        while (resultIterSub.hasNext()) {
                            Row row = resultIterSub.next();
                            int sort_id = row.getInt(1);
                            String data_id = row.getString(2);
                            logger.info("......delete 1mb data block: sort_id=" + sort_id + " data_id=" + data_id);
                            dataIDs.add(data_id);
                            sortIDs.add(sort_id);
                        }
                        for (String data_id : dataIDs) {
                            sql = STATEMENT_DELETE_DOCUMENTS_DATA;
                            sql = sql.replace("<data_id>", data_id);
                            clusterService.getSession().execute(sql);
                        }
                        for (int sort_id : sortIDs) {
                            sql = STATEMENT_DELETE_DOCUMENTS;
                            sql = sql.replace("<md5>", md5);
                            sql = sql.replace("<sort_id>", Integer.toString(sort_id));
                            clusterService.getSession().execute(sql);
                        }

                    }

                }
            } catch (NoSuchAlgorithmException e) {
                throw new ArchiveException(ArchiveException.MD5_ERROR,
                        "can not compute md5 of document - " + e.getMessage());
            }
        }
    }

    /**
     * This method stores a single document identified by the MD5 checksum.
     * <p>
     * The method splits the data into 1mb blocks stored in the table
     * 'documents_data'
     * 
     * 
     * @param md5
     * @param data
     * @param session
     */
    private void storeDocument(String md5, byte[] data) {
        boolean debug = logger.isLoggable(Level.FINE);
        // split the data into 1md blocks....
        DocumentSplitter documentSplitter = new DocumentSplitter(data);
        Iterator<byte[]> it = documentSplitter.iterator();
        int sort_id = 0;
        while (it.hasNext()) {
            String data_id = WorkflowKernel.generateUniqueID();
            if (debug) {
                logger.finest("......write new 1mb data block: sort_id=" + sort_id + " data_id=" + data_id);
            }
            byte[] chunk = it.next();
            // write 1MB chunk into cassandra....
            clusterService.getSession()
                    .execute(new SimpleStatement(STATEMENT_UPSET_DOCUMENTS_DATA, data_id, ByteBuffer.wrap(chunk)));
            // write sort_id....
            clusterService.getSession().execute(new SimpleStatement(STATEMENT_UPSET_DOCUMENTS, md5, sort_id, data_id));
            // increase sort_id
            sort_id++;
        }
        if (debug) {
            logger.finest("......stored filedata object: " + md5);
        }
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
    private void mergeDocumentData(ItemCollection itemCol) throws ArchiveException {
        boolean debug = logger.isLoggable(Level.FINE);
        List<FileData> files = itemCol.getFileData();
        for (FileData fileData : files) {
            // first verify if content is already stored.
            if (debug) {
                logger.finest("... merge fileData objects: " + files.size() + " fileData objects defined....");
            }
            // add document if not exists
            if (fileData.getContent() == null || fileData.getContent().length == 0) {
                fileData = loadFileData(fileData);
                itemCol.addFileData(fileData);
            }

        }
    }

}
