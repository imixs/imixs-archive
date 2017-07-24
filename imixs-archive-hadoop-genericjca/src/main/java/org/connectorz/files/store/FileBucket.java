package org.connectorz.files.store;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.resource.ResourceException;

import org.connectorz.files.Bucket;

public class FileBucket implements Bucket {

    private String rootDirectory;
    private ConcurrentHashMap<String, byte[]> txCache;
    private Set<String> deletedFiles;
    private Closeable closeable;
    private PrintWriter out;

    public FileBucket(PrintWriter out, String rootDirectory, Closeable closeable) {
        this.out = out;
        this.rootDirectory = rootDirectory;
        out.println("#FileBucket " + toString());
        this.closeable = closeable;
        this.txCache = new ConcurrentHashMap<>();
        this.deletedFiles = new ConcurrentSkipListSet<>();
    }


    void createIfNotExists(String folderName) {
        File file = new File(folderName);
        if (file.exists() && file.isDirectory()) {
            return;
        }
        file.mkdirs();

    }

    @Override
    public void write(String fileName, byte[] content) {
        out.println("#FileBucket.write " + fileName + " " + content);
        final byte[] existingContent = this.txCache.get(fileName);
        if (existingContent == null) {
             this.txCache.put(fileName, content);
        } else {
            this.txCache.put(fileName, concat(existingContent, content));
        }
    }

    private byte[] concat(byte[] a, byte[] b) {
        final byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    public void begin() throws ResourceException {
        out.println("#FileBucket.begin " + toString());
        this.createIfNotExists(this.rootDirectory);
    }

    public void commit() throws ResourceException {
        out.println("#FileBucket.commit " + toString());
        try {
            processDeletions();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot delete files: " + ex, ex);
        }
        flushChanges();
    }

    public void rollback() throws ResourceException {
        out.println("#FileBucket.rollback  " + toString());
        this.clear();
    }

    public void destroy() {
        out.println("#FileBucket.cleanup");
        this.clear();
    }

    private void flushChanges() throws ResourceException {
        Set<Entry<String, byte[]>> txSet = this.txCache.entrySet();
        for (Entry<String, byte[]> entry : txSet) {
            String fileName = entry.getKey();
            byte[] value = entry.getValue();
            writeFile(fileName, value);
            this.txCache.remove(fileName);
        }
    }

    void processDeletions() throws IOException {
        for (String fileName : deletedFiles) {
            deleteFile(getAbsoluteName(fileName));
        }
    }

    void writeFile(String fileName, byte[] content) throws ResourceException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(getAbsoluteName(fileName), true)) {
            fileOutputStream.write(content);
            fileOutputStream.flush();
        } catch (IOException ex) {
            throw new ResourceException(ex);
        } 
    }

    String getAbsoluteName(String fileName) {
        return this.rootDirectory + fileName;
    }

    @Override
    public byte[] fetch(String file) {
        try {
            final byte[] fileContent = readFromFile(getAbsoluteName(file));
            final byte[] txContent = this.txCache.get(file);
            if (fileContent == null) {
                return txContent;
            } else {
                if (txContent == null) {
                    return fileContent;
                } else {
                    return concat(fileContent, txContent);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot access file: " + getAbsoluteName(file), ex);
        }
    }

	@Override
	public FileTime lastModified(String file) {
		try {
			return Files.getLastModifiedTime(Paths.get(getAbsoluteName(file)));
		} catch (IOException ex) {
			throw new IllegalStateException("Cannot access file: "+getAbsoluteName(file), ex);
		}
	}	

	
    byte[] readFromFile(String fileName) throws IOException {
        Path file = Paths.get(fileName);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        return Files.readAllBytes(file);
    }

    void deleteFile(String absoluteName) throws IOException {
        Path file = Paths.get(absoluteName);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.deleteIfExists(file);
    }

    @Override
    public void delete(String file) {
        this.txCache.remove(file);
        this.deletedFiles.add(file);
    }
    @Override
    public void close() {
        try {
            this.closeable.close();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot close GenericManagedConnection",ex);
        }
    }

    public void clear() {
        this.txCache.clear();
        this.deletedFiles.clear();
    }

    @Override
    public String toString() {
        return "FileBucket{" +  "rootDirectory=" + rootDirectory + ", txCache=" + txCache + ", genericManagedConnection=" + closeable + '}';
    }
}