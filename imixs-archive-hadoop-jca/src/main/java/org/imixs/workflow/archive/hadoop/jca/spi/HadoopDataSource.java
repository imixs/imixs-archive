package org.imixs.workflow.archive.hadoop.jca.spi;

import java.io.PrintWriter;
import javax.naming.Reference;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnectionFactory;

import org.imixs.workflow.archive.hadoop.jca.DataSource;

public class HadoopDataSource
        implements DataSource {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ManagedConnectionFactory mcf;
    private Reference reference;
    private ConnectionManager cm;
    private PrintWriter out;

    public HadoopDataSource(PrintWriter out,ManagedConnectionFactory mcf, ConnectionManager cm) {
        out.println("#FileDataSource");
        this.mcf = mcf;
        this.cm = cm;
        this.out = out;
    }

    public HadoopConnection getConnection(){
        out.println("#FileDataSource.getConnection " + this.cm + " MCF: " + this.mcf);
        try {
            return (HadoopConnection) cm.allocateConnection(mcf, getConnectionRequestInfo());
        } catch (ResourceException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }
   
    public void setReference(Reference reference) {
        this.reference = reference;
    }

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