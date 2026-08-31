package zerodb.util;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * Utility for computing and verifying CRC32 checksums using Java Standard Library.
 */
public final class CRC32Utils {

    private CRC32Utils() {
        // Utility class
    }

    /**
     * Compute CRC32 checksum for a database record payload.
     * Includes opType, timestamp, key, and value.
     */
    public static long calculateChecksum(byte opType, long timestamp, byte[] keyBytes, byte[] valueBytes) {
        CRC32 crc = new CRC32();
        crc.update(opType);
        
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(timestamp);
        crc.update(buffer.array());
        
        if (keyBytes != null && keyBytes.length > 0) {
            crc.update(keyBytes);
        }
        if (valueBytes != null && valueBytes.length > 0) {
            crc.update(valueBytes);
        }
        
        return crc.getValue();
    }
}
