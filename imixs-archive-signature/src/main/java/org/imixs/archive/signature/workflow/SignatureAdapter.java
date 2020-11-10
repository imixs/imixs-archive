package org.imixs.archive.signature.workflow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.imixs.archive.core.SnapshotService;
import org.imixs.archive.signature.SignatureService;
import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.SignalAdapter;
import org.imixs.workflow.engine.WorkflowService;
import org.imixs.workflow.exceptions.AdapterException;

/**
 * The SignatureAdapter signes a PDF document
 * 
 * @version 1.0
 * @author rsoika
 */
public class SignatureAdapter implements SignalAdapter {

	private static Logger logger = Logger.getLogger(SignatureAdapter.class.getName());

	@Inject
	SignatureService signatureService;

	@Inject
	SnapshotService snapshotService;

	@Inject
	WorkflowService workflowService;

	/**
	 * This method posts a text from an attachment to the Imixs-ML Analyse service
	 * endpoint
	 */
	@Override
	public ItemCollection execute(ItemCollection document, ItemCollection event) throws AdapterException {

		// find first PDF file....
		List<String> fileNames = document.getFileNames();
		for (String fileName : fileNames) {

			if (fileName.toLowerCase().endsWith(".pdf")) {
				try {

					logger.info("......signing pdf: " + fileName);
					FileData fileData = document.getFileData(fileName);

					byte[] sourceContent = fileData.getContent();
					if (sourceContent.length == 0) {
						// load from snapshot
						ItemCollection snapshot = snapshotService.findSnapshot(document);
						fileData = snapshot.getFileData(fileName);
						sourceContent = fileData.getContent();
					}

					Path path = Paths.get(fileName);
					Files.write(path, sourceContent);

					File f = new File(fileName);
					
					File bild=new File("/opt/imixs-keystore/Sign_Ralph_Soika.jpg");
					
					signatureService.signPDF(f, bild);
					

					// attache the new generated file....
					String name = fileName;
					String substring = name.substring(0, name.lastIndexOf('.'));
					String newFileName = substring + "_signed.pdf";
					byte[] targetContent = Files.readAllBytes(Paths.get(newFileName));
					document.addFileData(new FileData(newFileName, targetContent, "application/pdf", null));

					logger.info("......signing pdf: " + fileName + " completed!");

				} catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException e) {
					throw new AdapterException(this.getClass().getSimpleName(), "SIGNING_ERROR", e.getMessage(), e);
				}

				break;
			}

		}

		return document;
	}

}