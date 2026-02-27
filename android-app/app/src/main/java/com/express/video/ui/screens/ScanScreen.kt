package com.express.video.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.express.video.camera.BarcodeAnalyzer
import com.express.video.model.AppConfig
import com.express.video.model.CameraSettings

enum class ScanMode {
    BARCODE,
    SERVER_CONFIG
}

@Composable
fun ScanScreen(
    config: AppConfig,
    onBarcodeDetected: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    cameraSettings: CameraSettings = CameraSettings(),
    scanMode: ScanMode = ScanMode.BARCODE,
    onServerConfigScanned: ((String, Int) -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasRecordAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var isCameraReady by remember { mutableStateOf(false) }
    var isCameraBound by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: false
        hasRecordAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: false
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || (scanMode == ScanMode.BARCODE && !hasRecordAudioPermission)) {
            val permissions = if (scanMode == ScanMode.BARCODE) {
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            } else {
                arrayOf(Manifest.permission.CAMERA)
            }
            permissionLauncher.launch(permissions)
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            try {
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    try {
                        cameraProvider = providerFuture.get()
                        isCameraReady = true
                    } catch (e: Exception) {
                        Log.e("ScanScreen", "Failed to get camera provider", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                Log.e("ScanScreen", "Failed to initialize camera", e)
            }
        }
    }

    DisposableEffect(cameraProvider) {
        onDispose {
            cameraProvider?.unbindAll()
            isCameraBound = false
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var scanSuccess by remember { mutableStateOf<String?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (hasCameraPermission && isCameraReady && cameraProvider != null) {
            AndroidView(
                factory = { ctx ->
                    previewView.apply {
                        post {
                            try {
                                val provider = cameraProvider ?: return@post
                                if (isCameraBound) return@post
                                
                                provider.unbindAll()
                                
                                val preview = Preview.Builder()
                                    .build()
                                    .also {
                                        it.setSurfaceProvider(surfaceProvider)
                                    }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also {
                                        it.setAnalyzer(
                                            ContextCompat.getMainExecutor(context),
                                            BarcodeAnalyzer { barcode ->
                                                Log.d("ScanScreen", "Barcode detected: $barcode, mode: $scanMode")
                                                if (scanMode == ScanMode.SERVER_CONFIG) {
                                                    val parts = barcode.split(":")
                                                    if (parts.size >= 2) {
                                                        val address = parts[0]
                                                        val port = parts.last().toIntOrNull() ?: 8080
                                                        Log.d("ScanScreen", "Server config scanned: $address:$port")
                                                        scanSuccess = "$address:$port"
                                                        showSuccessDialog = true
                                                        onServerConfigScanned?.invoke(address, port)
                                                    } else {
                                                        Log.w("ScanScreen", "Invalid server QR format: $barcode")
                                                    }
                                                } else {
                                                    onBarcodeDetected(barcode)
                                                }
                                            }
                                        )
                                    }

                                val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                                
                                isCameraBound = true
                            } catch (e: Exception) {
                                Log.e("ScanScreen", "Failed to bind camera", e)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(280.dp, 180.dp)
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
            )

            if (showSuccessDialog) {
                AlertDialog(
                    onDismissRequest = { 
                        showSuccessDialog = false
                        scanSuccess = null
                        // 不在这里调用回调，只在确认按钮调用
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    title = { Text("扫描成功") },
                    text = {
                        Text("服务器配置：$scanSuccess")
                    },
                    confirmButton = {
                        Button(
                            onClick = { 
                                showSuccessDialog = false
                                val parts = scanSuccess?.split(":")
                                val address = parts?.get(0) ?: ""
                                val port = parts?.get(1)?.toIntOrNull() ?: 8080
                                onServerConfigScanned?.invoke(address, port)
                                scanSuccess = null
                            }
                        ) {
                            Text("确定")
                        }
                    }
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (scanMode == ScanMode.SERVER_CONFIG) {
                        "扫描服务器二维码"
                    } else {
                        "扫描快递条码"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("需要相机权限")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        val permissions = if (scanMode == ScanMode.BARCODE) {
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                        } else {
                            arrayOf(Manifest.permission.CAMERA)
                        }
                        permissionLauncher.launch(permissions)
                    }) {
                        Text("授权")
                    }
                }
            }
        }

        if (scanMode == ScanMode.SERVER_CONFIG && onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else {
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun BarcodeConfirmDialog(
    barcode: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("扫描成功") },
        text = {
            Column {
                Text("快递单号:")
                Text(
                    text = barcode,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("开始录制")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text("重新扫描")
            }
        }
    )
}