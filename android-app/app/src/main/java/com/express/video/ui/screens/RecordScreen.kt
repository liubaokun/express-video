package com.express.video.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.express.video.camera.CameraManager
import com.express.video.model.CameraSettings
import com.express.video.model.FocusMode
import com.express.video.model.VideoResolution
import com.express.video.model.WhiteBalanceMode
import com.express.video.repository.VideoRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ParamPanel {
    NONE, WB, EV, ISO, AF
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onStop: () -> Unit,
    onCameraSettingsChange: (CameraSettings) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

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

    var currentSettings by remember { mutableStateOf(cameraSettings) }
    var activePanel by remember { mutableStateOf(ParamPanel.NONE) }

    var focusBoxPosition by remember { mutableStateOf<Offset?>(null) }
    var focusBoxAlpha by remember { mutableFloatStateOf(0f) }

    var zoomRange by remember { mutableStateOf(android.util.Range(1.0f, 1.0f)) }
    var debounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(Unit) {
        val manager = CameraManager(context, lifecycleOwner, previewView)
        manager.initialize {
            manager.applySettings(currentSettings)
            zoomRange = manager.getZoomRange()
            isReady = true
        }
        manager.onRecordingComplete = { file ->
            isRecording = false
            if (file != null) onRecordingComplete()
        }
        manager.onRecordingError = { error ->
            isRecording = false
            onRecordingError(error)
        }
        cameraManager = manager

        val started = manager.startRecording(
            trackingNumber = trackingNumber,
            videoRepository = VideoRepository(context),
            resolution = videoResolution,
            bitrateMbps = videoBitrate
        )
        if (started) isRecording = true
    }

    LaunchedEffect(isRecording) {
        while (isRecording && !isPaused) {
            delay(1000)
            recordingTime += 1
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            debounceJob?.cancel()
            cameraManager?.release()
        }
    }

    val animatedFocusAlpha by animateFloatAsState(targetValue = focusBoxAlpha, label = "focus")

    fun saveSettings(settings: CameraSettings) {
        currentSettings = settings
        cameraManager?.applySettings(settings)
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(500)
            onCameraSettingsChange(settings)
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
                            saveSettings(currentSettings.copy(zoomRatio = newZoom))
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            focusBoxPosition = offset
                            focusBoxAlpha = 1f
                            cameraManager?.tapToFocus(
                                offset.x,
                                offset.y,
                                size.width,
                                size.height
                            )
                            scope.launch {
                                delay(1000)
                                focusBoxAlpha = 0f
                            }
                            if (currentSettings.focusMode == FocusMode.AUTO) {
                                scope.launch {
                                    delay(2000)
                                }
                            }
                        }
                    }
            )
        }

        focusBoxPosition?.let { pos ->
            Box(
                modifier = Modifier
                    .offset(x = (pos.x - 40).dp, y = (pos.y - 40).dp)
                    .size(80.dp)
                    .border(2.dp, Color.Yellow, RoundedCornerShape(8.dp))
                    .alpha(animatedFocusAlpha)
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

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ParamButton("WB", activePanel == ParamPanel.WB) { activePanel = if (activePanel == ParamPanel.WB) ParamPanel.NONE else ParamPanel.WB }
            ParamButton("EV", activePanel == ParamPanel.EV) { activePanel = if (activePanel == ParamPanel.EV) ParamPanel.NONE else ParamPanel.EV }
            ParamButton("ISO", activePanel == ParamPanel.ISO) { activePanel = if (activePanel == ParamPanel.ISO) ParamPanel.NONE else ParamPanel.ISO }
            ParamButton("AF", activePanel == ParamPanel.AF) { activePanel = if (activePanel == ParamPanel.AF) ParamPanel.NONE else ParamPanel.AF }
        }

        AnimatedVisibility(
            visible = activePanel != ParamPanel.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 80.dp)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                when (activePanel) {
                    ParamPanel.WB -> WBPanel(currentSettings) { saveSettings(it) }
                    ParamPanel.EV -> EVPanel(currentSettings) { saveSettings(it) }
                    ParamPanel.ISO -> ISOPanel(currentSettings) { saveSettings(it) }
                    ParamPanel.AF -> AFPanel(currentSettings) { saveSettings(it) }
                    else -> {}
                }
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
                            .background(Color.White.copy(alpha = 0.3f), CircleShape)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
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
                Text("已暂停", style = MaterialTheme.typography.bodyMedium, color = Color.Yellow)
            }
        }
    }
}

@Composable
private fun ParamButton(label: String, isActive: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(
                if (isActive) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                CircleShape
            )
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun WBPanel(settings: CameraSettings, onChange: (CameraSettings) -> Unit) {
    Column {
        Text("白平衡", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { onChange(settings.copy(whiteBalanceMode = WhiteBalanceMode.AUTO)) },
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (settings.whiteBalanceMode == WhiteBalanceMode.AUTO) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Text("自动", color = Color.White)
            }
            TextButton(
                onClick = { onChange(settings.copy(whiteBalanceMode = WhiteBalanceMode.MANUAL)) },
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (settings.whiteBalanceMode == WhiteBalanceMode.MANUAL) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Text("手动", color = Color.White)
            }
        }
        if (settings.whiteBalanceMode == WhiteBalanceMode.MANUAL) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("${settings.whiteBalanceTemperature}K", color = Color.White)
            Slider(
                value = settings.whiteBalanceTemperature.toFloat(),
                onValueChange = { onChange(settings.copy(whiteBalanceTemperature = it.toInt())) },
                valueRange = 2000f..8000f,
                steps = 59
            )
        }
    }
}

@Composable
private fun EVPanel(settings: CameraSettings, onChange: (CameraSettings) -> Unit) {
    Column {
        Text("曝光补偿: ${settings.exposureCompensation} EV", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = settings.exposureCompensation.toFloat(),
            onValueChange = { onChange(settings.copy(exposureCompensation = it.toInt())) },
            valueRange = -12f..12f,
            steps = 23
        )
    }
}

@Composable
private fun ISOPanel(settings: CameraSettings, onChange: (CameraSettings) -> Unit) {
    val isoValues = listOf(100, 200, 400, 800, 1600, 3200)
    Column {
        Text("ISO", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { onChange(settings.copy(isIsoAuto = true)) },
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (settings.isIsoAuto) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Text("自动", color = Color.White)
            }
            TextButton(
                onClick = { onChange(settings.copy(isIsoAuto = false)) },
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (!settings.isIsoAuto) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Text("手动", color = Color.White)
            }
        }
        if (!settings.isIsoAuto) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(isoValues) { iso ->
                    FilterChip(
                        selected = settings.iso == iso,
                        onClick = { onChange(settings.copy(iso = iso)) },
                        label = { Text(iso.toString()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AFPanel(settings: CameraSettings, onChange: (CameraSettings) -> Unit) {
    Column {
        Text("对焦模式", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FocusMode.entries.forEach { mode ->
                TextButton(
                    onClick = { onChange(settings.copy(focusMode = mode)) },
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (settings.focusMode == mode) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Text(mode.label, color = Color.White)
                }
            }
        }
    }
}

private fun formatRecordingTime(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}