
package org.connectorz.files;

import java.nio.file.attribute.FileTime;

/**
 *
 * @author adam-bien.com
 */
public interface Bucket extends AutoCloseable{

    void write(String file,byte[] content);
    void delete(String file);
    byte[] fetch(String file);
	FileTime lastModified(String file);
    @Override
    void close();

}