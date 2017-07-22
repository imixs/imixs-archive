package org.imixs.workflow.archive.hadoop.jca;

import javax.annotation.Resource;
import javax.ejb.SessionContext;

/**
 * Test class to test the JCAHadoopConnector
 * 
 * @author rsoika
 * 
 */
public class HadoopJCATest {
	@Resource(mappedName = "jca/FileFactory")
	private DataSource dataSource;

	@Resource
	private SessionContext context;

	public static String TEST_DATA = "/test-data.txt";

	public void accessFile(String content) {
		Connection connection = dataSource.getConnection();
		connection.write(content);
		connection.close();
	}

	public void accessFileAndRollback(String content) {
		Connection connection = dataSource.getConnection();
		connection.write(content);
		context.setRollbackOnly();
	}

	public void accessFileAndThrowException(String content) {
		Connection connection = dataSource.getConnection();
		connection.write(content);
		throw new RuntimeException("Force Rollback");
	}
}
