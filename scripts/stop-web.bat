@echo off
cd /d "%~dp0.."

set PORT=8090
if not "%~1"=="" set PORT=%~1

echo.
echo   Stopping preview server on port %PORT% ...

set FOUND=0
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%PORT%" ^| findstr LISTENING') do (
  echo   Killing PID %%a
  taskkill /f /pid %%a >nul 2>&1
  set FOUND=1
)

if "%FOUND%"=="0" echo   Nothing is running on port %PORT%
echo   Done.
echo.
pause
