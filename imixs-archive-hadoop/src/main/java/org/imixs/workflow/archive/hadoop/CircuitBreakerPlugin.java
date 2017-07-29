package org.imixs.workflow.archive.hadoop;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.plugins.AbstractPlugin;
import org.imixs.workflow.exceptions.PluginException;

public class CircuitBreakerPlugin extends AbstractPlugin {
	static final String ERROR = "ERROR";
	
	@Override
	public ItemCollection run(ItemCollection document, ItemCollection event) throws PluginException {
		
		if (1 == 1) {
			throw new PluginException(this.getClass().getSimpleName(), ERROR,
					"forced plugin exception!");
		}
		return document;
	}

}
