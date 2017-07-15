package org.imixs.workflow.archive.hadoop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;

import org.apache.hadoop.security.authentication.client.AuthenticationException;

/**
 * 
===== HTTP GET <br/>
<li>OPEN (see FileSystem.open)
<li>GETFILESTATUS (see FileSystem.getFileStatus)
<li>LISTSTATUS (see FileSystem.listStatus)
<li>GETCONTENTSUMMARY (see FileSystem.getContentSummary)
<li>GETFILECHECKSUM (see FileSystem.getFileChecksum)
<li>GETHOMEDIRECTORY (see FileSystem.getHomeDirectory)
<li>GETDELEGATIONTOKEN (see FileSystem.getDelegationToken)
<li>GETDELEGATIONTOKENS (see FileSystem.getDelegationTokens)
<br/>
===== HTTP PUT <br/>
<li>CREATE (see FileSystem.create)
<li>MKDIRS (see FileSystem.mkdirs)
<li>CREATESYMLINK (see FileContext.createSymlink)
<li>RENAME (see FileSystem.rename)
<li>SETREPLICATION (see FileSystem.setReplication)
<li>SETOWNER (see FileSystem.setOwner)
<li>SETPERMISSION (see FileSystem.setPermission)
<li>SETTIMES (see FileSystem.setTimes)
<li>RENEWDELEGATIONTOKEN (see FileSystem.renewDelegationToken)
<li>CANCELDELEGATIONTOKEN (see FileSystem.cancelDelegationToken)
<br/>
===== HTTP POST <br/>
APPEND (see FileSystem.append)
<br/>
===== HTTP DELETE <br/>
DELETE (see FileSystem.delete)

 */
public interface WebHDFSConnection {
	
	
	
/*
 * ========================================================================
 * GET	
 * ========================================================================
 */
	/**
	 * <b>GETHOMEDIRECTORY</b>
	 * 
	 * curl -i "http://<HOST>:<PORT>/webhdfs/v1/?op=GETHOMEDIRECTORY"
	 * 
	 * @return
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws AuthenticationException
	 */
	public String getHomeDirectory() throws MalformedURLException, IOException, AuthenticationException ;
	
	/**
	 * <b>OPEN</b>
	 * 
	 * curl -i -L "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=OPEN
	                    [&offset=<LONG>][&length=<LONG>][&buffersize=<INT>]"
	 * @param path
	 * @param os
	 * @throws AuthenticationException 
	 * @throws IOException 
	 * @throws MalformedURLException 
	 */
	public  String open(String path, OutputStream os) throws MalformedURLException, IOException, AuthenticationException ;
	
	/**
	 * <b>GETCONTENTSUMMARY</b>
	 * 
	 * curl -i "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=GETCONTENTSUMMARY"
	 * 
	 * @param path
	 * @return
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws AuthenticationException
	 */
	public  String getContentSummary(String path) throws MalformedURLException, IOException, AuthenticationException ;
	
	/**
	 * <b>LISTSTATUS</b>
	 * 
	 * curl -i  "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=LISTSTATUS"
	 * 
	 * @param path
	 * @return
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws AuthenticationException
	 */
	public  String listStatus(String path) throws MalformedURLException, IOException, AuthenticationException;
	
	/**
	 * <b>GETFILESTATUS</b>
	 * 
	 * curl -i  "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=GETFILESTATUS"
	 * 
	 * @param path
	 * @return
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws AuthenticationException
	 */
	public  String getFileStatus(String path) throws MalformedURLException, IOException, AuthenticationException;
	
	/**
	 * <b>GETFILECHECKSUM</b>
	 * 
	 * curl -i  "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=GETFILECHECKSUM"
	 * 
	 * @param path
	 * @return
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws AuthenticationException
	 */
	public  String getFileCheckSum(String path) throws MalformedURLException, IOException, AuthenticationException ;
	
/*
 * ========================================================================
 * PUT
 * ========================================================================	
 */
	/**
	 * <b>CREATE</b>
	 * 
	 * curl -i -X PUT "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=CREATE
                    [&overwrite=<true|false>][&blocksize=<LONG>][&replication=<SHORT>]
                    [&permission=<OCTAL>][&buffersize=<INT>]"
	 * @param path
	 * @param is
	 * @return
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws AuthenticationException
	 */
	public String create(String path, InputStream is) throws MalformedURLException, IOException, AuthenticationException;
	
	/**
	 * <b>MKDIRS</b>
	 * 
	 * curl -i -X PUT "http://<HOST>:<PORT>/<PATH>?op=MKDIRS[&permission=<OCTAL>]"
	 * 
	 * @param path
	 * @return
	 * @throws AuthenticationException 
	 * @throws IOException 
	 * @throws MalformedURLException 
	 */
	public String mkdirs(String path) throws MalformedURLException, IOException, AuthenticationException ;
	
	/**
	 * <b>CREATESYMLINK</b>
	 * 
	 * curl -i -X PUT "http://<HOST>:<PORT>/<PATH>?op=CREATESYMLINK
                              &destination=<PATH>[&createParent=<true|false>]"
	 * 
	 * @param path
	 * @return
	 * @throws AuthenticationException 
	 * @throws IOException 
	 * @throws MalformedURLException 
	 */
	public String createSymLink(String srcPath, String destPath) throws MalformedURLException, IOException, AuthenticationException ;		
	
	/**
	 * <b>RENAME</b>
	 * 
	 * curl -i -X PUT "http://<HOST>:<PORT>/<PATH>?op=RENAME
                              &destination=<PATH>[&createParent=<true|false>]"
	 * 
	 * @param path
	 * @return
	 * @throws AuthenticationException 
	 * @throws IOException 
	 * @throws MalformedURLException 
	 */
	public String rename(String srcPath, String destPath) throws MalformedURLException, IOException, AuthenticationException ;	
	
	/**
	 * <b>SETPERMISSION</b>
	 * 
	 * curl -i -X PUT "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=SETPERMISSION
                              [&permission=<OCTAL>]"
	 * 
	 * @param path
	 * @return
	 * @throws AuthenticationException 
	 * @throws IOException 
	 * @throws MalformedURLException 
	 */
	public String setPermission(String path) throws MalformedURLException, IOException, AuthenticationException ;
	
	/**
	 * <b>SETOWNER</b>
	 * 
	 * curl -i -X PUT "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=SETOWNER
                              [&owner=<USER>][&group=<GROUP>]"
	 * 
	 * @param path
	 * @return
	 * @throws AuthenticationException 
	 * @throws IOException 
	 * @throws MalformedURLException 
	 */
	public String setOwner(String path) throws MalformedURLException, IOException, AuthenticationException;
	
	/**
	 * <b>SETREPLICATION</b>
	 * 
	 * curl -i -X PUT "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=SETREPLICATION
                              [&replication=<SHORT>]"
	 * 
	 * @param path
	 * @return
	 * @throws AuthenticationException 
	 * @throws IOException 
	 * @throws MalformedURLException 
	 */
	public String setReplication(String path) throws MalformedURLException, IOException, AuthenticationException ;
	
	/**
	 * <b>SETTIMES</b>
	 * 
	 * curl -i -X PUT "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=SETTIMES
                              [&modificationtime=<TIME>][&accesstime=<TIME>]"
	 * 
	 * @param path
	 * @return
	 * @throws AuthenticationException 
	 * @throws IOException 
	 * @throws MalformedURLException 
	 */
	public String setTimes(String path) throws MalformedURLException, IOException, AuthenticationException;
	
/*
 * ========================================================================
 * POST	
 * ========================================================================
 */
	/**
	 * curl -i -X POST "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=APPEND[&buffersize=<INT>]"
	 * @param path
	 * @param is
	 * @return
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws AuthenticationException
	 */
	public String append(String path, InputStream is) throws MalformedURLException, IOException, AuthenticationException ;
/*
 * ========================================================================
 * DELETE	
 * ========================================================================
 */
	/**
	 * <b>DELETE</b>
	 * 
	 * curl -i -X DELETE "http://<host>:<port>/webhdfs/v1/<path>?op=DELETE
                              [&recursive=<true|false>]"
	 * 
	 * @param path
	 * @return
	 * @throws AuthenticationException 
	 * @throws IOException 
	 * @throws MalformedURLException 
	 */
	public String delete(String path) throws MalformedURLException, IOException, AuthenticationException ;
	
	
	
	
}