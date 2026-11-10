package org.imixs.archive.backup.util;

import java.util.List;

import org.imixs.archive.backup.BackupException;
import org.imixs.workflow.ItemCollection;

/**
 * A BackupStore abstracts the physical backup storage (e.g. a FTP server or the
 * local file system).
 * <p>
 * All paths are relative to the configured backup root directory. The directory
 * layout is identical for all implementations:
 *
 * <pre>
 *   {root}/yyyy/MM/{uniqueid}.xml
 * </pre>
 *
 * A BackupStore holds resources (e.g. an open FTP connection) and must be
 * closed after usage.
 *
 * @author rsoika
 */
public interface BackupStore extends AutoCloseable {

    /**
     * Writes a snapshot into the backup store. The target sub directory is computed
     * from the snapshot creation date.
     *
     * @param snapshot  the snapshot to store
     * @param overwrite if false, an existing file will not be overwritten
     * @throws BackupException
     */
    void put(ItemCollection snapshot, boolean overwrite) throws BackupException;

    /**
     * Returns the names of all sub directories of the given path, sorted by name.
     *
     * @param path relative path; an empty string addresses the root directory
     * @throws BackupException
     */
    List<String> listDirectories(String path) throws BackupException;

    /**
     * Returns the names of all files of the given path, sorted by name.
     *
     * @param path relative path; an empty string addresses the root directory
     * @throws BackupException
     */
    List<String> listFiles(String path) throws BackupException;

    /**
     * Reads a snapshot from the backup store.
     *
     * @param path relative path of the file (e.g. "2024/03/abc.xml")
     * @throws BackupException
     */
    ItemCollection get(String path) throws BackupException;

    /**
     * Releases all resources. This method never throws an exception.
     */
    @Override
    void close();
}