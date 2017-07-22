package org.imixs.workflow.archive.hadoop.jca.spi;



import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;
import javax.resource.ResourceException;
import javax.resource.spi.*;
import javax.security.auth.Subject;

public class HadoopManagedConnectionFactory
        implements ManagedConnectionFactory, Serializable {

   private int hash=-1;
	private static final long serialVersionUID = 1L;
	private PrintWriter out;

    public HadoopManagedConnectionFactory() {
    	hash=(int) System.currentTimeMillis();
        out = new PrintWriter(System.out);
        out.println("#HadoopManagedConnectionFactory.constructor - hash="+hash);
    }

    public Object createConnectionFactory(ConnectionManager cxManager) throws ResourceException {
        out.println("#HadoopManagedConnectionFactory.createConnectionFactory,1");
        return new HadoopDataSource(out,this, cxManager);
    }

    public Object createConnectionFactory() throws ResourceException {
        out.println("#HadoopManagedConnectionFactory.createManagedFactory,2");
        return new HadoopDataSource(out,this, null);
    }

    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo info) {
        out.println("#HadoopManagedConnectionFactory.createManagedConnection");
        return new HadoopManagedConnection(out,this, info);
    }

    public ManagedConnection matchManagedConnections(Set connectionSet, Subject subject, ConnectionRequestInfo info)
            throws ResourceException {
        out.println("#HadoopManagedConnectionFactory.matchManagedConnections Subject " + subject + " Info: " +  info);
        for (Iterator it = connectionSet.iterator(); it.hasNext();) {
            HadoopManagedConnection gmc = (HadoopManagedConnection) it.next();
            ConnectionRequestInfo connectionRequestInfo = gmc.getConnectionRequestInfo();
            if((info == null) || connectionRequestInfo.equals(info))
                return gmc;
        }
        throw new ResourceException("Cannot find connection for info!");
    }

    public void setLogWriter(PrintWriter out) throws ResourceException {
        out.println("#HadoopManagedConnectionFactory.setLogWriter");
        this.out = out;
    }

    public PrintWriter getLogWriter() throws ResourceException {
        out.println("#HadoopManagedConnectionFactory.getLogWriter");
        return this.out;
    }

    
    
    @Override
    public int hashCode() {
      return hash;
    }

    
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        /*
        final HadoopManagedConnectionFactory other = (HadoopManagedConnectionFactory) obj;
        if (this.connectionRequestInfo != other.connectionRequestInfo && (this.connectionRequestInfo == null || !this.connectionRequestInfo.equals(other.connectionRequestInfo))) {
            return false;
        }
        */
        return true;
    }
    
}