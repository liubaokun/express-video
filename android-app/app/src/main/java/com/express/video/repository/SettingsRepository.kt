package com.express.video.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.express.video.model.AppConfig
import com.express.video.model.CameraSettings
import com.express.video.model.SaveMode
import com.express.video.model.VideoResolution
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
}