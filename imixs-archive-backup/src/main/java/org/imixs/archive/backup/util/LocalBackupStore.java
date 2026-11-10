package org.imixs.archive.backup.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.imixs.archive.backup.BackupApi;
import org.imixs.archive.backup.BackupException;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.xml.XMLDocumentAdapter;

/**
 * BackupStore implementation for the local file system (e.g. a mounted NAS
 * volume).
 * <p>
 * The directory layout is identical to the FTP layout:
 * {root}/yyyy/MM/{uniqueid}.xml
 * <p>
 * Safety rules:
 * <ul>
 * <li>The root directory must already exist. It is never created automatically,
 * to avoid silently writing into the container file system if the backup volume
 * is not mounted.</li>
 * <li>Files are first written into a hidden temp file and then moved to the
 * final name. This avoids incomplete backup files after a crash or power
 * loss.</li>
 * </ul>
 *
 * @author rsoika
 */
public class LocalBackupStore implements BackupStore {

    public static final String LOCAL_ERROR = "LOCAL_ERROR";

    private static Logger logger = Logger.getLogger(LocalBackupStore.class.getName());

    private static final String TEMP_PREFIX = ".";
    private static final String TEMP_SUFFIX = ".tmp";

    private final Path rootPath;

    /**
     * Creates a new local backup store and verifies the root directory.
     *
     * @param rootDirectory the configured backup root directory
     * @throws BackupException if the root directory does not exist or is not
     *                         writable
     */
    public LocalBackupStore(String rootDirectory) throws BackupException {
        if (rootDirectory == null || rootDirectory.isBlank()) {
            throw new BackupException(LOCAL_ERROR, "Local backup failed: no root directory provided!");
        }
        rootPath = Paths.get(rootDirectory.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(rootPath)) {
            throw new BackupException(LOCAL_ERROR, "Local backup failed: root directory '" + rootPath
                    + "' does not exist - verify that the backup volume is mounted!");
        }
        if (!Files.isWritable(rootPath)) {
            throw new BackupException(LOCAL_ERROR,
                    "Local backup failed: root directory '" + rootPath + "' is not writable!");
        }
    }

    @Override
    public void put(ItemCollection snapshot, boolean overwrite) throws BackupException {
        String snapshotID = snapshot.getUniqueID();
        String originUniqueID = BackupApi.getUniqueIDFromSnapshotID(snapshotID);
        String fileName = originUniqueID + ".xml";

        Date created = snapshot.getItemValueDate(WorkflowKernel.CREATED);
        if (created == null) {
            throw new BackupException(LOCAL_ERROR,
                    "Local backup failed: snapshot " + snapshotID + " has no creation date!");
        }
        byte[] rawData = BackupApi.getRawData(snapshot);

        // same sub directory structure as the FTP storage: yyyy/MM
        Path targetDir = rootPath.resolve(new SimpleDateFormat("yyyy").format(created))
                .resolve(new SimpleDateFormat("MM").format(created));
        Path targetFile = targetDir.resolve(fileName);
        Path tempFile = targetDir.resolve(TEMP_PREFIX + fileName + TEMP_SUFFIX);

        try {
            // only the sub directories are created - the root directory must exist
            Files.createDirectories(targetDir);

            if (!overwrite && Files.exists(targetFile)) {
                logger.info("......file " + fileName + " already exists, skipping (overwrite=false)");
                return;
            }

            // write into a temp file and force the data to disk
            try (FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(rawData);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }

            // move the temp file to the final name
            try {
                Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.finest("...." + targetFile + " written successful");

        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e1) {
                logger.warning("Failed to delete temp file " + tempFile + ": " + e1.getMessage());
            }
            throw new BackupException(LOCAL_ERROR,
                    "Local backup failed: unable to write '" + targetFile + "' : " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listDirectories(String path) throws BackupException {
        return list(path, true);
    }

    @Override
    public List<String> listFiles(String path) throws BackupException {
        return list(path, false);
    }

    @Override
    public ItemCollection get(String path) throws BackupException {
        Path file = resolve(path);
        try {
            byte[] data = Files.readAllBytes(file);
            return XMLDocumentAdapter.readItemCollection(data);
        } catch (IOException | jakarta.xml.bind.JAXBException e) {
            throw new BackupException(LOCAL_ERROR, "Local read failed for '" + file + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        // no resources to release
    }

    /**
     * Lists the sorted names of directories or regular files. Hidden entries
     * (starting with '.') are ignored - this also excludes pending temp files.
     */
    private List<String> list(String path, boolean directories) throws BackupException {
        Path dir = resolve(path);
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> directories ? Files.isDirectory(p) : Files.isRegularFile(p))
                    .map(p -> p.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new BackupException(LOCAL_ERROR, "Local list failed for '" + dir + "': " + e.getMessage(), e);
        }
    }

    /**
     * Resolves a relative path against the root directory. Paths outside the root
     * directory are rejected.
     */
    private Path resolve(String path) throws BackupException {
        if (path == null || path.isBlank()) {
            return rootPath;
        }
        String relative = path.startsWith("/") ? path.substring(1) : path;
        Path result = rootPath.resolve(relative).normalize();
        if (!result.startsWith(rootPath)) {
            throw new BackupException(LOCAL_ERROR, "Invalid path outside of backup root: " + path);
        }
        return result;
    }
}