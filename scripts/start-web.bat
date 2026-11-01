@echo off
chcp 65001 >nul
rem  Alias of start-nginx.bat : start the real nginx on port 8090
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-nginx.ps1" %*
echo.
pause
