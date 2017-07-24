package org.connectorz.files.store;

import java.io.PrintWriter;

import javax.naming.Reference;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnectionFactory;

import org.connectorz.files.Bucket;
import org.connectorz.files.BucketStore;

public class FileBucketStore
        implements BucketStore {
    private static final long serialVersionUID = 1L;
	private ManagedConnectionFactory mcf;
    private Reference reference;
    private ConnectionManager cm;
    private PrintWriter out;

    public FileBucketStore(PrintWriter out,ManagedConnectionFactory mcf, ConnectionManager cm) {
        out.println("#FileBucketStore");
        this.mcf = mcf;
        this.cm = cm;
        this.out = out;
    }

	@Override
	public Bucket getBucket() {
		out.println("#FileBucketStore.getConnection " + this.cm + " MCF: " + this.mcf);
		try {
			return (Bucket) cm.allocateConnection(mcf, getConnectionRequestInfo());
		} catch (ResourceException ex) {
			throw new RuntimeException(ex.getMessage());
		}
	}

   
    @Override
    public void setReference(Reference reference) {
        this.reference = reference;
    }

    @Override
    public Reference getReference() {
        return reference;
    }

    private ConnectionRequestInfo getConnectionRequestInfo() {
        return new ConnectionRequestInfo() {

            @Override
            public boolean equals(Object obj) {
                return true;
            }

            @Override
            public int hashCode() {
                return 1;
            }
        };
    }
}