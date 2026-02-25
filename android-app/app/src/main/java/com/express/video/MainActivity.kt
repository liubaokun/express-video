package com.express.video

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.express.video.ui.MainViewModel
import com.express.video.ui.screens.BarcodeConfirmDialog
import com.express.video.ui.screens.RecordScreen
import com.express.video.ui.screens.ScanScreen
import com.express.video.ui.screens.SettingsScreen
import com.express.video.ui.theme.ExpressVideoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpressVideoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = viewModel()
                    val uiState by viewModel.uiState.collectAsState()

                    var showConfirmDialog by remember { mutableStateOf(false) }
                    var detectedBarcode by remember { mutableStateOf("") }

                    when {
                        uiState.showSettings -> {
                            SettingsScreen(
                                config = uiState.config,
                                onSave = { viewModel.updateConfig(it) },
                                onBack = { viewModel.showSettings(false) }
                            )
                        }
                        uiState.isRecording -> {
                            RecordScreen(
                                trackingNumber = uiState.scannedBarcode,
                                cameraSettings = uiState.config.cameraSettings,
                                videoResolution = uiState.config.videoResolution,
                                videoBitrate = uiState.config.videoBitrate,
                                isPaused = uiState.isPaused,
                                isUploading = uiState.isUploading,
                                uploadProgress = uiState.uploadProgress,
                                uploadStatus = uiState.uploadStatus,
                                onRecordingComplete = {
                                    viewModel.onRecordingComplete(it)
                                },
                                onRecordingError = { viewModel.onRecordingError(it) },
                                onPause = {},
                                onResume = {},
                                onStop = {}
                            )
                        }
                        else -> {
                            ScanScreen(
                                config = uiState.config,
                                onBarcodeDetected = { barcode ->
                                    detectedBarcode = barcode
                                    showConfirmDialog = true
                                },
                                onNavigateToSettings = { viewModel.showSettings(true) },
                                cameraSettings = uiState.config.cameraSettings
                            )
                        }
                    }

                    if (showConfirmDialog && detectedBarcode.isNotEmpty()) {
                        BarcodeConfirmDialog(
                            barcode = detectedBarcode,
                            onConfirm = {
                                showConfirmDialog = false
                                viewModel.onBarcodeScanned(detectedBarcode)
                                viewModel.confirmAndStartRecording()
                            },
                            onCancel = {
                                showConfirmDialog = false
                                detectedBarcode = ""
                            }
                        )
                    }
                }
            }
        }
    }
}
