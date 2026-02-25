package com.express.video.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.express.video.model.AppConfig
import com.express.video.model.CameraSettings
import com.express.video.model.FocusMode
import com.express.video.model.SaveMode
import com.express.video.model.VideoResolution
import com.express.video.model.WhiteBalanceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_config")

class SettingsRepository(private val context: Context) {

    companion object {
        private val SAVE_MODE = intPreferencesKey("save_mode")
        private val SERVER_ADDRESS = stringPreferencesKey("server_address")
        private val SERVER_PORT = intPreferencesKey("server_port")
        private val VIDEO_RESOLUTION = intPreferencesKey("video_resolution")
        private val VIDEO_BITRATE = intPreferencesKey("video_bitrate")
        private val EXPOSURE_COMPENSATION = intPreferencesKey("exposure_compensation")
        private val WHITE_BALANCE_MODE = intPreferencesKey("white_balance_mode")
        private val WHITE_BALANCE_TEMPERATURE = intPreferencesKey("white_balance_temperature")
        private val FOCUS_MODE = intPreferencesKey("focus_mode")
        private val ISO = intPreferencesKey("iso")
        private val IS_ISO_AUTO = booleanPreferencesKey("is_iso_auto")
        private val ZOOM_RATIO = floatPreferencesKey("zoom_ratio")
        private val MAX_RECORD_DURATION = intPreferencesKey("max_record_duration")
    }

    val configFlow: Flow<AppConfig> = context.dataStore.data.map { prefs ->
        AppConfig(
            saveMode = SaveMode.entries.getOrNull(prefs[SAVE_MODE] ?: 0) ?: SaveMode.LOCAL,
            serverAddress = prefs[SERVER_ADDRESS] ?: "",
            serverPort = prefs[SERVER_PORT] ?: 8080,
            videoResolution = VideoResolution.entries.getOrNull(prefs[VIDEO_RESOLUTION] ?: 1)
                ?: VideoResolution.RESOLUTION_1080P,
            videoBitrate = prefs[VIDEO_BITRATE] ?: 8,
            cameraSettings = CameraSettings(
                exposureCompensation = prefs[EXPOSURE_COMPENSATION] ?: 0,
                whiteBalanceMode = WhiteBalanceMode.entries.getOrNull(
                    prefs[WHITE_BALANCE_MODE] ?: 0
                ) ?: WhiteBalanceMode.AUTO,
                whiteBalanceTemperature = prefs[WHITE_BALANCE_TEMPERATURE] ?: 5500,
                focusMode = FocusMode.entries.getOrNull(prefs[FOCUS_MODE] ?: 0)
                    ?: FocusMode.AUTO,
                iso = prefs[ISO] ?: 0,
                isIsoAuto = prefs[IS_ISO_AUTO] ?: true,
                zoomRatio = prefs[ZOOM_RATIO] ?: 1.0f
            ),
            maxRecordDuration = prefs[MAX_RECORD_DURATION] ?: 0
        )
    }

    suspend fun updateConfig(config: AppConfig) {
        context.dataStore.edit { prefs ->
            prefs[SAVE_MODE] = config.saveMode.ordinal
            prefs[SERVER_ADDRESS] = config.serverAddress
            prefs[SERVER_PORT] = config.serverPort
            prefs[VIDEO_RESOLUTION] = config.videoResolution.ordinal
            prefs[VIDEO_BITRATE] = config.videoBitrate
            prefs[EXPOSURE_COMPENSATION] = config.cameraSettings.exposureCompensation
            prefs[WHITE_BALANCE_MODE] = config.cameraSettings.whiteBalanceMode.ordinal
            prefs[WHITE_BALANCE_TEMPERATURE] = config.cameraSettings.whiteBalanceTemperature
            prefs[FOCUS_MODE] = config.cameraSettings.focusMode.ordinal
            prefs[ISO] = config.cameraSettings.iso
            prefs[IS_ISO_AUTO] = config.cameraSettings.isIsoAuto
            prefs[ZOOM_RATIO] = config.cameraSettings.zoomRatio
            prefs[MAX_RECORD_DURATION] = config.maxRecordDuration
        }
    }

    suspend fun updateSaveMode(mode: SaveMode) {
        context.dataStore.edit { prefs ->
            prefs[SAVE_MODE] = mode.ordinal
        }
    }

    suspend fun updateServerConfig(address: String, port: Int) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_ADDRESS] = address
            prefs[SERVER_PORT] = port
        }
    }

    suspend fun updateCameraSettings(settings: CameraSettings) {
        context.dataStore.edit { prefs ->
            prefs[EXPOSURE_COMPENSATION] = settings.exposureCompensation
            prefs[WHITE_BALANCE_MODE] = settings.whiteBalanceMode.ordinal
            prefs[WHITE_BALANCE_TEMPERATURE] = settings.whiteBalanceTemperature
            prefs[FOCUS_MODE] = settings.focusMode.ordinal
            prefs[ISO] = settings.iso
            prefs[IS_ISO_AUTO] = settings.isIsoAuto
            prefs[ZOOM_RATIO] = settings.zoomRatio
        }
    }
}