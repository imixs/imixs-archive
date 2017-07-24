package org.connectorz.files.store;

import javax.resource.spi.ActivationSpec;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.Connector;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.TransactionSupport;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.transaction.xa.XAResource;

/**
 *
 * @author adam bien, blog.adam-bien.com
 */
@Connector(reauthenticationSupport = false,
transactionSupport = TransactionSupport.TransactionSupportLevel.LocalTransaction)
public class FileAdapter implements ResourceAdapter {

    @Override
    public void start(BootstrapContext bc) {
    }

    @Override
    public void stop() {
    }

    @Override
    public void endpointActivation(MessageEndpointFactory mef, ActivationSpec as) {
    }

    @Override
    public void endpointDeactivation(MessageEndpointFactory mef, ActivationSpec as) {
    }

    @Override
    public XAResource[] getXAResources(ActivationSpec[] ass) {
        return null;
    }
    
    
 // TODO - equals is not correct implemented!
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GenericManagedConnection other = (GenericManagedConnection) obj;
        if ((this == null || !this.equals(other))) {
            return false;
        }
        return true;
    }

    
    // TODO - hashCode is not correct implemented!
    @Override
    public int hashCode() {
    	 int hash = 5;
         hash = 83 * hash + (this != null ? this.hashCode() : 0);
         return hash;
    }
}