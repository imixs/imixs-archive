package org.imixs.workflow.archive.hadoop.jca;

/**
*
* 
*/
public interface Connection {

   public void write(String content);
   public void close();
}