package org.connectorz.files.store;

import org.connectorz.files.BucketStore;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import javax.resource.ResourceException;
import javax.resource.spi.*;
import javax.security.auth.Subject;
import javax.validation.constraints.Min;
import org.connectorz.files.Bucket;

@ConnectionDefinition(connectionFactory = BucketStore.class,
   connectionFactoryImpl = FileBucketStore.class,
   connection = Bucket.class,
   connectionImpl = FileBucket.class)
public class GenericManagedConnectionFactory
        implements ManagedConnectionFactory, Serializable {

    private PrintWriter out;
    private String rootDirectory;

    public GenericManagedConnectionFactory() {
        out = new PrintWriter(System.out);
        out.println("#GenericManagedConnectionFactory.constructor");
    }

    //@Min(1)
    //@ConfigProperty(defaultValue = "./store/", supportsDynamicUpdates = true, description = "The root folder of the file store")
    public void setRootDirectory(String rootDirectory) {
        out.println("#FileBucket.setRootDirectory: " + rootDirectory);
        this.rootDirectory = rootDirectory;
    }

    @Override
    public Object createConnectionFactory(ConnectionManager cxManager) throws ResourceException {
        out.println("#GenericManagedConnectionFactory.createConnectionFactory,1");
        return new FileBucketStore(out,this, cxManager);
    }

    @Override
    public Object createConnectionFactory() throws ResourceException {
        out.println("#GenericManagedConnectionFactory.createManagedFactory,2");
        return new FileBucketStore(out,this, null);
    }

    @Override
    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo info) {
        out.println("#GenericManagedConnectionFactory.createManagedConnection");
        return new GenericManagedConnection(out,this.rootDirectory,this, info);
    }

    @Override
    public ManagedConnection matchManagedConnections(Set connectionSet, Subject subject, ConnectionRequestInfo info)
            throws ResourceException {
        out.println("#GenericManagedConnectionFactory.matchManagedConnections Subject " + subject + " Info: " +  info);
        for (Object con : connectionSet) {
            GenericManagedConnection gmc = (GenericManagedConnection) con;
            ConnectionRequestInfo connectionRequestInfo = gmc.getConnectionRequestInfo();
            if((info == null) || connectionRequestInfo.equals(info))
                return gmc;
        }
        throw new ResourceException("Cannot find connection for info!");
    }

    @Override
    public void setLogWriter(PrintWriter out) throws ResourceException {
        out.println("#GenericManagedConnectionFactory.setLogWriter");
        this.out = out;
    }

    @Override
    public PrintWriter getLogWriter() throws ResourceException {
        out.println("#GenericManagedConnectionFactory.getLogWriter");
        return this.out;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GenericManagedConnectionFactory other = (GenericManagedConnectionFactory) obj;
        if (!Objects.equals(this.rootDirectory, other.rootDirectory)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Objects.hashCode(this.rootDirectory);
        return hash;
    }

}