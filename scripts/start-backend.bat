@echo off
cd /d "%~dp0.."

echo.
echo   Xinji Mining ERP - back-end launcher (runs the packaged jar)
echo   ==================================================

rem  If something already listens on 8080 (e.g. IDEA running ErpApplication), just exit
netstat -ano | findstr /R /C:":8080 .*LISTENING" >nul 2>&1
if %errorlevel%==0 (
  echo   [OK] Port 8080 already has a backend running. Nothing to do.
  echo.
  pause
  exit /b 0
)

set JAR=server\target\xinji-erp.jar
if not exist "%JAR%" (
  echo   [X] Cannot find %JAR%
  echo       Run ErpApplication in IDEA, or build it with:
  echo           cd server and mvn clean package -DskipTests
  echo.
  pause
  exit /b 1
)

set JAVA_EXE=java
if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\java.exe" set JAVA_EXE="%JAVA_HOME%\bin\java.exe"

echo   Backend will be on http://localhost:8080
echo   Requires: local MySQL (schema xinji_erp) and Redis are running
echo.
start "xinji-server" /min cmd /c "chcp 65001 >nul && %JAVA_EXE% -Dfile.encoding=UTF-8 -jar server\target\xinji-erp.jar --server.port=8080"

echo   Starting... in about 10 seconds you can open:
echo       http://localhost:8080/actuator/health
echo       http://localhost:8080/swagger-ui.html
echo.
echo   The minimized window shows the log. Closing it stops the backend.
echo.
pause
