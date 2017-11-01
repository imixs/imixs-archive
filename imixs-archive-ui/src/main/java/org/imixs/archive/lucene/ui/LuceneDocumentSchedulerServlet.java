package org.imixs.archive.lucene.ui;

import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

import org.imixs.archive.experimental.LuceneDocumentSchedulerService;
import org.imixs.workflow.ItemCollection;

/**
 * This servlet runs during deployment and verifies if a
 * WorkflowSchedulerService need to be restarted.
 * 
 * @author rsoika
 * 
 */
@WebServlet(value = "/lucenedocumentservicesetup", loadOnStartup = 1)
public class LuceneDocumentSchedulerServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(LuceneDocumentSchedulerServlet.class.getName());

	@EJB
	LuceneDocumentSchedulerService luceneDocumentSchedulerService;

	/**
	 * This method is called on startup. The method verifies if a
	 * workflowScheduler timer exits and restarts the timer service
	 */
	@Override
	public void init() throws ServletException {

		super.init();

		// try to start the scheduler service
		try {
			
			
			logger.severe("Starting LuceneDocumentScheduler....");
			
			ItemCollection configItemCollection = luceneDocumentSchedulerService.loadConfiguration();
			if (configItemCollection != null && configItemCollection.getItemValueBoolean("_enabled")) {
				logger.info("Starting WorkflowScheduler ID='" + configItemCollection.getUniqueID() + "'....");
				luceneDocumentSchedulerService.start();
			}
		} catch (Exception e) {
			logger.warning("Error due to start workflowSchedulerService: " + e.getMessage());
		}
	}

}