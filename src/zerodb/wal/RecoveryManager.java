package zerodb.wal;

import zerodb.storage.IndexManager;
import zerodb.storage.Record;
import zerodb.storage.StorageEngine;

import java.io.IOException;
import java.util.List;

/**
 * Handles database crash recovery by scanning WAL and DB storage on startup.
 */
public class RecoveryManager {

    private final StorageEngine storageEngine;
    private final WALManager walManager;
    private final IndexManager indexManager;

    public RecoveryManager(StorageEngine storageEngine, WALManager walManager, IndexManager indexManager) {
        this.storageEngine = storageEngine;
        this.walManager = walManager;
        this.indexManager = indexManager;
    }

    public static class RecoveryReport {
        private final long dbRecordsLoaded;
        private final int pendingWalReplayed;
        private final boolean cleanShutdown;

        public RecoveryReport(long dbRecordsLoaded, int pendingWalReplayed, boolean cleanShutdown) {
            this.dbRecordsLoaded = dbRecordsLoaded;
            this.pendingWalReplayed = pendingWalReplayed;
            this.cleanShutdown = cleanShutdown;
        }

        public long getDbRecordsLoaded() {
            return dbRecordsLoaded;
        }

        public int getPendingWalReplayed() {
            return pendingWalReplayed;
        }

        public boolean isCleanShutdown() {
            return cleanShutdown;
        }

        @Override
        public String toString() {
            return "RecoveryReport{" +
                    "dbRecordsLoaded=" + dbRecordsLoaded +
                    ", pendingWalReplayed=" + pendingWalReplayed +
                    ", cleanShutdown=" + cleanShutdown +
                    '}';
        }
    }

    /**
     * Executes the recovery protocol on startup.
     */
    public RecoveryReport performRecovery() throws IOException {
        // Step 1: Rebuild index from main database file
        long dbRecordsCount = storageEngine.rebuildIndex(indexManager);

        // Step 2: Read WAL entries and check for pending operations
        List<WALManager.WALEntry> walEntries = walManager.readAllEntries();
        int replayedCount = 0;
        boolean cleanShutdown = true;

        for (WALManager.WALEntry walEntry : walEntries) {
            if (walEntry.isPending()) {
                cleanShutdown = false;
                Record record = walEntry.getRecord();
                
                // Replay record into storage engine
                long newDbOffset = storageEngine.appendRecord(record);
                int recordSize = record.getSerializedSize();

                if (record.isTombstone()) {
                    indexManager.remove(record.getKey(), newDbOffset, recordSize, record.getTimestamp());
                } else {
                    indexManager.put(record.getKey(), newDbOffset, recordSize, record.getTimestamp());
                }

                walManager.markCommitted(walEntry.getWalOffset(), record);
                replayedCount++;
            }
        }

        // Step 3: Checkpoint WAL if any records were processed
        if (!walEntries.isEmpty()) {
            walManager.checkpoint();
        }

        return new RecoveryReport(dbRecordsCount, replayedCount, cleanShutdown);
    }
}
