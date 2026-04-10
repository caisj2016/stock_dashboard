@echo off
setlocal EnableDelayedExpansion
title Stock Dashboard

set "ROOT=%~dp0"
set "BACKEND_DIR=%ROOT%backend"
set "JAR_PATH=%BACKEND_DIR%\target\stock-dashboard-backend-0.0.1-SNAPSHOT.jar"
set "PORTFOLIO_FILE=%ROOT%portfolio.json"
set "BACKUP_DIR=%ROOT%data_backups"
set "APP_PORT=18080"

cd /d "%ROOT%"

echo.
echo  ================================================
echo   Japan Stock Portfolio Dashboard
echo  ================================================
echo.

if not exist "%BACKEND_DIR%\pom.xml" (
    echo  [ERROR] backend\pom.xml not found!
    echo  Please make sure the backend project exists under the current folder.
    echo.
    pause
    exit /b 1
)

call :find_port
if errorlevel 1 (
    echo  [ERROR] No available port found between 18080 and 18090.
    echo.
    pause
    exit /b 1
)

set "APP_URL=http://localhost:%APP_PORT%"

echo  ------------------------------------------------
echo   Server starting...
echo   URL: %APP_URL%
echo  ------------------------------------------------
echo.

start "" "%APP_URL%" >nul 2>&1

if exist "%JAR_PATH%" (
    echo  Starting packaged backend jar on port %APP_PORT%...
    echo.
    java -jar "%JAR_PATH%" --server.port=%APP_PORT% --app.portfolio.file="%PORTFOLIO_FILE%" --app.portfolio.backup-dir="%BACKUP_DIR%"
    goto :done
)

where mvn >nul 2>&1
if errorlevel 1 (
    echo  [ERROR] Maven not found and packaged jar is missing!
    echo  Please install Maven or build the backend jar first.
    echo.
    pause
    exit /b 1
)

echo  Packaged jar not found. Falling back to Maven run on port %APP_PORT%...
echo.
cd /d "%BACKEND_DIR%"
call mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=%APP_PORT%

:done
echo.
echo  Server stopped.
pause
exit /b 0

:find_port
for /L %%P in (18080,1,18090) do (
    netstat -ano | findstr /R /C:":%%P .*LISTENING" >nul 2>&1
    if errorlevel 1 (
        set "APP_PORT=%%P"
        exit /b 0
    )
)
exit /b 1
