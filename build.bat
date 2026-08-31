@echo off
REM ZeroDB Windows Build, Test, and Packaging Script
echo ===================================================
echo  Building ZeroDB (Java 21 Standard Library Only)
echo ===================================================

if not exist out mkdir out

echo Compiling Java source files...
javac -d out src\zerodb\Main.java src\zerodb\cli\*.java src\zerodb\core\*.java src\zerodb\storage\*.java src\zerodb\wal\*.java src\zerodb\util\*.java tests\zerodb\test\*.java

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    exit /b %ERRORLEVEL%
)

echo [SUCCESS] Compilation successful!
echo.
echo ===================================================
echo  Running Automated Test Suite
echo ===================================================
java -cp out zerodb.test.TestRunner

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Test suite failed!
    exit /b %ERRORLEVEL%
)

echo.
echo ===================================================
echo  Packaging Executable JAR (zerodb.jar)
echo ===================================================
jar cfe zerodb.jar zerodb.Main -C out zerodb

echo [SUCCESS] Executable zerodb.jar created!
echo.
echo Launching ZeroDB Interactive CLI...
echo ===================================================
java -jar zerodb.jar
