package org.imixs.workflow.archive.hadoop.jca;


import java.io.Serializable;
import javax.resource.Referenceable;

/**
 *
 * 
 */
public interface DataSource extends Serializable, Referenceable {
      Connection getConnection();
}