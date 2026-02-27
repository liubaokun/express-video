@echo off
chcp 65001 >nul
title 快递视频接收器

echo ====================================
echo    快递视频接收器
echo ====================================
echo.

rem 确保进入 desktop-app 目录
if exist "%~dp0desktop-app" (
    cd /d "%~dp0desktop-app"
) else if exist "%~dp0main.py" (
    cd /d "%~dp0"
) else (
    echo [错误] 找不到 desktop-app 目录或 main.py 文件
    pause
    exit /b 1
)

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
python -m pip install PyQt5 Flask qrcode[pil] psutil -q

echo [完成] 所有依赖已就绪
echo.
echo ====================================
echo    正在启动快递视频接收器...
echo ====================================
echo.

rem 启动应用
if exist main.py (
    echo [提示] 正在启动 main.py...
    python main.py
) else (
    echo [错误] 未找到 main.py，请确保桌面端代码在 desktop-app 文件夹中
    pause
)

if errorlevel 1 (
    echo.
    echo [错误] 程序异常退出 (错误代码: %errorlevel%)
    echo [提示] 如果看到 "No module named xxx"，请尝试手动运行: python -m pip install xxx
    echo.
    pause
)
