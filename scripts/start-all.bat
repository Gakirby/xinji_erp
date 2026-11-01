@echo off
chcp 65001 >nul
rem  One-click start: backend jar (8080) + real nginx (8090)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1" %*
echo.
pause
