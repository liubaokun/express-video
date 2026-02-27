package com.express.video.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.express.video.model.AppConfig
import com.express.video.model.CameraSettings
import com.express.video.model.SaveMode
import com.express.video.model.WhiteBalanceMode
import com.express.video.network.FileUploader
import com.express.video.network.UploadResult
import com.express.video.repository.SettingsRepository
import com.express.video.repository.VideoRepository
import kotlinx.coroutines.delay
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
    val showSettings: Boolean = false,
    val showSaveDialog: Boolean = false,
    val recordingCount: Int = 0,
    val showSaveSuccess: Boolean = false,
    val savedFileName: String = ""
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

    fun checkStoragePermission(): Boolean {
        val context = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (!granted) {
            _uiState.update { it.copy(errorMessage = "需要存储权限才能保存视频") }
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
        if (file != null && file.exists() && file.length() > 0) {
            _uiState.update {
                it.copy(
                    recordedFile = file,
                    showSaveDialog = true
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    errorMessage = if (file == null) "录制失败：文件为空" else "录制失败：文件不存在或大小为0",
                    isRecording = false
                )
            }
        }
    }

    fun saveRecording() {
        val state = _uiState.value
        val file = state.recordedFile ?: return

        if (state.config.saveMode == SaveMode.NETWORK) {
            uploadFile(file, state.scannedBarcode)
        } else {
            saveToMediaStore(file)
        }
    }

    private fun saveToMediaStore(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(showSaveDialog = false, isUploading = true, uploadStatus = "正在保存...") }
            
            val fileName = videoRepository.getFileNameWithTimestamp(_uiState.value.scannedBarcode)
            val success = videoRepository.saveToMediaStore(_uiState.value.scannedBarcode, file)
            if (success) {
                videoRepository.deleteLocalFile(file)
                val newCount = _uiState.value.recordingCount + 1
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        isRecording = false,
                        showSaveSuccess = true,
                        savedFileName = fileName,
                        recordingCount = newCount
                    )
                }
                
                viewModelScope.launch {
                    delay(2000)
                    resetForNewScan()
                }
            } else {
                _uiState.update {
                    it.copy(
                        errorMessage = "保存失败",
                        isRecording = false,
                        isUploading = false,
                        showSaveDialog = false,
                        recordedFile = null
                    )
                }
            }
        }
    }

    private fun uploadFile(file: File, trackingNumber: String) {
        val state = _uiState.value
        
        if (state.config.serverAddress.isBlank()) {
            _uiState.update {
                it.copy(
                    showSaveDialog = false,
                    isUploading = true,
                    uploadProgress = 0,
                    uploadStatus = "服务器地址未配置，正在保存到本地..."
                )
            }
            saveToMediaStore(file)
            return
        }

        _uiState.update {
            it.copy(
                showSaveDialog = false,
                isUploading = true,
                uploadProgress = 0,
                uploadStatus = "正在连接服务器 ${state.config.serverAddress}:${state.config.serverPort}..."
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
                    _uiState.update { 
                        it.copy(
                            uploadProgress = result.percent,
                            uploadStatus = when (result.percent) {
                                0 -> "正在上传..."
                                100 -> "上传完成"
                                else -> "上传中 ${result.percent}%"
                            }
                        ) 
                    }
                }
                is UploadResult.Success -> {
                    viewModelScope.launch {
                        videoRepository.deleteLocalFile(file)
                        val newCount = _uiState.value.recordingCount + 1
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                isRecording = false,
                                uploadProgress = 100,
                                uploadStatus = "上传成功",
                                showSaveSuccess = true,
                                savedFileName = videoRepository.getFileNameWithTimestamp(trackingNumber),
                                recordingCount = newCount
                            )
                        }
                        // 停留 2 秒显示成功对话框
                        delay(2000)
                        resetForNewScan()
                    }
                }
                is UploadResult.Error -> {
                    viewModelScope.launch {
                        _uiState.update {
                            it.copy(
                                isUploading = true,
                                uploadStatus = "上传失败: ${result.message}，正在保存到本地..."
                            )
                        }
                        saveToMediaStore(file)
                        _uiState.update {
                            it.copy(
                                uploadStatus = "已保存到本地 (上传失败: ${result.message})"
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
        val currentCount = _uiState.value.recordingCount
        _uiState.update {
            MainUiState(config = it.config, recordingCount = currentCount)
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
        // 独即更新 UI 状态，同时切换到网络上传模式
        _uiState.update { current ->
            current.copy(
                config = current.config.copy(
                    serverAddress = address,
                    serverPort = port,
                    saveMode = SaveMode.NETWORK
                )
            )
        }
        
        // 异步持久化
        viewModelScope.launch {
            settingsRepository.updateServerConfig(address, port)
            settingsRepository.updateSaveMode(SaveMode.NETWORK)
        }
    }
        
        // 异步持久化
        viewModelScope.launch {
            settingsRepository.updateServerConfig(address, port)
        }
    }

    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun updateWhiteBalance(mode: WhiteBalanceMode) {
        viewModelScope.launch {
            val currentSettings = _uiState.value.config.cameraSettings
            settingsRepository.updateConfig(
                _uiState.value.config.copy(
                    cameraSettings = currentSettings.copy(whiteBalanceMode = mode)
                )
            )
        }
    }

    fun updateColorTemperature(colorTemp: Int) {
        viewModelScope.launch {
            val currentSettings = _uiState.value.config.cameraSettings
            val mode = WhiteBalanceMode.fromColorTemp(colorTemp)
            settingsRepository.updateConfig(
                _uiState.value.config.copy(
                    cameraSettings = currentSettings.copy(
                        colorTemperature = colorTemp,
                        whiteBalanceMode = mode
                    )
                )
            )
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
}