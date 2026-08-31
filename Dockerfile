# Stage 1: Build & Package
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY src/ ./src/
COPY tests/ ./tests/

# Compile & Package Executable JAR
RUN mkdir -p out && \
    javac -d out $(find src tests -name "*.java") && \
    java -cp out zerodb.test.TestRunner && \
    jar cfe zerodb.jar zerodb.Main -C out zerodb

# Stage 2: Minimal Production JRE Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/zerodb.jar /app/zerodb.jar

# Volume mount for persistent storage files (zerodb.db & zerodb.wal)
VOLUME [ "/app/data" ]

# Run ZeroDB
ENTRYPOINT ["java", "-jar", "/app/zerodb.jar"]
