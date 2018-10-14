package org.imixs.workflow.archive.cassandra.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.ItemCollectionComparator;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.archive.cassandra.ImixsArchiveApp;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.InvalidConfigurationInQueryException;
import com.datastax.driver.core.exceptions.QueryValidationException;
import com.datastax.driver.core.exceptions.SyntaxError;

/**
 * The MetadataService saves metadata for an archive. E.g. the current syncpoint
 * and the scheduler status
 * 
 * @author rsoika
 * 
 */
@Stateless
public class MetadataService {

	private static Logger logger = Logger.getLogger(MetadataService.class.getName());

	@EJB
	ClusterService clusterService;

	@EJB
	SchedulerService schedulerService;

	/**
	 * This method initializes the Core-KeySpace and creates the table schema if not
	 * exits. The method returns true if the Core-KeySpace is accessible.
	 * <p>
	 * The method starts all enabled schedulers for existing configurations.
	 * 
	 */
	public boolean init() {
		try {
			logger.info("...init imixsarchive keyspace ...");
			Session session = clusterService.getArchiveSession();

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
		}
	}

	/**
	 * Loads a configuration from the core-keyspace by a keyspaceId.
	 * 
	 * @return
	 * @throws ImixsArchiveException
	 */
	public ItemCollection loadMetadata() throws ImixsArchiveException {
		Session session = null;
		// get session from archive....
		try {
			session = clusterService.getArchiveSession();

			String id = "METADATA" + clusterService.getKeySpaceName();

			ResultSet resultSet = session.execute("SELECT * FROM configurations WHERE id='" + id + "';");
			Row row = resultSet.one();
			if (row != null) {

				// String keyspace = row.getString("id");
				byte[] source = row.getBytes("data").array();
				return DocumentService.getItemCollection(source);
			}
		} finally {
			if (session != null) {
				session.close();
			}
		}
		return null;
	}

	/**
	 * Save the meta data. The id of the Meta data is a static string of
	 * 'METADATA_'+ keyspace
	 * 
	 * @param metadata
	 * @throws ImixsArchiveException
	 */
	public void saveMetadata(ItemCollection metadata) throws ImixsArchiveException {
		PreparedStatement statement = null;
		BoundStatement bound = null;
		Session session = null;

		// set static UnqiueID
		String id = "METADATA" + clusterService.getKeySpaceName();
		metadata.setItemValue("$uniqueid", id);

		try {
			// get session from archive....
			session = clusterService.getArchiveSession();

			// upset document....
			statement = session.prepare(DocumentService.STATEMENT_UPSET_SNAPSHOTS);
			bound = statement.bind().setString(DocumentService.COLUMN_SNAPSHOT, metadata.getUniqueID())
					.setBytes(DocumentService.COLUMN_DATA, ByteBuffer.wrap(DocumentService.getRawData(metadata)));
			session.execute(bound);

		} finally {
			if (session != null) {
				session.close();
			}
		}
	}
}
