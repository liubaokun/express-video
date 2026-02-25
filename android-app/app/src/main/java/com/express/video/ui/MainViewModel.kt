package com.express.video.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.express.video.model.AppConfig
import com.express.video.model.CameraSettings
import com.express.video.model.SaveMode
import com.express.video.network.FileUploader
import com.express.video.network.UploadResult
import com.express.video.repository.SettingsRepository
import com.express.video.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class MainUiState(
    val config: AppConfig = AppConfig(),
    val scannedBarcode: String = "",
    val isScanning: Boolean = true,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val uploadProgress: Int = 0,
    val isUploading: Boolean = false,
    val uploadStatus: String = "",
    val errorMessage: String? = null,
    val recordedFile: File? = null,
    val showSettings: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val videoRepository = VideoRepository(application)
    private val fileUploader = FileUploader()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            settingsRepository.configFlow.collect { config ->
                _uiState.update { it.copy(config = config) }
            }
        }
    }

    fun onBarcodeScanned(barcode: String) {
        _uiState.update {
            it.copy(
                scannedBarcode = barcode,
                isScanning = false
            )
        }
    }

    fun confirmAndStartRecording() {
        val state = _uiState.value
        if (state.scannedBarcode.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    isRecording = true,
                    isScanning = false,
                    errorMessage = null
                )
            }
        }
    }

    fun onRecordingStarted() {
        _uiState.update { it.copy(isRecording = true) }
    }

    fun onRecordingComplete(file: File?) {
        if (file != null && file.exists()) {
            val state = _uiState.value
            if (state.config.saveMode == SaveMode.NETWORK) {
                uploadFile(file, state.scannedBarcode)
            } else {
                saveToMediaStore(file)
            }
        } else {
            _uiState.update {
                it.copy(
                    errorMessage = "录制失败",
                    isRecording = false
                )
            }
        }
    }

    fun onRecordingError(message: String) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                isRecording = false
            )
        }
    }

    private fun saveToMediaStore(file: File) {
        viewModelScope.launch {
            val success = videoRepository.saveToMediaStore(_uiState.value.scannedBarcode, file)
            if (success) {
                videoRepository.deleteLocalFile(file)
                _uiState.update {
                    it.copy(
                        isRecording = false,
                        uploadStatus = "已保存到本地",
                        scannedBarcode = "",
                        isScanning = true
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        errorMessage = "保存失败",
                        isRecording = false
                    )
                }
            }
        }
    }

    private fun uploadFile(file: File, trackingNumber: String) {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                isUploading = true,
                uploadProgress = 0,
                uploadStatus = "正在上传..."
            )
        }

        fileUploader.uploadFile(
            serverAddress = state.config.serverAddress,
            serverPort = state.config.serverPort,
            file = file,
            trackingNumber = trackingNumber
        ) { result ->
            when (result) {
                is UploadResult.Progress -> {
                    _uiState.update { it.copy(uploadProgress = result.percent) }
                }
                is UploadResult.Success -> {
                    viewModelScope.launch {
                        videoRepository.deleteLocalFile(file)
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                isRecording = false,
                                uploadProgress = 100,
                                uploadStatus = "上传成功",
                                scannedBarcode = "",
                                isScanning = true
                            )
                        }
                    }
                }
                is UploadResult.Error -> {
                    viewModelScope.launch {
                        saveToMediaStore(file)
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                uploadStatus = "上传失败，已保存到本地: ${result.message}"
                            )
                        }
                    }
                }
            }
        }
    }

    fun cancelScanning() {
        _uiState.update {
            it.copy(
                scannedBarcode = "",
                isScanning = true
            )
        }
    }

    fun resetForNewScan() {
        _uiState.update {
            MainUiState(config = it.config)
        }
    }

    fun updateConfig(config: AppConfig) {
        viewModelScope.launch {
            settingsRepository.updateConfig(config)
        }
    }

    fun updateSaveMode(mode: SaveMode) {
        viewModelScope.launch {
            settingsRepository.updateSaveMode(mode)
        }
    }

    fun updateServerConfig(address: String, port: Int) {
        viewModelScope.launch {
            settingsRepository.updateServerConfig(address, port)
        }
    }

    fun updateCameraSettings(settings: CameraSettings) {
        viewModelScope.launch {
            settingsRepository.updateCameraSettings(settings)
        }
    }

    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
