# ZeroDB — Java Standard Library Replacement Guide (`STDLIB.md`)

This document details 10+ standard third-party libraries and frameworks commonly used in production database development, alongside the **Java 21 Standard Library replacements** implemented entirely from scratch in **ZeroDB**.

---

## Standard Library Replacement Table

| # | Standard Industry Library / Tool | Standard Library Replacement in ZeroDB | Package / Class Used | Architectural Implementation Notes |
|---|:---|:---|:---|:---|
| **1** | **SQLite / RocksDB / H2** | Custom Log-Structured Append-Only Storage Engine | `java.io.RandomAccessFile`<br>`java.nio.channels.FileChannel` | Custom binary record format written directly to disk via byte channels with $O(1)$ indexed reads. |
| **2** | **Apache Commons CLI / Picocli** | Custom Interactive Command Parser & REPL | `java.util.Scanner`<br>`java.io.BufferedReader`<br>`java.lang.String` | Command routing, argument tokenization, multi-word value parsing, and formatted help menus. |
| **3** | **SLF4J / Logback / Log4j** | Standard Output Logging & Crash Diagnostics | `java.lang.System.out`<br>`java.lang.System.err` | Structured console alerts with colorized status markers (`[RECOVERY]`, `[READY]`, `[WARN]`, `[ERROR]`). |
| **4** | **Jackson / Gson / Fastjson** | Custom Binary Record Serialization | `java.nio.ByteBuffer`<br>`java.nio.charset.StandardCharsets` | High-efficiency binary serialization converting OpTypes, 64-bit timestamps, keys, values, and CRC checksums directly to raw byte arrays. |
| **5** | **JUnit 5 / TestNG / AssertJ** | Custom Zero-Dependency Test Suite & Runner | Java Functional Interfaces & Custom Assertions (`TestRunner.java`) | Functional runner inspecting pass/fail states across 8 test suites (PUT, GET, DELETE, Persistence, WAL Recovery, Corruption Repair, Multithreading). |
| **6** | **Guava Cache / Caffeine** | Custom In-Memory Hash Index (`IndexManager`) | `java.util.concurrent.ConcurrentHashMap` | In-memory index storing key-to-file-offset mappings ($O(1)$ lookup time) without disk scanning. |
| **7** | **Apache Commons Codec / PureJavaCrc32** | Standard Hardware-Accelerated Checksum Utility | `java.util.zip.CRC32` | Built-in CRC32 computation protecting records against bit flips, silent corruption, and partial disk writes. |
| **8** | **Apache Commons IO / Guava Files** | Java NIO File & Channel Systems | `java.nio.file.Files`<br>`java.io.File` | Platform-independent file path resolutions, channel truncations, and disk space statistics. |
| **9** | **Spring Boot / Google Guice (DI)** | Explicit Facade Constructor Injection | Core Standard Constructors | Decoupled architecture passing `StorageEngine`, `WALManager`, and `IndexManager` directly into `Database` facade. |
| **10**| **RxJava / Synchronized Wrappers** | High-Throughput Read-Write Locking | `java.util.concurrent.locks.ReentrantReadWriteLock` | Multiple readers can query keys concurrently while exclusive write lock protects `PUT`, `DELETE`, and `CLEAR`. |
| **11**| **Apache Commons Lang (StringUtils)** | Native String Operations & Text Blocks | Java 21 Text Blocks (`"""`) & String utilities | Raw multi-line string text blocks for CLI banners and formatted help documentation. |

---

## Detailed Code Example Comparison

### Example 1: Binary Serialization (Instead of Jackson JSON / Protobuf)

#### Industry Standard (Jackson):
```java
// Requires com.fasterxml.jackson.databind.ObjectMapper JARs
ObjectMapper mapper = new ObjectMapper();
byte[] jsonBytes = mapper.writeValueAsBytes(record);
```

#### ZeroDB Implementation (Pure JDK `ByteBuffer`):
```java
// Zero dependencies: zero overhead, compact binary byte array
ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + 8 + 4 + keyBytes.length + 4 + valBytes.length + 8);
buffer.put(Constants.DB_MAGIC_BYTE);
buffer.put(opType);
buffer.putLong(timestamp);
buffer.putInt(keyBytes.length);
buffer.put(keyBytes);
buffer.putInt(valBytes.length);
buffer.put(valBytes);
buffer.putLong(checksum);
return buffer.array();
```

---

### Example 2: Checksum Integrity Validation (Instead of Apache Commons Codec)

#### Industry Standard (Apache Commons):
```java
// Requires org.apache.commons.codec.digest.DigestUtils JARs
long crc = DigestUtils.crc32(payload);
```

#### ZeroDB Implementation (`java.util.zip.CRC32`):
```java
// Pure Java Standard Library
CRC32 crc = new CRC32();
crc.update(opType);
ByteBuffer buffer = ByteBuffer.allocate(8);
buffer.putLong(timestamp);
crc.update(buffer.array());
crc.update(keyBytes);
crc.update(valBytes);
return crc.getValue();
```
