@echo off
chcp 65001 >nul
echo ====================================
echo   快递视频接收服务器 - 测试工具
echo ====================================
echo.
echo 正在检查服务器状态...
echo.

curl -s http://192.168.1.7:8080/ping >nul 2>&1
if %errorlevel% equ 0 (
    echo [成功] 服务器已启动
    echo.
    echo 服务器信息:
    curl -s http://192.168.1.7:8080/status
    echo.
    echo.
    echo 手机扫码配置：
    echo   服务器地址：192.168.1.7
    echo   端口：8080
    echo.
    echo 或者直接输入：192.168.1.7:8080
    echo.
) else (
    echo [失败] 服务器未启动
    echo.
    echo 请先运行桌面端程序：main.py
)

echo 按任意键退出...
pause >nul
