package zerodb.storage;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the in-memory index for ZeroDB.
 * Provides O(1) key lookups mapping to byte positions in the persistent data file.
 */
public class IndexManager {

    public static class IndexEntry {
        private final long fileOffset;
        private final int recordSize;
        private final long timestamp;
        private final boolean isDeleted;

        public IndexEntry(long fileOffset, int recordSize, long timestamp, boolean isDeleted) {
            this.fileOffset = fileOffset;
            this.recordSize = recordSize;
            this.timestamp = timestamp;
            this.isDeleted = isDeleted;
        }

        public long getFileOffset() {
            return fileOffset;
        }

        public int getRecordSize() {
            return recordSize;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isDeleted() {
            return isDeleted;
        }
    }

    private final Map<String, IndexEntry> index = new ConcurrentHashMap<>();

    public void put(String key, long offset, int size, long timestamp) {
        index.put(key, new IndexEntry(offset, size, timestamp, false));
    }

    public void remove(String key, long offset, int size, long timestamp) {
        index.put(key, new IndexEntry(offset, size, timestamp, true));
    }

    public IndexEntry get(String key) {
        IndexEntry entry = index.get(key);
        if (entry == null || entry.isDeleted()) {
            return null;
        }
        return entry;
    }

    public boolean containsKey(String key) {
        IndexEntry entry = index.get(key);
        return entry != null && !entry.isDeleted();
    }

    public int activeKeyCount() {
        int count = 0;
        for (IndexEntry entry : index.values()) {
            if (!entry.isDeleted()) {
                count++;
            }
        }
        return count;
    }

    public Set<String> getActiveKeys() {
        return index.keySet();
    }

    public Map<String, IndexEntry> getAllEntries() {
        return Collections.unmodifiableMap(index);
    }

    public void clear() {
        index.clear();
    }
}
