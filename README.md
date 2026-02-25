# 快递视频录制系统

## 项目结构

```
├── android-app/          # 安卓端APP
├── desktop-app/          # 电脑端接收服务
└── .github/workflows/    # GitHub Actions自动编译
```

## 快速开始

### 方式1：GitHub Actions 自动编译（无需安装Android Studio）

1. 将项目推送到GitHub
2. 进入仓库的 **Actions** 页面
3. 点击 **Build APK** 工作流
4. 等待编译完成（约5分钟）
5. 在 **Artifacts** 中下载 `app-debug.apk`
6. 安装到手机即可

### 方式2：本地编译

需要安装 Android Studio 或 Android SDK。

```bash
cd android-app
./gradlew assembleDebug
# APK位置: app/build/outputs/apk/debug/app-debug.apk
```

## 电脑端使用

```bash
cd desktop-app
pip install -r requirements.txt
python main.py
```

## 功能说明

### 安卓端
- 扫描快递条形码（Code128/Code39/EAN-13等）
- 录制视频并以快递单号命名
- 支持自定义摄像头参数（曝光/白平衡/对焦/ISO）
- 支持本地保存或局域网传输

### 电脑端
- 接收安卓端上传的视频
- 系统托盘运行
- 自定义保存路径和端口
