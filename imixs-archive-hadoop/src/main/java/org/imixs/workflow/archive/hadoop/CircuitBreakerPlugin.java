package org.imixs.workflow.archive.hadoop;

import javax.annotation.Resource;
import javax.ejb.SessionContext;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.plugins.AbstractPlugin;
import org.imixs.workflow.exceptions.PluginException;

public class CircuitBreakerPlugin extends AbstractPlugin {
	static final String ARCHIVE_ERROR = "ARCHIVE_ERROR";
	
	@Resource
	SessionContext ctx;
	
	@Override
	public ItemCollection run(ItemCollection document, ItemCollection event) throws PluginException {
		
		if (1 == 1) {
			ctx.setRollbackOnly();
			throw new PluginException(this.getClass().getSimpleName(), ARCHIVE_ERROR,
					"forced plugin exception!");
		}

		
		// TODO Auto-generated method stub
		return document;
	}

}
