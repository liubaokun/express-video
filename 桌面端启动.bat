@echo off
chcp 65001 >nul
title 快递视频接收器

echo ====================================
echo    快递视频接收器
echo ====================================
echo.

cd /d "%~dp0"

rem 检查 Python 是否安装
python --version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Python，请先安装 Python 3.8+
    echo.
    echo 下载地址：https://www.python.org/downloads/
    echo.
    pause
    exit /b 1
)

echo [检查] Python 已安装
python --version
echo.

rem 检查依赖是否安装
echo [检查] 检查依赖...
python -c "import PyQt5" >nul 2>&1
if errorlevel 1 (
    echo [安装] 正在安装 PyQt5...
    pip install PyQt5 -q
)

python -c "import flask" >nul 2>&1
if errorlevel 1 (
    echo [安装] 正在安装 Flask...
    pip install flask -q
)

python -c "import qrcode" >nul 2>&1
if errorlevel 1 (
    echo [安装] 正在安装 qrcode...
    pip install qrcode[pil] -q
)

python -c "import psutil" >nul 2>&1
if errorlevel 1 (
    echo [安装] 正在安装 psutil...
    pip install psutil -q
)

echo [完成] 所有依赖已就绪
echo.
echo ====================================
echo    正在启动快递视频接收器...
echo ====================================
echo.

rem 启动应用
python main.py

if errorlevel 1 (
    echo.
    echo [错误] 程序运行出错，请检查错误信息
    echo.
    pause
)
