@echo off
chcp 65001 >nul
echo ====================================
echo   快速构建 APK
echo ====================================
echo.

cd /d "%~dp0android-app"

echo 正在构建 APK...
echo.

set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
call gradlew.bat assembleDebug --no-daemon

if %errorlevel% equ 0 (
    echo.
    echo ====================================
    echo   构建成功!
    echo ====================================
    echo.
    echo APK 位置:
    echo %~dp0android-app\app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo 正在打开文件夹...
    explorer "%~dp0android-app\app\build\outputs\apk\debug"
) else (
    echo.
    echo 构建失败，请检查错误信息
)

echo.
pause
