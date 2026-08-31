package zerodb.storage;

import zerodb.util.CRC32Utils;
import zerodb.util.Constants;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Represents a persistent key-value binary record in ZeroDB.
 * Record Format:
 * [Magic Byte (1B)][OpType (1B)][Timestamp (8B)][KeyLength (4B)][KeyBytes][ValLength (4B)][ValBytes][Checksum (8B)]
 */
public class Record {
    private final byte opType;
    private final long timestamp;
    private final String key;
    private final String value;
    private final long checksum;

    public Record(byte opType, String key, String value) {
        this(opType, System.currentTimeMillis(), key, value);
    }

    public Record(byte opType, long timestamp, String key, String value) {
        this.opType = opType;
        this.timestamp = timestamp;
        this.key = key;
        this.value = value != null ? value : "";
        
        byte[] keyBytes = this.key.getBytes(StandardCharsets.UTF_8);
        byte[] valBytes = this.value.getBytes(StandardCharsets.UTF_8);
        this.checksum = CRC32Utils.calculateChecksum(this.opType, this.timestamp, keyBytes, valBytes);
    }

    public Record(byte opType, long timestamp, String key, String value, long checksum) {
        this.opType = opType;
        this.timestamp = timestamp;
        this.key = key;
        this.value = value != null ? value : "";
        this.checksum = checksum;
    }

    public byte getOpType() {
        return opType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public long getChecksum() {
        return checksum;
    }

    public boolean isTombstone() {
        return opType == Constants.OP_DELETE;
    }

    public boolean isValidChecksum() {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valBytes = value.getBytes(StandardCharsets.UTF_8);
        long expected = CRC32Utils.calculateChecksum(opType, timestamp, keyBytes, valBytes);
        return expected == checksum;
    }

    /**
     * Serializes this record into a binary byte array.
     */
    public byte[] serialize() {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valBytes = value.getBytes(StandardCharsets.UTF_8);

        int totalSize = 1 + 1 + 8 + 4 + keyBytes.length + 4 + valBytes.length + 8;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);

        buffer.put(Constants.DB_MAGIC_BYTE);
        buffer.put(opType);
        buffer.putLong(timestamp);
        buffer.putInt(keyBytes.length);
        buffer.put(keyBytes);
        buffer.putInt(valBytes.length);
        buffer.put(valBytes);
        buffer.putLong(checksum);

        return buffer.array();
    }

    public int getSerializedSize() {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valBytes = value.getBytes(StandardCharsets.UTF_8);
        return 1 + 1 + 8 + 4 + keyBytes.length + 4 + valBytes.length + 8;
    }

    @Override
    public String toString() {
        return "Record{" +
                "opType=" + (opType == Constants.OP_PUT ? "PUT" : "DELETE") +
                ", timestamp=" + timestamp +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", checksum=" + checksum +
                '}';
    }
}
