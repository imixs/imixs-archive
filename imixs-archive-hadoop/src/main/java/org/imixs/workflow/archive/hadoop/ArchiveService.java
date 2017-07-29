/*******************************************************************************
 *  Imixs Workflow 
 *  Copyright (C) 2001, 2011 Imixs Software Solutions GmbH,  
 *  http://www.imixs.com
 *  
 *  This program is free software; you can redistribute it and/or 
 *  modify it under the terms of the GNU General Public License 
 *  as published by the Free Software Foundation; either version 2 
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful, 
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 *  General Public License for more details.
 *  
 *  You can receive a copy of the GNU General Public
 *  License at http://www.gnu.org/licenses/gpl.html
 *  
 *  Project: 
 *  	http://www.imixs.org
 *  	http://java.net/projects/imixs-workflow
 *  
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika - Software Developer
 *******************************************************************************/

package org.imixs.workflow.archive.hadoop;

import java.io.IOException;
import java.io.StringWriter;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.ejb.EJBException;
import javax.ejb.LocalBean;
import javax.ejb.SessionSynchronization;
import javax.ejb.Stateful;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.xml.XMLItemCollection;
import org.imixs.workflow.xml.XMLItemCollectionAdapter;

/**
 * The Service can be used to store data into hadoop. The bean has
 * SessionSynchronization to rollback failed transactions. The bean is used by
 * the ArchivePlugin.
 * 
 * The method write(path, data) can be used to store data into the hadoop
 * cluster. The bean synchronizes the transaction state and rollback any changes
 * to hadoop made in on transaction.
 * 
 * 
 * 
 * @author rsoika
 */

@Stateful
@LocalBean
public class ArchiveService implements SessionSynchronization {

	static final String ARCHIVE_CONNECTION_ERROR = "ARCHIVE_CONNECTION_ERROR";
	static final String ARCHIVE_DATA_ERROR = "ARCHIVE_DATA_ERROR";
	static final String ARCHIVE_ROLBACK_ERROR = "ARCHIVE_ROLBACK_ERROR";
	static final String ARCHIVE_TRANSACTION_ERROR = "ARCHIVE_TRANSACTION_ERROR";
	private static Logger logger = Logger.getLogger(ArchiveService.class.getName());

	List<String> transactionCache;

	/**
	 * This method writes the data of a Imixs ItemCollection to the hadoop archive.
	 * 
	 * An existing file will be overwritten (overwrite=true)
	 * 
	 * To support the current transaction, an existing version of the file is
	 * written under a temp filename. If the transaction completes successful, the
	 * temp file will be removed. If the transaction failed the origin file will be
	 * restored.
	 * 
	 * @param file
	 * @param content
	 * @return
	 */
	public void doArchive(String path, ItemCollection document) throws PluginException {
		long l = System.currentTimeMillis();
		if (transactionCache == null) {
			logger.info("init transactioncache...");
			transactionCache = new ArrayList<String>();
		}

		HDFSClient hdfsClient = null;
		try {

			hdfsClient = new HDFSClient();

			// first rename existing file
			String tmpFileName = path + "_" + System.nanoTime();
			transactionCache.add(tmpFileName);
			// move current file into the transaction store
			hdfsClient.renameData(path, tmpFileName);
			if (hdfsClient.getHadoopResponse().isError()) {
				throw new PluginException(ArchivePlugin.class.getName(), ARCHIVE_TRANSACTION_ERROR,
						"creating transaction file failed!");
			}
			logger.info("renamed successful");

			StringWriter writer = new StringWriter();

			// convert the ItemCollection into a XMLItemcollection...
			XMLItemCollection xmlItemCollection = XMLItemCollectionAdapter.putItemCollection(document);

			// marshal the Object into an XML Stream....

			JAXBContext context = JAXBContext.newInstance(XMLItemCollection.class);
			Marshaller m = context.createMarshaller();
			m.marshal(xmlItemCollection, writer);

			byte[] content = writer.toString().getBytes();

			hdfsClient.putData(path, content, true);
			if (hdfsClient.getHadoopResponse().isError()) {
				throw new PluginException(ArchivePlugin.class.getName(), ARCHIVE_CONNECTION_ERROR,
						"connection failed - HTTP Result:" + hdfsClient.getHadoopResponse().getCode());
			}

			// succeeded
			logger.info("Archive process completed in " + (System.currentTimeMillis() - l) + "ms");

		} catch (JAXBException e) {
			if (hdfsClient != null) {
				logger.severe("Unable to connect to '" + hdfsClient.getUrl() + "'");
			}
			// throw plugin exception
			throw new PluginException(ArchivePlugin.class.getName(), ARCHIVE_DATA_ERROR, e.getMessage(), e);
		} catch (IOException e) {
			if (hdfsClient != null) {
				logger.severe("connection failed to: '" + hdfsClient.getUrl() + "'");
			}
			// throw plugin exception
			throw new PluginException(ArchivePlugin.class.getName(), ARCHIVE_CONNECTION_ERROR, e.getMessage(), e);
		}
	}

	@Override
	public void afterBegin() throws EJBException, RemoteException {
		logger.finest("after begin....");
	}

	/**
	 * clean transaction log...
	 */
	@Override
	public void afterCompletion(boolean committed) throws EJBException, RemoteException {
		logger.info("after completion... committed=" + committed);
		HDFSClient hdfsClient = new HDFSClient();

		// Rollback...
		if (committed == false) {
			// role back
			logger.info("Rollback transaction...");
			for (String path : transactionCache) {
				int i = path.lastIndexOf("_");
				String target = path.substring(0, i);
				logger.info("restore " + path);
				try {
					hdfsClient.deleteData(target);
					if (hdfsClient.getHadoopResponse().isError()) {
						throw new EJBException(ARCHIVE_ROLBACK_ERROR + ": Failed to rename transaction file");
					}
					hdfsClient.renameData(path, target);
					if (hdfsClient.getHadoopResponse().isError()) {
						throw new EJBException(ARCHIVE_ROLBACK_ERROR + ": Failed to rename transaction file");
					}
				} catch (IOException | JAXBException e) {
					throw new EJBException(ARCHIVE_ROLBACK_ERROR + ":" + e.getMessage(), e);
				}
			}
		} else {
			// commit ok - remove transaction files
			logger.info("clean transactioncache...");
			for (String path : transactionCache) {
				logger.info("delete " + path);
				try {
					hdfsClient.deleteData(path);
					if (hdfsClient.getHadoopResponse().isError()) {
						throw new EJBException(ARCHIVE_ROLBACK_ERROR + ": Failed to rename transaction file");
					}

				} catch (IOException | JAXBException e) {
					throw new EJBException(ARCHIVE_ROLBACK_ERROR + ":" + e.getMessage(), e);
				}
			}
			logger.info("transaction completed");

		}

	}

	@Override
	public void beforeCompletion() throws EJBException, RemoteException {
		logger.finest("before comple...");
	}

}
