@echo off
chcp 65001 >nul
echo ====================================
echo   上传功能测试工具
echo ====================================
echo.

:: 创建一个测试视频文件（小的空文件用于测试）
echo 创建测试文件...
if not exist "test_video.mp4" (
    echo 正在下载测试文件...
    curl -L "https://www.w3schools.com/html/mov_bbb.mp4" -o "test_video.mp4" 2>nul
)

if not exist "test_video.mp4" (
    echo 创建空测试文件...
    type nul > test_video.mp4
)

echo.
echo 测试上传到服务器...
echo.

curl -X POST http://192.168.1.7:8080/upload ^
  -F "trackingNumber=TEST123" ^
  -F "file=@test_video.mp4" ^
  -H "Content-Type: multipart/form-data"

echo.
echo.
if %errorlevel% equ 0 (
    echo [成功] 上传测试完成
) else (
    echo [失败] 上传失败
    echo.
    echo 可能原因:
    echo 1. 服务器未启动
    echo 2. 防火墙阻止连接
    echo 3. IP 地址或端口错误
)

echo.
echo 按任意键退出...
pause >nul
