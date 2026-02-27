package com.express.video

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.express.video.ui.MainViewModel
import com.express.video.ui.screens.BarcodeConfirmDialog
import com.express.video.ui.screens.RecordScreen
import com.express.video.ui.screens.ScanMode
import com.express.video.ui.screens.ScanScreen
import com.express.video.ui.screens.SettingsScreen
import com.express.video.ui.theme.ExpressVideoTheme
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
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
                    val context = LocalContext.current

                    var showConfirmDialog by remember { mutableStateOf(false) }
                    var detectedBarcode by remember { mutableStateOf("") }
                    var showServerConfigScan by remember { mutableStateOf(false) }

                    val storagePermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        if (granted) {
                            viewModel.saveRecording()
                        } else {
                            viewModel.onPermissionResult(false)
                        }
                    }

                    when {
                        showServerConfigScan -> {
                            ScanScreen(
                                config = uiState.config,
                                onBarcodeDetected = { },
                                onNavigateToSettings = { },
                                scanMode = ScanMode.SERVER_CONFIG,
                                onServerConfigScanned = { address, port ->
                                    showServerConfigScan = false
                                    viewModel.updateServerConfig(address, port)
                                    viewModel.showSettings(true)
                                    Toast.makeText(context, "服务器配置成功：$address:$port", Toast.LENGTH_LONG).show()
                                },
                                onBack = { 
                                    showServerConfigScan = false
                                }
                            )
                        }
                        uiState.showSettings -> {
                            SettingsScreen(
                                config = uiState.config,
                                onSave = { viewModel.updateConfig(it) },
                                onBack = { viewModel.showSettings(false) },
                                onScanServerQr = { showServerConfigScan = true }
                            )
                        }
                        uiState.isRecording -> {
                            RecordScreen(
                                trackingNumber = uiState.scannedBarcode,
                                videoResolution = uiState.config.videoResolution,
                                videoBitrate = uiState.config.videoBitrate,
                                recordingCount = uiState.recordingCount,
                                isUploading = uiState.isUploading,
                                uploadProgress = uiState.uploadProgress,
                                uploadStatus = uiState.uploadStatus,
                                onRecordingComplete = { file ->
                                    viewModel.onRecordingComplete(file)
                                },
                                onRecordingError = { viewModel.onRecordingError(it) },
                                onStop = {},
                                initialColorTemperature = uiState.config.cameraSettings.colorTemperature,
                                onColorTempChange = { viewModel.updateColorTemperature(it) }
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

                    if (uiState.showSaveDialog && uiState.recordedFile != null) {
                        SaveConfirmDialog(
                            fileName = uiState.savedFileName.ifEmpty { "${uiState.scannedBarcode}.mp4" },
                            fileSize = formatFileSize(uiState.recordedFile!!.length()),
                            onSave = {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        viewModel.saveRecording()
                                    } else {
                                        storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    }
                                } else {
                                    viewModel.saveRecording()
                                }
                            }
                        )
                    }

                    if (uiState.showSaveSuccess) {
                        SaveSuccessDialog(
                            fileName = uiState.savedFileName
                        )
                    }

                    uiState.errorMessage?.let { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }
}

@Composable
fun SaveConfirmDialog(
    fileName: String,
    fileSize: String,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = "Recording Complete",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "Video saved successfully",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "File:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Size:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = fileSize,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Save")
            }
        }
    )
}

@Composable
fun SaveSuccessDialog(
    fileName: String
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = "Save Success",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column {
                Text(
                    text = "Video saved successfully",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Auto close in 2 seconds...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { }
    )
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }
}