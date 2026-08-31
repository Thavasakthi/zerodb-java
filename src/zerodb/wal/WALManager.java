package zerodb.wal;

import zerodb.storage.Record;
import zerodb.util.Constants;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages Write-Ahead Logging (WAL) for durability and crash consistency.
 */
public class WALManager implements AutoCloseable {

    public static class WALEntry {
        private final Record record;
        private final byte status;
        private final long walOffset;

        public WALEntry(Record record, byte status, long walOffset) {
            this.record = record;
            this.status = status;
            this.walOffset = walOffset;
        }

        public Record getRecord() {
            return record;
        }

        public byte getStatus() {
            return status;
        }

        public boolean isPending() {
            return status == Constants.WAL_STATUS_PENDING;
        }

        public long getWalOffset() {
            return walOffset;
        }
    }

    private final File walFile;
    private RandomAccessFile raf;
    private FileChannel channel;

    public WALManager(String walFilePath) throws IOException {
        this.walFile = new File(walFilePath);
        initChannel();
    }

    private synchronized void initChannel() throws IOException {
        if (raf == null || !channel.isOpen()) {
            this.raf = new RandomAccessFile(walFile, "rw");
            this.channel = raf.getChannel();
        }
    }

    /**
     * Writes an operation to the WAL with PENDING status.
     * Flushes channel immediately.
     */
    public synchronized long logPending(Record record) throws IOException {
        long offset = channel.size();
        channel.position(offset);

        byte[] recordBytes = record.serialize();
        // WAL Entry: WAL_MAGIC_BYTE (1B) + RecordPayload + WAL_STATUS_PENDING (1B)
        ByteBuffer buffer = ByteBuffer.allocate(1 + recordBytes.length + 1);
        buffer.put(Constants.WAL_MAGIC_BYTE);
        buffer.put(recordBytes);
        buffer.put(Constants.WAL_STATUS_PENDING);

        buffer.flip();
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        channel.force(true);

        return offset;
    }

    /**
     * Marks a WAL entry as COMMITTED after successful write to DB file.
     */
    public synchronized void markCommitted(long walOffset, Record record) throws IOException {
        long statusOffset = walOffset + 1 + record.getSerializedSize();
        if (statusOffset < channel.size()) {
            channel.position(statusOffset);
            ByteBuffer buf = ByteBuffer.allocate(1);
            buf.put(Constants.WAL_STATUS_COMMITTED);
            buf.flip();
            channel.write(buf);
            channel.force(true);
        }
    }

    /**
     * Reads all WAL entries for crash recovery analysis.
     */
    public synchronized List<WALEntry> readAllEntries() throws IOException {
        List<WALEntry> entries = new ArrayList<>();
        channel.position(0);
        long currentOffset = 0;

        while (currentOffset < channel.size()) {
            channel.position(currentOffset);
            long startOffset = currentOffset;

            try {
                // Read WAL Magic Byte
                ByteBuffer walMagicBuf = ByteBuffer.allocate(1);
                int read = channel.read(walMagicBuf);
                if (read < 1) break;

                walMagicBuf.flip();
                byte walMagic = walMagicBuf.get();
                if (walMagic != Constants.WAL_MAGIC_BYTE) {
                    System.err.println("[WAL] Invalid WAL Magic Byte at offset " + startOffset + ". Truncating log.");
                    channel.truncate(startOffset);
                    break;
                }

                // Read DB Magic Byte + OpType + Timestamp + KeyLen
                ByteBuffer recHeader = ByteBuffer.allocate(1 + 1 + 8 + 4);
                int readHeader = channel.read(recHeader);
                if (readHeader < 14) {
                    System.err.println("[WAL] Incomplete WAL record header at offset " + startOffset + ". Truncating.");
                    channel.truncate(startOffset);
                    break;
                }
                recHeader.flip();

                byte dbMagic = recHeader.get();
                if (dbMagic != Constants.DB_MAGIC_BYTE) {
                    System.err.println("[WAL] Invalid DB Magic inside WAL at offset " + startOffset + ". Truncating.");
                    channel.truncate(startOffset);
                    break;
                }

                byte opType = recHeader.get();
                long timestamp = recHeader.getLong();
                int keyLen = recHeader.getInt();

                if (keyLen < 0 || keyLen > 1024 * 1024) {
                    throw new IOException("Invalid Key Length in WAL: " + keyLen);
                }

                byte[] keyBytes = new byte[keyLen];
                channel.read(ByteBuffer.wrap(keyBytes));
                String key = new String(keyBytes, StandardCharsets.UTF_8);

                ByteBuffer valLenBuf = ByteBuffer.allocate(4);
                channel.read(valLenBuf);
                valLenBuf.flip();
                int valLen = valLenBuf.getInt();

                if (valLen < 0 || valLen > 50 * 1024 * 1024) {
                    throw new IOException("Invalid Value Length in WAL: " + valLen);
                }

                byte[] valBytes = new byte[valLen];
                channel.read(ByteBuffer.wrap(valBytes));
                String value = new String(valBytes, StandardCharsets.UTF_8);

                ByteBuffer crcBuf = ByteBuffer.allocate(8);
                channel.read(crcBuf);
                crcBuf.flip();
                long checksum = crcBuf.getLong();

                ByteBuffer statusBuf = ByteBuffer.allocate(1);
                channel.read(statusBuf);
                statusBuf.flip();
                byte status = statusBuf.get();

                Record record = new Record(opType, timestamp, key, value, checksum);
                if (!record.isValidChecksum()) {
                    System.err.println("[WAL] Checksum mismatch in WAL at offset " + startOffset + ". Truncating log.");
                    channel.truncate(startOffset);
                    break;
                }

                entries.add(new WALEntry(record, status, startOffset));
                currentOffset = channel.position();
            } catch (Exception e) {
                System.err.println("[WAL] Truncating incomplete/corrupt WAL entry at offset " + startOffset);
                channel.truncate(startOffset);
                break;
            }
        }

        return entries;
    }

    /**
     * Checkpoints the WAL by truncating it to zero.
     */
    public synchronized void checkpoint() throws IOException {
        channel.truncate(0);
        channel.force(true);
    }

    public synchronized long getWalSize() throws IOException {
        return channel.size();
    }

    @Override
    public synchronized void close() throws IOException {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (raf != null) {
            raf.close();
        }
    }
}
