package zerodb.test;

import zerodb.core.Database;
import zerodb.storage.Record;
import zerodb.util.Constants;
import zerodb.wal.WALManager;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom 0-dependency Test Runner for ZeroDB using Java 21 Standard Library only.
 */
public class TestRunner {

    private static final String TEST_DB = "test_zerodb.db";
    private static final String TEST_WAL = "test_zerodb.wal";

    private static int passedCount = 0;
    private static int failedCount = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println(" Running ZeroDB Standard Library Test Suite");
        System.out.println("=================================================");

        runTest("PUT and GET Operation Test", TestRunner::testPutAndGet);
        runTest("DELETE Operation Test", TestRunner::testDelete);
        runTest("Persistence Across Process Restart Test", TestRunner::testPersistenceAcrossRestart);
        runTest("Write-Ahead Log (WAL) Crash Recovery Test", TestRunner::testWalCrashRecovery);
        runTest("Corrupted Data Record Auto-Repair Test", TestRunner::testCorruptedRecordHandling);
        runTest("Concurrent Multithreaded Reads & Writes Test", TestRunner::testConcurrentReadsAndWrites);
        runTest("LIST, SIZE and CLEAR Operations Test", TestRunner::testListSizeClear);
        runTest("Edge Cases and Missing Key Handling Test", TestRunner::testEdgeCases);

        System.out.println("=================================================");
        System.out.println(String.format(" Test Results: %d PASSED | %d FAILED", passedCount, failedCount));
        System.out.println("=================================================");

        cleanupTestFiles();

        if (failedCount > 0) {
            System.exit(1);
        }
    }

    private static void runTest(String name, TestRunnable runnable) {
        cleanupTestFiles();
        System.out.print("[TEST] " + name + " ... ");
        try {
            runnable.run();
            System.out.println("PASSED");
            passedCount++;
        } catch (Throwable e) {
            System.out.println("FAILED");
            System.err.println("   -> Exception: " + e.getMessage());
            e.printStackTrace();
            failedCount++;
        } finally {
            cleanupTestFiles();
        }
    }

    @FunctionalInterface
    interface TestRunnable {
        void run() throws Exception;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(message + " | Expected: " + expected + ", Actual: " + actual);
    }

    private static void cleanupTestFiles() {
        new File(TEST_DB).delete();
        new File(TEST_WAL).delete();
    }

    // --- TEST IMPLEMENTATIONS ---

    private static void testPutAndGet() throws Exception {
        try (Database db = new Database(TEST_DB, TEST_WAL)) {
            db.put("name", "Priya");
            db.put("role", "Java Developer");

            assertEquals("Priya", db.get("name"), "Key 'name' should match inserted value.");
            assertEquals("Java Developer", db.get("role"), "Key 'role' should match inserted value.");
        }
    }

    private static void testDelete() throws Exception {
        try (Database db = new Database(TEST_DB, TEST_WAL)) {
            db.put("tempKey", "tempValue");
            assertEquals("tempValue", db.get("tempKey"), "Key must exist before delete.");

            boolean deleted = db.delete("tempKey");
            assertTrue(deleted, "Delete should return true for existing key.");

            assertEquals(null, db.get("tempKey"), "Key should be null after delete.");
            assertEquals(0, db.size(), "Database size should be 0 after delete.");
        }
    }

    private static void testPersistenceAcrossRestart() throws Exception {
        // Step 1: Write data and close DB
        try (Database db = new Database(TEST_DB, TEST_WAL)) {
            db.put("k1", "v1");
            db.put("k2", "v2");
            db.put("k3", "v3");
        }

        // Step 2: Re-open DB from disk
        try (Database db = new Database(TEST_DB, TEST_WAL)) {
            assertEquals(3, db.size(), "Size should be 3 after restarting DB.");
            assertEquals("v1", db.get("k1"), "k1 value must persist.");
            assertEquals("v2", db.get("k2"), "k2 value must persist.");
            assertEquals("v3", db.get("k3"), "k3 value must persist.");
        }
    }

    private static void testWalCrashRecovery() throws Exception {
        // Step 1: Initialize DB, write committed entry
        try (Database db = new Database(TEST_DB, TEST_WAL)) {
            db.put("committedKey", "committedVal");
        }

        // Step 2: Simulate power outage mid-transaction by manually writing pending record directly into WAL
        try (WALManager wal = new WALManager(TEST_WAL)) {
            Record pendingRecord = new Record(Constants.OP_PUT, "uncommittedKey", "recoveredVal");
            wal.logPending(pendingRecord); // Written with status PENDING (0x00), not yet written to DB file!
        }

        // Step 3: Startup Database engine. RecoveryManager should detect pending WAL record, replay it into DB!
        try (Database db = new Database(TEST_DB, TEST_WAL)) {
            assertEquals("committedVal", db.get("committedKey"), "Committed record must exist.");
            assertEquals("recoveredVal", db.get("uncommittedKey"), "Uncommitted WAL record must be recovered on startup!");
            assertTrue(db.getRecoveryReport().getPendingWalReplayed() > 0, "Recovery report must indicate replayed WAL record.");
        }
    }

    private static void testCorruptedRecordHandling() throws Exception {
        // Step 1: Insert valid records
        try (Database db = new Database(TEST_DB, TEST_WAL)) {
            db.put("validKey", "validValue");
        }

        // Step 2: Corrupt trailing bytes in DB file
        try (RandomAccessFile raf = new RandomAccessFile(TEST_DB, "rw");
             FileChannel channel = raf.getChannel()) {
            channel.position(channel.size());
            raf.write(new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66}); // Junk bytes
        }

        // Step 3: Re-open DB. StorageEngine rebuildIndex should detect bad record/checksum and truncate safely.
        try (Database db = new Database(TEST_DB, TEST_WAL)) {
            assertEquals("validValue", db.get("validKey"), "Valid record before corruption must remain accessible.");
            db.put("newKey", "newValue");
            assertEquals("newValue", db.get("newKey"), "Database must remain usable for new writes after corruption repair.");
        }
    }

    private static void testConcurrentReadsAndWrites() throws Exception {
        try (Database db = new Database(TEST_DB, TEST_WAL)) {
            int threadCount = 10;
            int opsPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    for (int i = 0; i < opsPerThread; i++) {
                        try {
                            String key = "thread_" + threadId + "_key_" + i;
                            String val = "val_" + i;
                            db.put(key, val);
                            String retrieved = db.get(key);
                            if (!val.equals(retrieved)) {
                                errorCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        }
                    }
                });
            }

            executor.shutdown();
            boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
            assertTrue(finished, "Multithreaded execution should finish within timeout.");
            assertEquals(0, errorCount.get(), "Concurrent reads and writes should yield zero errors.");
            assertEquals(threadCount * opsPerThread, db.size(), "Total key count should match total concurrent writes.");
        }
    }

    private static void testListSizeClear() throws Exception {
        try (Database db = new Database(TEST_DB, TEST_WAL)) {
            db.put("a", "1");
            db.put("b", "2");
            db.put("c", "3");

            assertEquals(3, db.size(), "Size should be 3.");
            Map<String, String> list = db.list();
            assertEquals(3, list.size(), "List snapshot should contain 3 entries.");
            assertEquals("1", list.get("a"), "List item 'a' should match.");

            db.clear();
            assertEquals(0, db.size(), "Size after clear should be 0.");
            assertEquals(0, db.list().size(), "List after clear should be empty.");
            assertEquals(null, db.get("a"), "GET after clear should return null.");
        }
    }

    private static void testEdgeCases() throws Exception {
        try (Database db = new Database(TEST_DB, TEST_WAL)) {
            assertEquals(null, db.get("nonExistentKey"), "Getting missing key should return null.");
            assertTrue(!db.delete("nonExistentKey"), "Deleting missing key should return false.");

            // Empty value test
            db.put("emptyValKey", "");
            assertEquals("", db.get("emptyValKey"), "Empty string value should be supported.");

            // Multi-word value with spaces
            db.put("role", "Senior Java & Storage Engineer");
            assertEquals("Senior Java & Storage Engineer", db.get("role"), "Multi-word value should be supported.");
        }
    }
}
