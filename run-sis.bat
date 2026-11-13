@echo off
REM ============================================================================
REM Makerere Institute of Technology Student Information Portal - Deployment and Startup Script for Windows
REM ============================================================================
REM This script automates setup and startup of the MIT Student Information Portal
REM Usage: run-sis.bat [command]
REM Commands: setup, build, run, clean, docker-up, docker-down, docker-reset
REM ============================================================================

setlocal enabledelayedexpansion

REM Project configuration
set "PROJECT_DIR=%~dp0"
cd /d "%PROJECT_DIR%"
set JAVA_VERSION=17 or newer
set MAVEN_VERSION=3.8.1
set PORT=8080
if not "%SIS_PORT%"=="" set "PORT=%SIS_PORT%"
set "SERVER_ADDRESS=127.0.0.1"

REM Color codes for output
set "GREEN=[92m"
set "RED=[91m"
set "YELLOW=[93m"
set "BLUE=[94m"
set "RESET=[0m"

REM Display header
echo.
echo %BLUE%===============================================%RESET%
echo %BLUE%  Makerere Institute of Technology%RESET%
echo %BLUE%  Student Information Portal%RESET%
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
if "%COMMAND%"=="docker-up" goto docker_up
if "%COMMAND%"=="docker-down" goto docker_down
if "%COMMAND%"=="docker-reset" goto docker_reset
if "%COMMAND%"=="help" goto help

echo %RED%[!] Unknown command: %COMMAND%%RESET%
goto help

REM ============================================================================
REM SETUP COMMAND - Prepare environment
REM ============================================================================
:setup
echo.
echo %BLUE%[*] Checking SIS local prerequisites...%RESET%

REM Check MySQL
echo %YELLOW%[*] Checking MySQL installation...%RESET%
mysql --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo %RED%[!] MySQL not found. Please install MySQL 5.7+ or MariaDB%RESET%
    echo Download from: https://www.mysql.com/downloads/
    exit /b 1
)
echo %GREEN%[+] MySQL found%RESET%

echo %GREEN%[+] Prerequisite check completed. Flyway manages the application schema at startup.%RESET%
goto end

REM ============================================================================
REM BUILD COMMAND - Compile project
REM ============================================================================
:build
echo.
echo %BLUE%[*] Building SIS project...%RESET%
echo %YELLOW%[*] Running: mvnw clean package%RESET%
call mvnw.cmd clean package
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

REM Stay on loopback unless the caller explicitly supplies an interface address.
if not "%~2"=="" set "SERVER_ADDRESS=%~2"
if /i "%SERVER_ADDRESS%"=="0.0.0.0" (
    echo %RED%[BLOCKED] Binding to all interfaces is not supported. Supply the exact trusted interface IP.%RESET%
    exit /b 1
)
if not "%SERVER_ADDRESS%"=="127.0.0.1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_DIR%ops\verify-lan-bind.ps1" -HostAddress "%SERVER_ADDRESS%"
    if errorlevel 1 (
        echo %RED%[BLOCKED] LAN checks failed. No server was started.%RESET%
        exit /b 1
    )
    set "LAN_ORIGIN=http://%SERVER_ADDRESS%:%PORT%"
    if defined CORS_ALLOWED_ORIGINS (
        set "CORS_ALLOWED_ORIGINS=%CORS_ALLOWED_ORIGINS%,%LAN_ORIGIN%"
    ) else (
        set "CORS_ALLOWED_ORIGINS=http://localhost:%PORT%,http://127.0.0.1:%PORT%,%LAN_ORIGIN%"
    )
    set "FRONTEND_URL=%LAN_ORIGIN%"
)
set "SERVER_PORT=%PORT%"
echo %YELLOW%[*] Server bind address: %SERVER_ADDRESS%:%PORT%%RESET%
if "%SERVER_ADDRESS%"=="127.0.0.1" (
    echo %YELLOW%[*] Local Portal: http://127.0.0.1:%PORT%/index.html%RESET%
) else (
    echo %YELLOW%[*] Interface Portal: http://%SERVER_ADDRESS%:%PORT%/index.html%RESET%
    echo %YELLOW%[*] LAN mode is only for a trusted Private Wi-Fi network with an isolated test database.%RESET%
)

REM Use the packaged artifact when available; otherwise run directly from source.
if exist "target\sis-0.0.1-SNAPSHOT.jar" goto run_jar

echo %YELLOW%[*] Packaged JAR not found. Starting from source with Maven...%RESET%
echo %YELLOW%[*] This path works even when Maven packaging plugins are unavailable.%RESET%
echo %YELLOW%[*] Application starting on http://localhost:%PORT%/%RESET%
echo %YELLOW%[*] Student Portal: http://localhost:%PORT%/index.html%RESET%
echo %YELLOW%[*] Admin Login: http://localhost:%PORT%/admin-login.html%RESET%
echo %YELLOW%[*] Press Ctrl+C to stop%RESET%
echo.

PowerShell -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_DIR%ops\run-local.ps1" -Mode maven -ServerAddress "%SERVER_ADDRESS%" -Port %PORT%
goto end

:run_jar
echo %YELLOW%[*] Application starting on http://localhost:%PORT%/%RESET%
echo %YELLOW%[*] Student Portal: http://localhost:%PORT%/index.html%RESET%
echo %YELLOW%[*] Admin Login: http://localhost:%PORT%/admin-login.html%RESET%
echo %YELLOW%[*] Press Ctrl+C to stop%RESET%
echo.

PowerShell -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_DIR%ops\run-local.ps1" -Mode jar -ServerAddress "%SERVER_ADDRESS%" -Port %PORT%
goto end

REM ============================================================================
REM DOCKER-UP COMMAND - Automated local Docker stack startup
REM ============================================================================
:docker_up
echo.
echo %BLUE%[*] Starting the Dockerized SIS stack...%RESET%
PowerShell -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_DIR%ops\start-local-dev.ps1"
if %ERRORLEVEL% NEQ 0 (
    echo %RED%[!] Docker startup failed%RESET%
    exit /b 1
)
goto end

REM ============================================================================
REM DOCKER-DOWN COMMAND - Stop the Dockerized SIS stack
REM ============================================================================
:docker_down
echo.
echo %BLUE%[*] Stopping the Dockerized SIS stack...%RESET%
PowerShell -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_DIR%ops\stop-local-dev.ps1"
if %ERRORLEVEL% NEQ 0 (
    echo %RED%[!] Docker shutdown failed%RESET%
    exit /b 1
)
goto end

REM ============================================================================
REM DOCKER-RESET COMMAND - Reset the Dockerized SIS stack
REM ============================================================================
:docker_reset
echo.
echo %YELLOW%[!] WARNING: This will destroy the Docker database volume and reset all local data!%RESET%
set /p CONFIRM="Are you sure? (yes/no): "
if /i not "%CONFIRM%"=="yes" (
    echo %YELLOW%[*] Docker reset cancelled%RESET%
    goto end
)
PowerShell -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_DIR%ops\start-local-dev.ps1" -Reset
if %ERRORLEVEL% NEQ 0 (
    echo %RED%[!] Docker reset failed%RESET%
    exit /b 1
)
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
REM RESET COMMAND - Disabled legacy direct database reset
REM ============================================================================
:reset
echo.
echo %RED%[BLOCKED] Direct MySQL reset is disabled to protect shared and existing databases.%RESET%
echo %YELLOW%[*] For disposable local testing only, use run-sis.bat docker-reset.%RESET%
exit /b 1

REM ============================================================================
REM HELP COMMAND - Display help
REM ============================================================================
:help
echo.
echo %BLUE%Available Commands:%RESET%
echo.
echo   %YELLOW%setup%RESET%      - Check local MySQL availability (does not modify the database)
echo   %YELLOW%build%RESET%      - Compile project using Maven
echo   %YELLOW%run%RESET%        - Start on loopback; optionally pass an exact interface IP
echo   %YELLOW%docker-up%RESET%   - Start the fully automated Docker stack
echo   %YELLOW%docker-down%RESET% - Stop the automated Docker stack
echo   %YELLOW%docker-reset%RESET% - Reset the Docker database volume and start fresh
echo   %YELLOW%clean%RESET%      - Remove build artifacts
echo   %YELLOW%reset%RESET%      - Disabled; use docker-reset only for disposable Compose data
echo   %YELLOW%help%RESET%       - Display this help message
echo.
echo %BLUE%Examples:%RESET%
echo   %YELLOW%run-sis.bat setup%RESET%       - Check local MySQL availability without changing it
echo   %YELLOW%run-sis.bat build%RESET%       - Compile project
echo   %YELLOW%run-sis.bat run%RESET%         - Start application
echo   %YELLOW%run-sis.bat run 192.168.1.25%RESET% - Bind only to that interface (trusted Private Wi-Fi only)
echo   %YELLOW%run-sis.bat docker-up%RESET%   - One-click Docker startup
echo   %YELLOW%run-sis.bat docker-reset%RESET% - Recreate the database and app stack
echo.
echo %BLUE%Quick Start:%RESET%
echo   1. run-sis.bat docker-up
echo   2. Open http://localhost:8080/index.html in browser
echo   3. run-sis.bat docker-down when you are done
echo.
goto end

REM ============================================================================
REM Cleanup and exit
REM ============================================================================
:end
endlocal
