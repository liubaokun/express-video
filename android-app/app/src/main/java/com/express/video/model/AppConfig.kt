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
    val zoomRatio: Float = 1.0f
)