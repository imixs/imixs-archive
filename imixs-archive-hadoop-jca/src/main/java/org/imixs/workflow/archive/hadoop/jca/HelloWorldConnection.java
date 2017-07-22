package org.imixs.workflow.archive.hadoop.jca;

public interface HelloWorldConnection {
	/**
	 * HelloWorld
	 * 
	 * @return String
	 */
	public String helloWorld();

	/**
	 * HelloWorld
	 * 
	 * @param name
	 *            A name
	 * @return String
	 */
	public String helloWorld(String name);

	/**
	 * Close
	 */
	public void close();
}