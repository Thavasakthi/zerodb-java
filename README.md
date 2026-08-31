# ZeroDB — A Lightweight Dependency-Free Embedded Key-Value Database

> **Tagline:** A lightweight, persistent, crash-recoverable key-value database built entirely from scratch using only the Java Standard Library.

[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://jdk.java.net/21/)
[![Dependencies](https://img.shields.io/badge/Dependencies-0-brightgreen.svg)](#hackathon-track-information)
[![Hackathon](https://img.shields.io/badge/Hackathon-Zero_Dependency_72h-blue.svg)](#hackathon-track-information)

---

## 1. Project Overview

**ZeroDB** is a high-performance, embedded, crash-recoverable key-value database written in **pure Java 21**. It delivers persistent local file storage, $O(1)$ fast in-memory index lookups, Write-Ahead Logging (WAL), automatic crash recovery, and thread-safe concurrency—**without requiring a single external dependency, JAR, Maven, Gradle, or third-party library**.

Designed for embedded workloads, local desktop applications, and edge services, ZeroDB exposes a clean, intuitive Command Line Interface (CLI) supporting operations such as `PUT`, `GET`, `DELETE`, `LIST`, `SIZE`, `CLEAR`, `HELP`, and `EXIT`.

---

## 2. Problem Statement

Modern Java applications often pull in hundreds of megabytes of external dependencies (`pom.xml` / `build.gradle` sprawl) for basic persistence needs (SQLite wrappers, JSON serializers, logging facades, CLI frameworks). This introduces security vulnerabilities (Log4j / Supply Chain risks), heavy build footprints, and runtime overhead.

**ZeroDB solves this problem** by proving that a full-featured, durable, crash-resilient key-value storage engine can be built using **only the Java Standard Library**.

---

## 3. Key Features

- 🚀 **Zero Dependencies**: 100% pure Java 21 standard library (`java.base`). No Maven, Gradle, Spring, Jackson, SQLite, or JUnit required.
- ⚡ **O(1) Instant Lookup Index**: Bitcask-inspired in-memory hash map index (`IndexManager`) mapping string keys to exact file byte offsets.
- 💾 **Persistent Append-Only Binary Engine**: Low-overhead binary storage engine using `java.nio.channels.FileChannel` and `ByteBuffer`.
- 🛡️ **Write-Ahead Logging (WAL)**: Ensures transactions are logged and channel-synced to disk before committing to the main database file.
- 🔄 **Automatic Crash Recovery**: Detects uncommitted transactions or corrupted records on startup, safely replaying pending operations and repairing log files.
- 🔒 **Thread-Safe Concurrency**: `ReentrantReadWriteLock` allows unlimited parallel reads while protecting concurrent write transactions.
- 🧪 **Self-Contained Test Suite**: Custom, 0-dependency automated test runner (`TestRunner.java`) verifying core operations, multithreading, persistence, and WAL recovery.
- 💻 **Interactive REPL CLI**: User-friendly interactive terminal interface with rich diagnostic reporting.

---

## 4. System Architecture

```
                  +-----------------------------------+
                  |      Interactive CLI / REPL       |
                  |         (zerodb.cli)              |
                  +-----------------+-----------------+
                                    |
                                    v
                  +-----------------------------------+
                  |         Database Interface        |
                  |         (zerodb.core)             |
                  | (ReentrantReadWriteLock Protection) |
                  +--------+-----------------+--------+
                           |                 |
             +-------------+                 +-------------+
             |                                             |
             v                                             v
+------------------------+                     +------------------------+
|      WAL Manager       |                     |     Storage Engine     |
|    (zerodb.wal)        |                     |   (zerodb.storage)     |
| Log-Ahead Durability   |                     | Append-Only Record Store|
+-----------+------------+                     +-----------+------------+
            |                                              |
            v                                              v
  [ zerodb.wal File ]                            [ zerodb.db File ]
                                                           ^
                                                           |
                                               +-----------+------------+
                                               |     Index Manager      |
                                               |   (In-Memory HashMap)  |
                                               | O(1) Key -> File Offset|
                                               +------------------------+
```

---

## 5. Installation & Requirements

### Prerequisites
- **JDK 21 or higher** installed and available in system `PATH`.
- Check Java version:
  ```bash
  java -version
  ```

---

## 6. Build & Run Instructions

### Option A: Using Provided Scripts (Recommended)

#### Windows:
```cmd
build.bat
```

#### Linux / macOS:
```bash
chmod +x build.sh
./build.sh
```

---

### Option B: Manual Single-Command Build & Run

#### Linux / macOS:
```bash
# 1. Compile all Java sources into 'out' directory
javac -d out $(find src tests -name "*.java")

# 2. Run automated zero-dependency test suite
java -cp out zerodb.test.TestRunner

# 3. Launch interactive database CLI
java -cp out zerodb.Main
```

#### Windows (PowerShell):
```powershell
# 1. Compile source code
javac -d out (Get-ChildItem -Recurse -Filter *.java -Path src, tests).FullName

# 2. Run test suite
java -cp out zerodb.test.TestRunner

# 3. Launch interactive CLI
java -cp out zerodb.Main
```

---

## 7. Interactive Usage Examples

Launch ZeroDB interactive prompt:

```text
=============================================================
 ______                _____  ____  
|___  /               |  __ \|  _ \ 
   / /  ___ _ __ ___  | |  | | |_) |
  / /  / _ \ '__/ _ \ | |  | |  _ < 
 / /__|  __/ | | (_) || |__| | |_) |
/_____|\___|_|  \___/ |_____/|____/ 

ZeroDB v1.0.0-HACKATHON
A Lightweight Dependency-Free Embedded Key-Value Database
Built strictly with Java 21 Standard Library ONLY
=============================================================

[READY] Loaded 0 key(s) into index. Type 'HELP' for available commands.

zerodb> PUT name Priya
OK

zerodb> GET name
Priya

zerodb> PUT role Java Developer
OK

zerodb> GET role
Java Developer

zerodb> LIST
name = Priya
role = Java Developer

zerodb> SIZE
Size: 2 key(s) | Active Keys: 2 | DB File Size: 114 bytes | WAL File Size: 0 bytes

zerodb> DELETE name
OK

zerodb> GET name
Key not found: name

zerodb> LIST
role = Java Developer

zerodb> EXIT
Goodbye!
```

---

## 8. Supported CLI Commands

| Command | Usage Syntax | Description |
| :--- | :--- | :--- |
| **`PUT`** | `PUT <key> <value>` | Inserts or updates a key-value pair. Values can contain multi-word strings. |
| **`GET`** | `GET <key>` | Retrieves the value associated with `<key>` in $O(1)$ time. |
| **`DELETE`** | `DELETE <key>` | Removes `<key>` by appending a tombstone record. |
| **`LIST`** | `LIST` | Displays a snapshot of all active key-value pairs. |
| **`SIZE`** | `SIZE` | Displays key count and disk usage statistics. |
| **`CLEAR`** | `CLEAR` | Purges all database keys and resets storage files. |
| **`HELP`** | `HELP` | Displays command syntax reference. |
| **`EXIT`** | `EXIT` | Safely closes database files and exits CLI. |

---

## 9. Storage Format Specification (`zerodb.db`)

ZeroDB stores records sequentially in an append-only binary file (`zerodb.db`).

### Binary Record Layout:
```
+------------------+-------------------+--------------------+-------------------+
| Field            | Size              | Type               | Description       |
+------------------+-------------------+--------------------+-------------------+
| Magic Byte       | 1 Byte            | byte (0x5A)        | Valid marker ('Z')|
| Operation Type   | 1 Byte            | byte (0x01/0x02)   | 0x01=PUT,0x02=DEL |
| Timestamp        | 8 Bytes           | long               | System time (ms)  |
| Key Length (K)   | 4 Bytes           | int                | Length of key     |
| Key Payload      | K Bytes           | byte[]             | UTF-8 Key bytes   |
| Value Length (V) | 4 Bytes           | int                | Length of value   |
| Value Payload    | V Bytes           | byte[]             | UTF-8 Value bytes |
| Checksum         | 8 Bytes           | long               | CRC32 Checksum    |
+------------------+-------------------+--------------------+-------------------+
Total Record Size = 26 + K + V Bytes
```

- **Magic Byte (`0x5A`)**: Validates record boundaries.
- **Tombstones**: `DELETE` operations write a tombstone record (`OpType = 0x02`, `Value Length = 0`).

---

## 10. Write-Ahead Log (WAL) & Crash Recovery Specification

To guarantee durability, write operations follow the WAL protocol:

### WAL Record Format (`zerodb.wal`):
`[WAL_MAGIC (1B)][Record Binary Payload][Status Byte (1B)]`

- `Status Byte = 0x00`: **PENDING** (Operation logged to WAL, channel forced to disk).
- `Status Byte = 0x01`: **COMMITTED** (Operation written to `zerodb.db`).

### Crash Recovery Protocol:
Upon initialization, `RecoveryManager`:
1. Scans `zerodb.db` to rebuild `IndexManager`.
2. Inspects `zerodb.wal` for any `PENDING` records.
3. Replays valid pending records into `zerodb.db` and updates the index.
4. Truncates/checkpoints the WAL file cleanly.

---

## 11. Thread Safety & Concurrency

ZeroDB uses `java.util.concurrent.locks.ReentrantReadWriteLock`:
- **Read Lock (`readLock()`)**: Acquired by `GET`, `LIST`, and `SIZE`. Multiple threads can read simultaneously without blocking.
- **Write Lock (`writeLock()`)**: Acquired by `PUT`, `DELETE`, and `CLEAR`. Ensures atomic, sequential disk appends.

---

## 12. Project Limitations

1. **In-Memory Key Footprint**: The index (`IndexManager`) keeps all keys and file offset entries in RAM. Key set size is limited by heap memory.
2. **Append-Only Growth**: `DELETE` and update operations append records to the data file. (Compaction clears tombstones during `CLEAR`).

---

## 13. Future Improvements

- [ ] Background Data File Compaction (Garbage Collector to purge old key versions & tombstones).
- [ ] LRU Value Cache for ultra-fast reading of frequent keys.
- [ ] Range Query Support (`SCAN prefix`).

---

## 14. Hackathon Track Information

- **Hackathon:** Zero Dependency | 72-Hour Hackathon
- **Track:** Standard Library Storage Engine
- **Dependencies:** 0 (Zero external libraries)
- **Language:** Java 21 Standard Library ONLY
- **Proof:** See [`DEPENDENCY_PROOF.md`](file:///e:/java/zerodb/DEPENDENCY_PROOF.md) and [`STDLIB.md`](file:///e:/java/zerodb/STDLIB.md).
