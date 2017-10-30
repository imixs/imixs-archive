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

package org.imixs.archive.lucene;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Singleton;

import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.BytesRef;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.engine.PropertyService;
import org.imixs.workflow.exceptions.IndexException;
import org.imixs.workflow.exceptions.PluginException;

/**
 * The LuceneDocumentService provides methods to index .doc, .txt and .pdf
 * files.
 * 
 * @see http://stackoverflow.com/questions/34880347/why-did-lucene-indexwriter-
 *      did-not-update-the-index-when-called-from-a-web-modul
 * @see LucenePlugin
 * @version 1.2
 * @author rsoika
 */
@Singleton
public class LuceneDocumentService {

	protected static final String DEFAULT_ANALYSER = "org.apache.lucene.analysis.standard.ClassicAnalyzer";
	protected static final String DEFAULT_INDEX_DIRECTORY = "imixs-workflow-index";
	protected static final String ANONYMOUS = "ANONYMOUS";

	private String indexDirectoryPath = null;
	private String analyserClass = null;
	private Properties properties = null;

	// default field lists
	private static List<String> DEFAULT_SEARCH_FIELD_LIST = Arrays.asList("$workflowsummary", "$workflowabstract");
	private static List<String> DEFAULT_NOANALYSE_FIELD_LIST = Arrays.asList("$modelversion", "$processid",
			"$workitemid", "$uniqueidref", "type", "$writeaccess", "$modified", "$created", "namcreator", "$creator",
			"$editor", "$lasteditor", "$workflowgroup", "$workflowstatus", "txtworkflowgroup", "txtname", "namowner",
			"txtworkitemref");

	@EJB
	PropertyService propertyService;

	private static Logger logger = Logger.getLogger(LuceneDocumentService.class.getName());

	/**
	 * PostContruct event - The method loads the lucene index properties from the
	 * imixs.properties file from the classpath. If no properties are defined the
	 * method terminates.
	 * 
	 */
	@PostConstruct
	void init() {

		// read configuration
		properties = propertyService.getProperties();
		indexDirectoryPath = properties.getProperty("lucence.indexDir", DEFAULT_INDEX_DIRECTORY);
		// luceneLockFactory = properties.getProperty("lucence.lockFactory");
		// get Analyzer Class -
		// default=org.apache.lucene.analysis.standard.ClassicAnalyzer
		analyserClass = properties.getProperty("lucence.analyzerClass", DEFAULT_ANALYSER);

	}

	/**
	 * A method to index undindexed files in workitems..
	 * 
	 * 
	 * @throws FileNotFoundException
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	public void updateDocuments(Collection<ItemCollection> documents) {
		try {
			long start = System.currentTimeMillis();
			
			logger.info("starting indexing " + documents.size() + " documents...");
			
			createIndexWriter();
//			checkFileValidity();
//			closeIndexWriter();
//			long end = System.currentTimeMillis();
//			System.out.println("Total Document Indexed : " + TotalDocumentsIndexed());
//			System.out.println("Total time" + (end - start) / (100 * 60));
		} catch (Exception e) {
			System.out.println("Sorry task cannot be completed");
		}
	}

	


	/**
	 * Returns the Lucene configuration
	 * 
	 * @return
	 */
	public ItemCollection getConfiguration() {
		ItemCollection config = new ItemCollection();

		config.replaceItemValue("lucence.indexDir", indexDirectoryPath);
		// config.replaceItemValue("lucence.lockFactory", luceneLockFactory);
		config.replaceItemValue("lucence.analyzerClass", analyserClass);

		return config;
	}

	/**
	 * This method removes a single Document from the search index.
	 * 
	 * @param uniqueID
	 *            of the workitem to be removed
	 * @throws PluginException
	 */
	public void removeDocument(String uniqueID) {
		IndexWriter awriter = null;
		long ltime = System.currentTimeMillis();
		try {
			awriter = createIndexWriter();
			Term term = new Term("$uniqueid", uniqueID);
			awriter.deleteDocuments(term);
		} catch (CorruptIndexException e) {
			throw new IndexException(IndexException.INVALID_INDEX,
					"Unable to remove workitem '" + uniqueID + "' from search index", e);
		} catch (LockObtainFailedException e) {
			throw new IndexException(IndexException.INVALID_INDEX,
					"Unable to remove workitem '" + uniqueID + "' from search index", e);
		} catch (IOException e) {
			throw new IndexException(IndexException.INVALID_INDEX,
					"Unable to remove workitem '" + uniqueID + "' from search index", e);
		} finally {
			// close writer!
			if (awriter != null) {
				logger.finest("lucene close IndexWriter...");
				try {
					awriter.close();
				} catch (CorruptIndexException e) {
					throw new IndexException(IndexException.INVALID_INDEX, "Unable to close lucene IndexWriter: ", e);
				} catch (IOException e) {
					throw new IndexException(IndexException.INVALID_INDEX, "Unable to close lucene IndexWriter: ", e);
				}
			}
		}

		logger.fine("lucene removeDocument in " + (System.currentTimeMillis() - ltime) + " ms");

	}

	/**
	 * This method creates a new instance of a lucene IndexWriter.
	 * 
	 * The location of the lucene index in the filesystem is read from the
	 * imixs.properties
	 * 
	 * @return
	 * @throws IOException
	 * @throws Exception
	 */
	IndexWriter createIndexWriter() throws IOException {
		// create a IndexWriter Instance
		Directory indexDir = FSDirectory.open(Paths.get(indexDirectoryPath));
		IndexWriterConfig indexWriterConfig;
		indexWriterConfig = new IndexWriterConfig(new ClassicAnalyzer());

		return new IndexWriter(indexDir, indexWriterConfig);
	}

}
