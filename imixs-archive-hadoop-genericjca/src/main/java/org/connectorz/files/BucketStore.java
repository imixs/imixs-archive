package org.connectorz.files;

import java.io.Serializable;
import javax.resource.Referenceable;

/**
 *
 * @author adam-bien.com
 */
public interface BucketStore extends Serializable, Referenceable {
      Bucket getBucket();
}