#!/usr/bin/env bash
# ZeroDB Linux/macOS Build, Test, and Packaging Script
set -e

echo "==================================================="
echo " Building ZeroDB (Java 21 Standard Library Only)"
echo "==================================================="

mkdir -p out

echo "Compiling Java source files..."
javac -d out $(find src tests -name "*.java")

echo "[SUCCESS] Compilation successful!"
echo ""
echo "==================================================="
echo " Running Automated Test Suite"
echo "==================================================="
java -cp out zerodb.test.TestRunner

echo ""
echo "==================================================="
echo " Packaging Executable JAR (zerodb.jar)"
echo "==================================================="
jar cfe zerodb.jar zerodb.Main -C out zerodb

echo "[SUCCESS] Executable zerodb.jar created!"
echo ""
echo "Launching ZeroDB Interactive CLI..."
echo "==================================================="
exec java -jar zerodb.jar
