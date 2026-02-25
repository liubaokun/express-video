package com.express.video.model

data class AppConfig(
    val saveMode: SaveMode = SaveMode.LOCAL,
    val serverAddress: String = "",
    val serverPort: Int = 8080,
    val videoResolution: VideoResolution = VideoResolution.RESOLUTION_1080P,
    val videoBitrate: Int = 8,
    val cameraSettings: CameraSettings = CameraSettings(),
    val maxRecordDuration: Int = 0
)

enum class SaveMode {
    LOCAL,
    NETWORK
}

enum class VideoResolution(val width: Int, val height: Int, val label: String) {
    RESOLUTION_720P(1280, 720, "720p"),
    RESOLUTION_1080P(1920, 1080, "1080p"),
    RESOLUTION_4K(3840, 2160, "4K")
}

data class CameraSettings(
    val exposureCompensation: Int = 0,
    val whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.AUTO,
    val whiteBalanceTemperature: Int = 5500,
    val focusMode: FocusMode = FocusMode.CONTINUOUS,
    val iso: Int = 0,
    val isIsoAuto: Boolean = true
)

enum class WhiteBalanceMode(val value: Int, val label: String) {
    AUTO(1, "自动"),
    INCANDESCENT(2, "白炽灯"),
    FLUORESCENT(3, "荧光灯"),
    DAYLIGHT(4, "日光"),
    CLOUDY(5, "阴天"),
    MANUAL(0, "手动")
}

enum class FocusMode(val value: Int, val label: String) {
    AUTO(0, "自动"),
    CONTINUOUS(1, "连续对焦"),
    MANUAL(2, "手动")
}
