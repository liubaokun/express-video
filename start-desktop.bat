@echo off
title Express Video Receiver

echo ====================================
echo    Express Video Receiver
echo ====================================
echo.

cd /d "%~dp0desktop-app"

rem Check Python
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python not found!
    echo Please install Python 3.8+ from: https://www.python.org/downloads/
    pause
    exit /b 1
)

echo [OK] Python found
python --version
echo.

rem Install dependencies
echo Installing dependencies...
pip install PyQt5 Flask qrcode[pil] psutil -q

echo.
echo Starting application...
echo.

python main.py

pause
