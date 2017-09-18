package org.imixs.archive.api.test;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.plugins.AbstractPlugin;
import org.imixs.workflow.exceptions.PluginException;
/**
 * Plugin class to simulate a rolleback scenario. 
 * @author rsoika
 *
 */
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
