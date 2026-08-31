package zerodb.storage;

import zerodb.util.Constants;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * Handles persistent binary storage I/O on disk using FileChannel and ByteBuffer.
 * Appends records to zerodb.db and reads specific entries by file offset.
 */
public class StorageEngine implements AutoCloseable {

    private final File dbFile;
    private RandomAccessFile raf;
    private FileChannel channel;

    public StorageEngine(String dbFilePath) throws IOException {
        this.dbFile = new File(dbFilePath);
        initChannel();
    }

    private synchronized void initChannel() throws IOException {
        if (raf == null || !channel.isOpen()) {
            this.raf = new RandomAccessFile(dbFile, "rw");
            this.channel = raf.getChannel();
        }
    }

    /**
     * Appends a record to the database file and forces a disk sync.
     * @return The file byte offset where the record starts.
     */
    public synchronized long appendRecord(Record record) throws IOException {
        long offset = channel.size();
        channel.position(offset);

        byte[] payload = record.serialize();
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }

        // Ensure data is synced to physical storage
        channel.force(true);
        return offset;
    }

    /**
     * Reads a Record at a specific file offset.
     */
    public synchronized Record readRecordAt(long offset) throws IOException {
        if (offset < 0 || offset >= channel.size()) {
            return null;
        }

        channel.position(offset);
        return readRecordFromChannel(channel);
    }

    /**
     * Rebuilds the in-memory IndexManager by scanning zerodb.db sequentially from offset 0 to EOF.
     */
    public synchronized long rebuildIndex(IndexManager indexManager) throws IOException {
        indexManager.clear();
        channel.position(0);
        long currentOffset = 0;
        long totalRecordsCount = 0;

        while (currentOffset < channel.size()) {
            channel.position(currentOffset);
            long recordStartOffset = currentOffset;

            try {
                Record record = readRecordFromChannel(channel);
                if (record == null) {
                    break;
                }

                if (!record.isValidChecksum()) {
                    System.err.println("[WARN] Corrupted record detected at offset " + recordStartOffset + ". Truncating database file.");
                    channel.truncate(recordStartOffset);
                    break;
                }

                int recordSize = record.getSerializedSize();
                if (record.isTombstone()) {
                    indexManager.remove(record.getKey(), recordStartOffset, recordSize, record.getTimestamp());
                } else {
                    indexManager.put(record.getKey(), recordStartOffset, recordSize, record.getTimestamp());
                }

                currentOffset += recordSize;
                totalRecordsCount++;
            } catch (IOException e) {
                System.err.println("[WARN] Incomplete/Corrupted record at end of file offset " + recordStartOffset + ". Truncating.");
                channel.truncate(recordStartOffset);
                break;
            }
        }

        return totalRecordsCount;
    }

    /**
     * Reads a single record starting from the current position of the channel.
     */
    private Record readRecordFromChannel(FileChannel fc) throws IOException {
        // Record format: Magic (1B) + Op (1B) + Timestamp (8B) + KeyLen (4B)
        ByteBuffer header = ByteBuffer.allocate(1 + 1 + 8 + 4);
        int bytesRead = fc.read(header);
        if (bytesRead < 14) {
            return null;
        }

        header.flip();
        byte magic = header.get();
        if (magic != Constants.DB_MAGIC_BYTE) {
            throw new IOException("Invalid DB Magic Byte: 0x" + Integer.toHexString(magic & 0xFF));
        }

        byte opType = header.get();
        long timestamp = header.getLong();
        int keyLen = header.getInt();

        if (keyLen < 0 || keyLen > 1024 * 1024) { // safety limit 1MB key
            throw new IOException("Invalid Key Length: " + keyLen);
        }

        byte[] keyBytes = new byte[keyLen];
        ByteBuffer keyBuf = ByteBuffer.wrap(keyBytes);
        fc.read(keyBuf);
        String key = new String(keyBytes, StandardCharsets.UTF_8);

        ByteBuffer valLenBuf = ByteBuffer.allocate(4);
        fc.read(valLenBuf);
        valLenBuf.flip();
        int valLen = valLenBuf.getInt();

        if (valLen < 0 || valLen > 50 * 1024 * 1024) { // safety limit 50MB value
            throw new IOException("Invalid Value Length: " + valLen);
        }

        byte[] valBytes = new byte[valLen];
        ByteBuffer valBuf = ByteBuffer.wrap(valBytes);
        fc.read(valBuf);
        String value = new String(valBytes, StandardCharsets.UTF_8);

        ByteBuffer crcBuf = ByteBuffer.allocate(8);
        fc.read(crcBuf);
        crcBuf.flip();
        long checksum = crcBuf.getLong();

        return new Record(opType, timestamp, key, value, checksum);
    }

    public synchronized void clear() throws IOException {
        channel.truncate(0);
        channel.force(true);
    }

    public synchronized long getFileSize() throws IOException {
        return channel.size();
    }

    public synchronized void close() throws IOException {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (raf != null) {
            raf.close();
        }
    }
}
