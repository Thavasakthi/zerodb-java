package zerodb.util;

/**
 * Constants used across ZeroDB storage engine, WAL, and CLI.
 */
public final class Constants {

    private Constants() {
        // Utility class, prevent instantiation
    }

    public static final String DEFAULT_DB_FILE = "zerodb.db";
    public static final String DEFAULT_WAL_FILE = "zerodb.wal";

    public static final byte DB_MAGIC_BYTE = (byte) 0x5A; // 'Z'
    public static final byte WAL_MAGIC_BYTE = (byte) 0xA5;

    public static final byte OP_PUT = (byte) 0x01;
    public static final byte OP_DELETE = (byte) 0x02;

    public static final byte WAL_STATUS_PENDING = (byte) 0x00;
    public static final byte WAL_STATUS_COMMITTED = (byte) 0x01;

    public static final String CLI_PROMPT = "zerodb> ";
    public static final String VERSION = "1.0.0-HACKATHON";
}
