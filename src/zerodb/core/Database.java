package zerodb.core;

import zerodb.storage.IndexManager;
import zerodb.storage.Record;
import zerodb.storage.StorageEngine;
import zerodb.util.Constants;
import zerodb.wal.RecoveryManager;
import zerodb.wal.WALManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe embedded Key-Value Database facade engine.
 * Protected by ReentrantReadWriteLock for high-throughput concurrent access.
 */
public class Database implements AutoCloseable {

    private final StorageEngine storageEngine;
    private final WALManager walManager;
    private final IndexManager indexManager;
    private final ReentrantReadWriteLock rwLock;
    private final RecoveryManager.RecoveryReport recoveryReport;
    private boolean closed = false;

    public Database() throws IOException {
        this(Constants.DEFAULT_DB_FILE, Constants.DEFAULT_WAL_FILE);
    }

    public Database(String dbFilePath, String walFilePath) throws IOException {
        this.storageEngine = new StorageEngine(dbFilePath);
        this.walManager = new WALManager(walFilePath);
        this.indexManager = new IndexManager();
        this.rwLock = new ReentrantReadWriteLock();

        // Perform crash recovery & index reconstruction on initialization
        RecoveryManager recoveryManager = new RecoveryManager(storageEngine, walManager, indexManager);
        this.recoveryReport = recoveryManager.performRecovery();
    }

    public RecoveryManager.RecoveryReport getRecoveryReport() {
        return recoveryReport;
    }

    /**
     * Stores a key-value pair persistently in ZeroDB.
     */
    public boolean put(String key, String value) throws IOException {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty.");
        }
        if (value == null) {
            value = "";
        }

        rwLock.writeLock().lock();
        try {
            ensureOpen();
            Record record = new Record(Constants.OP_PUT, key.trim(), value);

            // 1. Write-Ahead Log entry (PENDING)
            long walOffset = walManager.logPending(record);

            // 2. Append to main DB file
            long dbOffset = storageEngine.appendRecord(record);
            int recordSize = record.getSerializedSize();

            // 3. Update WAL entry to COMMITTED
            walManager.markCommitted(walOffset, record);

            // 4. Update in-memory index
            indexManager.put(record.getKey(), dbOffset, recordSize, record.getTimestamp());

            // 5. Checkpoint WAL
            walManager.checkpoint();

            return true;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Retrieves a value by key in O(1) time using the in-memory index.
     */
    public String get(String key) throws IOException {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }

        rwLock.readLock().lock();
        try {
            ensureOpen();
            IndexManager.IndexEntry entry = indexManager.get(key.trim());
            if (entry == null) {
                return null;
            }

            Record record = storageEngine.readRecordAt(entry.getFileOffset());
            if (record == null || record.isTombstone() || !record.isValidChecksum()) {
                return null;
            }

            return record.getValue();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Deletes a key from the database by appending a tombstone record.
     */
    public boolean delete(String key) throws IOException {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }

        rwLock.writeLock().lock();
        try {
            ensureOpen();
            String cleanKey = key.trim();
            if (!indexManager.containsKey(cleanKey)) {
                return false;
            }

            Record record = new Record(Constants.OP_DELETE, cleanKey, "");

            // 1. Write WAL pending entry
            long walOffset = walManager.logPending(record);

            // 2. Write tombstone record to DB file
            long dbOffset = storageEngine.appendRecord(record);
            int recordSize = record.getSerializedSize();

            // 3. Mark WAL committed
            walManager.markCommitted(walOffset, record);

            // 4. Update index
            indexManager.remove(cleanKey, dbOffset, recordSize, record.getTimestamp());

            // 5. Checkpoint WAL
            walManager.checkpoint();

            return true;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Returns a snapshot of all active key-value pairs.
     */
    public Map<String, String> list() throws IOException {
        rwLock.readLock().lock();
        try {
            ensureOpen();
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, IndexManager.IndexEntry> entry : indexManager.getAllEntries().entrySet()) {
                if (!entry.getValue().isDeleted()) {
                    Record record = storageEngine.readRecordAt(entry.getValue().getFileOffset());
                    if (record != null && !record.isTombstone()) {
                        result.put(entry.getKey(), record.getValue());
                    }
                }
            }
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns the active number of keys stored.
     */
    public int size() {
        rwLock.readLock().lock();
        try {
            ensureOpen();
            return indexManager.activeKeyCount();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns statistics including disk space used.
     */
    public String getStats() throws IOException {
        rwLock.readLock().lock();
        try {
            ensureOpen();
            long dbSize = storageEngine.getFileSize();
            long walSize = walManager.getWalSize();
            int keyCount = indexManager.activeKeyCount();
            return String.format("Active Keys: %d | DB File Size: %d bytes | WAL File Size: %d bytes", keyCount, dbSize, walSize);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Clears all records from the database and index.
     */
    public void clear() throws IOException {
        rwLock.writeLock().lock();
        try {
            ensureOpen();
            storageEngine.clear();
            walManager.checkpoint();
            indexManager.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Database is closed.");
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            rwLock.writeLock().lock();
            try {
                closed = true;
                storageEngine.close();
                walManager.close();
            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }
}
