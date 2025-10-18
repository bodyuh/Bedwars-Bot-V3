@echo off
echo Building Bedwars Bot V3 manually...

REM Check if Java 8 is available
java -version
if %errorlevel% neq 0 (
    echo Java not found! Please install Java 8.
    pause
    exit /b 1
)

REM Create build directory
if not exist "build" mkdir build
if not exist "build\classes" mkdir build\classes
if not exist "build\libs" mkdir build\libs

echo.
echo Manual build process:
echo 1. Compile Kotlin source files
echo 2. Copy resources
echo 3. Create JAR file
echo.

REM Note: This is a simplified manual build
echo This requires manual compilation of Kotlin files.
echo For a complete build, use Java 16+ with the original build.gradle.kts
echo.
echo Alternative: Use IntelliJ IDEA or another IDE with Java 16+ support
echo to build the project, then use the resulting JAR.

pause

