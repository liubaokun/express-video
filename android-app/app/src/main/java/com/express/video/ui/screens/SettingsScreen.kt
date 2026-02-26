package com.express.video.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.express.video.model.AppConfig
import com.express.video.model.CameraSettings
import com.express.video.model.SaveMode
import com.express.video.model.VideoResolution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    onBack: () -> Unit
) {
    var saveMode by remember { mutableStateOf(config.saveMode) }
    var serverAddress by remember { mutableStateOf(config.serverAddress) }
    var serverPort by remember { mutableStateOf(config.serverPort.toString()) }
    var videoResolution by remember { mutableStateOf(config.videoResolution) }
    var videoBitrate by remember { mutableIntStateOf(config.videoBitrate) }
    var maxRecordDuration by remember { mutableIntStateOf(config.maxRecordDuration) }

    val bitrateFloat = remember { mutableFloatStateOf(videoBitrate.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val newConfig = AppConfig(
                            saveMode = saveMode,
                            serverAddress = serverAddress,
                            serverPort = serverPort.toIntOrNull() ?: 8080,
                            videoResolution = videoResolution,
                            videoBitrate = videoBitrate,
                            cameraSettings = CameraSettings(),
                            maxRecordDuration = maxRecordDuration
                        )
                        onSave(newConfig)
                        onBack()
                    }) {
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "保存模式",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    selectGroup(
                        options = SaveMode.entries,
                        selected = saveMode,
                        label = { mode ->
                            when (mode) {
                                SaveMode.LOCAL -> "本地保存"
                                SaveMode.NETWORK -> "局域网传输"
                            }
                        },
                        onSelect = { saveMode = it }
                    )

                    if (saveMode == SaveMode.NETWORK) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = serverAddress,
                            onValueChange = { serverAddress = it },
                            label = { Text("服务器地址") },
                            placeholder = { Text("例如: 192.168.1.100") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = serverPort,
                            onValueChange = { serverPort = it },
                            label = { Text("端口") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "视频设置",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "分辨率",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    selectGroup(
                        options = VideoResolution.entries,
                        selected = videoResolution,
                        label = { it.label },
                        onSelect = { videoResolution = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "码率: ${videoBitrate} Mbps",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = bitrateFloat.floatValue,
                        onValueChange = {
                            bitrateFloat.floatValue = it
                            videoBitrate = it.toInt()
                        },
                        valueRange = 1f..20f,
                        steps = 18,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = if (maxRecordDuration == 0) "" else maxRecordDuration.toString(),
                        onValueChange = { maxRecordDuration = it.toIntOrNull() ?: 0 },
                        label = { Text("最大录制时长(秒，0=无限制)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun <T> selectGroup(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(
        modifier = Modifier.selectableGroup()
    ) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selected,
                        onClick = { onSelect(option) }
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = option == selected,
                    onClick = null
                )
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}