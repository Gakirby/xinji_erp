@echo off
cd /d "%~dp0.."

echo.
echo   Xinji Mining ERP - front-end preview launcher
echo   ==================================================

where node >nul 2>nul
if errorlevel 1 (
  echo   [X] Node.js not found. Install Node 18+ first: https://nodejs.org/
  echo.
  pause
  exit /b 1
)

if not exist "deploy\html\index.html" (
  echo   [!] No build output found. Building three apps, about 30s...
  cd web
  call npm install --registry=https://registry.npmmirror.com
  call npm run build
  cd ..
)

set PORT=8090
if not "%~1"=="" set PORT=%~1

echo   Starting preview server on port %PORT% ...
start "xinji-web-preview" /min cmd /c "chcp 65001 >nul && node deploy\preview.mjs >> preview.log 2>&1"

ping -n 4 127.0.0.1 >nul

echo.
echo   Mall      http://localhost:%PORT%/
echo   Admin     http://localhost:%PORT%/admin/
echo   Mobile    http://localhost:%PORT%/m/
echo.
echo   The server runs in background. Closing this window will NOT stop it.
echo   To stop it: run scripts\stop-web.bat
echo   Log file : preview.log
echo.
start "" http://localhost:%PORT%/
pause
