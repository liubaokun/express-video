package com.express.video.model

import android.hardware.camera2.CaptureRequest

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

enum class WhiteBalanceMode(val mode: Int, val label: String, val colorTemp: Int) {
    AUTO(CaptureRequest.CONTROL_AWB_MODE_AUTO, "Auto", 0),
    INCANDESCENT(CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT, "Incandescent", 2700),
    FLUORESCENT(CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT, "Fluorescent", 4000),
    WARM_FLUORESCENT(CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT, "Warm Fluorescent", 3000),
    DAYLIGHT(CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT, "Daylight", 5500),
    CLOUDY(CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, "Cloudy", 6500),
    TWILIGHT(CaptureRequest.CONTROL_AWB_MODE_TWILIGHT, "Twilight", 7500),
    SHADE(CaptureRequest.CONTROL_AWB_MODE_SHADE, "Shade", 8000);

    companion object {
        fun fromColorTemp(colorTemp: Int): WhiteBalanceMode {
            if (colorTemp == 0) return AUTO
            return entries
                .filter { it != AUTO }
                .minByOrNull { kotlin.math.abs(it.colorTemp - colorTemp) }
                ?: DAYLIGHT
        }
    }
}

data class CameraSettings(
    val zoomRatio: Float = 1.0f,
    val whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.AUTO,
    val colorTemperature: Int = 0
)