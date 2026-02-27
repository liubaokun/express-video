@echo off
title Express Video Receiver

echo ====================================
echo    Express Video Receiver
echo ====================================
echo.

cd /d "%~dp0"

rem Check Python installation
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python not found. Please install Python 3.8+
    echo.
    echo Download: https://www.python.org/downloads/
    echo.
    pause
    exit /b 1
)

echo [OK] Python found
python --version
echo.

rem Check dependencies
echo [CHECK] Checking dependencies...
python -c "import PyQt5" >nul 2>&1
if errorlevel 1 (
    echo [INSTALL] Installing PyQt5...
    pip install PyQt5 -q
)

python -c "import flask" >nul 2>&1
if errorlevel 1 (
    echo [INSTALL] Installing Flask...
    pip install flask -q
)

python -c "import qrcode" >nul 2>&1
if errorlevel 1 (
    echo [INSTALL] Installing qrcode...
    pip install qrcode[pil] -q
)

python -c "import psutil" >nul 2>&1
if errorlevel 1 (
    echo [INSTALL] Installing psutil...
    pip install psutil -q
)

echo [OK] All dependencies ready
echo.
echo ====================================
echo    Starting Express Video Receiver...
echo ====================================
echo.

rem Start application
python main.py

if errorlevel 1 (
    echo.
    echo [ERROR] Program encountered an error
    echo.
    pause
)
