@echo off
setlocal
set "ROOT=C:\laragon\www\student_portal\mit_portal-main"
cd /d "%ROOT%"

powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\ops\start-cloudflare-tidb-at-logon.ps1" -Port 8080 -StartupTimeoutSeconds 600

if errorlevel 1 (
    echo.
    echo Pilot startup failed. Check the private .env.cloudflare-tidb file and the log files under %%LOCALAPPDATA%%\MITPortal\logs
    pause
    exit /b 1
)

pause
