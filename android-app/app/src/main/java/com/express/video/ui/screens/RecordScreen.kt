package com.express.video.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.express.video.camera.CameraManager
import com.express.video.model.VideoResolution
import com.express.video.repository.VideoRepository
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordScreen(
    trackingNumber: String,
    videoResolution: VideoResolution,
    videoBitrate: Int,
    recordingCount: Int,
    isUploading: Boolean,
    uploadProgress: Int,
    uploadStatus: String,
    onRecordingComplete: (File?) -> Unit,
    onRecordingError: (String) -> Unit,
    onStop: () -> Unit,
    initialColorTemperature: Int = 5500,
    onColorTempChange: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAllPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
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

    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableLongStateOf(0L) }
    var isReady by remember { mutableStateOf(false) }
    var cameraInitialized by remember { mutableStateOf(false) }

    var zoomRange by remember { mutableStateOf(android.util.Range(1.0f, 10.0f)) }
    var currentTime by remember { mutableStateOf("") }
    var colorTemp by remember { mutableIntStateOf(initialColorTemperature) }
    var isAutoWhiteBalance by remember { mutableStateOf(initialColorTemperature == 0) }
    
    LaunchedEffect(isRecording) {
        while (isRecording) {
            currentTime = SimpleDateFormat("H时mm分ss秒", Locale.getDefault()).format(Date())
            delay(1000)
            recordingTime += 1
        }
    }

    LaunchedEffect(Unit) {
        if (!hasAllPermissions) {
            onRecordingError("Camera or audio permission required")
            return@LaunchedEffect
        }
        
        try {
            val manager = CameraManager(context, lifecycleOwner, previewView)
            manager.initialize {
                Log.d("RecordScreen", "Camera initialized successfully")
                zoomRange = manager.getZoomRange()
                if (initialColorTemperature == 0) {
                    manager.setWhiteBalance(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO)
                } else {
                    manager.setColorTemperature(initialColorTemperature)
                }
                isReady = true
                cameraInitialized = true
            }
            manager.onRecordingComplete = { file ->
                isRecording = false
                onRecordingComplete(file)
            }
            manager.onRecordingError = { error ->
                isRecording = false
                onRecordingError(error ?: "Unknown error")
            }
            cameraManager = manager
        } catch (e: Exception) {
            Log.e("RecordScreen", "Failed to initialize camera", e)
            onRecordingError("Camera initialization failed: ${e.message}")
        }
    }

    LaunchedEffect(colorTemp, isAutoWhiteBalance) {
        if (isAutoWhiteBalance) {
            cameraManager?.setWhiteBalance(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO)
            onColorTempChange(0)
        } else {
            cameraManager?.setColorTemperature(colorTemp)
            onColorTempChange(colorTemp)
        }
    }

    LaunchedEffect(cameraInitialized) {
        if (cameraInitialized && hasAllPermissions) {
            delay(500)
            val manager = cameraManager
            if (manager != null) {
                try {
                    val videoRepository = VideoRepository(context)
                    val started = manager.startRecording(
                        trackingNumber = trackingNumber,
                        videoRepository = videoRepository,
                        resolution = videoResolution,
                        bitrateMbps = videoBitrate
                    )
                    if (started) {
                        isRecording = true
                        currentTime = SimpleDateFormat("H时mm分ss秒", Locale.getDefault()).format(Date())
                        Log.d("RecordScreen", "Recording started for: $trackingNumber")
                    } else {
                        onRecordingError("Failed to start recording")
                    }
                } catch (e: Exception) {
                    Log.e("RecordScreen", "Failed to start recording", e)
                    onRecordingError("Recording start failed: ${e.message}")
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager?.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isReady) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val currentZoom = cameraManager?.getCurrentZoom() ?: 1.0f
                            val newZoom = (currentZoom * zoom).coerceIn(
                                zoomRange.lower,
                                zoomRange.upper
                            )
                            cameraManager?.setZoomRatio(newZoom)
                        }
                    }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "第 ${recordingCount + 1} 段视频",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "单号: $trackingNumber",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Text(
                    text = formatRecordingTime(recordingTime),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.Red
                )
            }
        }

        if (currentTime.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        if (isUploading) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.large)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        progress = uploadProgress / 100f,
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(uploadStatus, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    Text("$uploadProgress%", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val zoom = cameraManager?.getCurrentZoom() ?: 1.0f
                Text(
                    text = String.format("%.1fx", zoom),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "自动",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isAutoWhiteBalance) Color(0xFF4CAF50) else Color.White
                        )
                        Switch(
                            checked = isAutoWhiteBalance,
                            onCheckedChange = { isAutoWhiteBalance = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF4CAF50),
                                checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    
                    if (!isAutoWhiteBalance) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${colorTemp}K",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                            Slider(
                                value = colorTemp.toFloat(),
                                onValueChange = { 
                                    colorTemp = it.toInt()
                                },
                                valueRange = 2000f..10000f,
                                modifier = Modifier.width(120.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFF4CAF50),
                                    inactiveTrackColor = Color.Gray
                                )
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    cameraManager?.stopRecording()
                    isRecording = false
                    onStop()
                },
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                enabled = isRecording && !isUploading
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "停止录制",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

private fun formatRecordingTime(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}