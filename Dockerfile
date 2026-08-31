# ==============================================================================
# ZeroDB — Zero-Dependency Dockerfile
# Built strictly using standard JDK 21 tools (javac, java, jar)
# No Maven, No Gradle, No External Plugins or Build Frameworks
# ==============================================================================

# Stage 1: Build & Run Test Suite
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY src/ ./src/
COPY tests/ ./tests/

# Compile with pure javac, execute test suite, and package executable JAR
RUN mkdir -p out && \
    javac -d out $(find src tests -name "*.java") && \
    java -cp out zerodb.test.TestRunner && \
    jar cfe zerodb.jar zerodb.Main -C out .

# Stage 2: Ultra-minimal Runtime Environment
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/zerodb.jar /app/zerodb.jar

# Mount volume for persistent database files (zerodb.db & zerodb.wal)
VOLUME [ "/app/data" ]

# Run ZeroDB Interactive CLI
ENTRYPOINT ["java", "-jar", "/app/zerodb.jar"]

