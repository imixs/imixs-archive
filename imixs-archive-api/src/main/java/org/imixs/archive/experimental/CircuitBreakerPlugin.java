package org.imixs.archive.experimental;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.plugins.AbstractPlugin;
import org.imixs.workflow.exceptions.PluginException;
/**
 * Plugin class to simulate a roleback scenario. 
 * @author rsoika
 *
 */
public class CircuitBreakerPlugin extends AbstractPlugin {
	static final String ERROR = "ERROR";
	
	@SuppressWarnings("unused")
	@Override
	public ItemCollection run(ItemCollection document, ItemCollection event) throws PluginException {
		
		if (true) {
			throw new PluginException(this.getClass().getSimpleName(), ERROR,
					"forced plugin exception!");
		}
		return document;
	}

}
