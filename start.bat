@echo off
title Stock Dashboard

echo.
echo  ================================================
echo   Japan Stock Portfolio Dashboard
echo  ================================================
echo.

where python >nul 2>&1
if errorlevel 1 (
    where py >nul 2>&1
    if errorlevel 1 (
        echo  [ERROR] Python not found!
        echo  Please install Python from: https://www.python.org/downloads/
        echo  Make sure to check "Add Python to PATH" during install.
        echo.
        pause
        exit /b 1
    )
    set PYTHON=py
) else (
    set PYTHON=python
)

echo  Python OK:
%PYTHON% --version
echo.

cd /d "%~dp0"

if not exist "%~dp0server.py" (
    echo  [ERROR] server.py not found!
    echo  Please make sure server.py is in the same folder as this bat file.
    echo.
    pause
    exit /b 1
)

echo  Installing dependencies...
%PYTHON% -m pip install yfinance flask flask-cors -q --disable-pip-version-check
echo  Done.
echo.

start "" /b cmd /c "timeout /t 2 >nul && start http://localhost:5555"

echo  ------------------------------------------------
echo   Server starting...
echo   Open browser: http://localhost:5555
echo   Close this window to stop the server.
echo  ------------------------------------------------
echo.

%PYTHON% server.py

echo.
echo  Server stopped.
pause
