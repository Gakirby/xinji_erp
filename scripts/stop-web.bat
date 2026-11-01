@echo off
chcp 65001 >nul
rem  Alias of stop-nginx.bat : stop nginx
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-nginx.ps1" %*
echo.
pause
