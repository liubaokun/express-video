package com.express.video.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.express.video.camera.CameraManager
import com.express.video.model.CameraSettings
import com.express.video.model.VideoResolution
import com.express.video.repository.VideoRepository
import kotlinx.coroutines.delay

@Composable
fun RecordScreen(
    trackingNumber: String,
    cameraSettings: CameraSettings,
    videoResolution: VideoResolution,
    videoBitrate: Int,
    isPaused: Boolean,
    isUploading: Boolean,
    uploadProgress: Int,
    uploadStatus: String,
    onRecordingComplete: () -> Unit,
    onRecordingError: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cameraManager) {
        if (cameraManager == null) {
            val manager = CameraManager(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = PreviewView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            )
            manager.initialize {
                manager.applySettings(cameraSettings)
                previewView = manager.previewViewInstance
                val started = manager.startRecording(
                    trackingNumber = trackingNumber,
                    videoRepository = VideoRepository(context),
                    resolution = videoResolution,
                    bitrateMbps = videoBitrate
                )
                if (started) {
                    isRecording = true
                    onRecordingComplete
                }
            }
            manager.onRecordingComplete = { file ->
                isRecording = false
                if (file != null) {
                    onRecordingComplete()
                }
            }
            manager.onRecordingError = { error ->
                isRecording = false
                onRecordingError(error)
            }
            cameraManager = manager
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000)
            recordingTime += 1
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager?.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        previewView?.let { view ->
            AndroidView(
                factory = { view },
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "单号: $trackingNumber",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Text(
                    text = formatRecordingTime(recordingTime),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isPaused) Color.Yellow else Color.Red
                )
            }
        }

        if (isUploading) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.large
                    )
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
                    Text(
                        text = uploadStatus,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Text(
                        text = "$uploadProgress%",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
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
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRecording && !isUploading) {
                    IconButton(
                        onClick = {
                            if (isPaused) {
                                cameraManager?.resumeRecording()
                                onResume()
                            } else {
                                cameraManager?.pauseRecording()
                                onPause()
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "继续" else "暂停",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    ),
                    enabled = isRecording && !isUploading
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "停止录制",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            if (isPaused) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "已暂停",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Yellow
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
