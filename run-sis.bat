@echo off
REM ============================================================================
REM School Information System - Deployment and Startup Script for Windows
REM ============================================================================
REM This script automates setup and startup of the SIS application
REM Usage: run-sis.bat [command]
REM Commands: setup, build, run, clean, reset
REM ============================================================================

setlocal enabledelayedexpansion

REM Project configuration
set PROJECT_DIR=%CD%
set JAVA_VERSION=17
set MAVEN_VERSION=3.8.1
set PORT=8080
set DB_NAME=sisdb
set UPLOAD_DIR=C:\uploads

REM Color codes for output
set "GREEN=[92m"
set "RED=[91m"
set "YELLOW=[93m"
set "BLUE=[94m"
set "RESET=[0m"

REM Display header
echo.
echo %BLUE%===============================================%RESET%
echo %BLUE%  School Information System - SIS%RESET%
echo %BLUE%  Deployment and Startup Script%RESET%
echo %BLUE%===============================================%RESET%
echo.

REM Check Java installation
echo %YELLOW%[*] Checking Java installation...%RESET%
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo %RED%[!] Java is not installed or not in PATH%RESET%
    echo Please install Java %JAVA_VERSION% or add it to your PATH
    exit /b 1
)
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| find "version"') do set JAVA_VER=%%v
echo %GREEN%[+] Java found: %JAVA_VER%%RESET%

REM Parse command line argument
set COMMAND=%1
if "%COMMAND%"=="" set COMMAND=run

REM Execute command
if "%COMMAND%"=="setup" goto setup
if "%COMMAND%"=="build" goto build
if "%COMMAND%"=="run" goto run
if "%COMMAND%"=="clean" goto clean
if "%COMMAND%"=="reset" goto reset
if "%COMMAND%"=="help" goto help

echo %RED%[!] Unknown command: %COMMAND%%RESET%
goto help

REM ============================================================================
REM SETUP COMMAND - Prepare environment
REM ============================================================================
:setup
echo.
echo %BLUE%[*] Setting up SIS environment...%RESET%

REM Create upload directory
if not exist "%UPLOAD_DIR%" (
    echo %YELLOW%[*] Creating upload directory: %UPLOAD_DIR%%RESET%
    mkdir "%UPLOAD_DIR%"
    mkdir "%UPLOAD_DIR%\students"
    mkdir "%UPLOAD_DIR%\teachers"
    echo %GREEN%[+] Upload directories created%RESET%
)

REM Check MySQL
echo %YELLOW%[*] Checking MySQL installation...%RESET%
mysql --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo %RED%[!] MySQL not found. Please install MySQL 5.7+ or MariaDB%RESET%
    echo Download from: https://www.mysql.com/downloads/
    exit /b 1
)
echo %GREEN%[+] MySQL found%RESET%

REM Create database
echo %YELLOW%[*] Initializing database...%RESET%
mysql -u root < src\main\resources\schema.sql
if %ERRORLEVEL% EQU 0 (
    echo %GREEN%[+] Database initialized successfully%RESET%
) else (
    echo %YELLOW%[!] Database initialization skipped (may already exist)%RESET%
)

echo %GREEN%[+] Setup completed!%RESET%
goto end

REM ============================================================================
REM BUILD COMMAND - Compile project
REM ============================================================================
:build
echo.
echo %BLUE%[*] Building SIS project...%RESET%
echo %YELLOW%[*] Running: mvn clean package%RESET%
call mvn clean package
if %ERRORLEVEL% EQU 0 (
    echo %GREEN%[+] Build completed successfully%RESET%
) else (
    echo %RED%[!] Build failed%RESET%
    exit /b 1
)
goto end

REM ============================================================================
REM RUN COMMAND - Start application
REM ============================================================================
:run
echo.
echo %BLUE%[*] Starting SIS application...%RESET%

REM Check if built
if not exist "target\sis-0.0.1-SNAPSHOT.jar" (
    echo %YELLOW%[*] JAR not found. Building project first...%RESET%
    call mvn clean package
)

REM Start application
echo %YELLOW%[*] Application starting on http://localhost:%PORT%%RESET%
echo %YELLOW%[*] Admin Dashboard: http://localhost:%PORT%/templates/admin-dashboard.html%RESET%
echo %YELLOW%[*] Press Ctrl+C to stop%RESET%
echo.

java -jar target\sis-0.0.1-SNAPSHOT.jar
goto end

REM ============================================================================
REM CLEAN COMMAND - Remove build artifacts
REM ============================================================================
:clean
echo.
echo %BLUE%[*] Cleaning build artifacts...%RESET%
if exist "target" (
    echo %YELLOW%[*] Removing target directory...%RESET%
    rmdir /s /q target
)
echo %GREEN%[+] Cleanup completed%RESET%
goto end

REM ============================================================================
REM RESET COMMAND - Reset database and cache
REM ============================================================================
:reset
echo.
echo %YELLOW%[!] WARNING: This will delete all data and reset the database!%RESET%
set /p CONFIRM="Are you sure? (yes/no): "
if /i not "%CONFIRM%"=="yes" (
    echo %YELLOW%[*] Reset cancelled%RESET%
    goto end
)

echo %BLUE%[*] Resetting database...%RESET%
mysql -u root -e "DROP DATABASE IF EXISTS %DB_NAME%;"
mysql -u root < src\main\resources\schema.sql
echo %GREEN%[+] Database reset completed%RESET%
goto end

REM ============================================================================
REM HELP COMMAND - Display help
REM ============================================================================
:help
echo.
echo %BLUE%Available Commands:%RESET%
echo.
echo   %YELLOW%setup%RESET%    - Prepare environment (create directories, init DB)
echo   %YELLOW%build%RESET%    - Compile project using Maven
echo   %YELLOW%run%RESET%      - Build and start the application
echo   %YELLOW%clean%RESET%    - Remove build artifacts
echo   %YELLOW%reset%RESET%    - Reset database and remove all data
echo   %YELLOW%help%RESET%     - Display this help message
echo.
echo %BLUE%Examples:%RESET%
echo   %YELLOW%run-sis.bat setup%RESET%     - Initialize environment
echo   %YELLOW%run-sis.bat build%RESET%     - Compile project
echo   %YELLOW%run-sis.bat run%RESET%       - Start application
echo.
echo %BLUE%Quick Start:%RESET%
echo   1. run-sis.bat setup
echo   2. run-sis.bat run
echo   3. Open http://localhost:8080 in browser
echo.
goto end

REM ============================================================================
REM Cleanup and exit
REM ============================================================================
:end
endlocal
