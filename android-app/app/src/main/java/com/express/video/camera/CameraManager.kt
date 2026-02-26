package com.express.video.camera

import android.content.Context
import android.util.Log
import android.util.Range
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.express.video.model.CameraSettings
import com.express.video.model.FocusMode
import com.express.video.model.VideoResolution
import com.express.video.model.WhiteBalanceMode
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    val previewView: PreviewView
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var _preview: Preview? = null
    
    private var currentRecordingFile: File? = null
    private var isInitialized = false

    var isRecording: Boolean = false
        private set

    var onRecordingComplete: ((File?) -> Unit)? = null
    var onRecordingError: ((String) -> Unit)? = null

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    fun initialize(onReady: (() -> Unit)? = null) {
        if (isInitialized) {
            Log.w("CameraManager", "Camera already initialized")
            onReady?.invoke()
            return
        }
        
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                if (cameraProvider == null) {
                    Log.e("CameraManager", "Camera provider is null")
                    onRecordingError?.invoke("摄像头初始化失败: 无法获取相机提供者")
                    return@addListener
                }
                setupCamera()
                isInitialized = true
                onReady?.invoke()
            } catch (e: Exception) {
                Log.e("CameraManager", "Camera initialization failed", e)
                onRecordingError?.invoke("摄像头初始化失败: ${e.message}")
            }
        }, mainExecutor)
    }

    private fun setupCamera() {
        val provider = cameraProvider ?: run {
            Log.e("CameraManager", "setupCamera: cameraProvider is null")
            return
        }
        
        try {
            provider.unbindAll()
            Log.d("CameraManager", "Unbound all use cases")
        } catch (e: Exception) {
            Log.w("CameraManager", "Failed to unbind all use cases", e)
        }

        _preview = Preview.Builder()
            .build()
            .also { preview ->
                preview.setSurfaceProvider(previewView.surfaceProvider)
            }
        Log.d("CameraManager", "Preview use case created")

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()

        videoCapture = VideoCapture.withOutput(recorder)
        Log.d("CameraManager", "VideoCapture use case created")

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                _preview,
                videoCapture
            )
            Log.d("CameraManager", "Camera bound to lifecycle successfully")
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to bind camera use cases", e)
            onRecordingError?.invoke("绑定摄像头失败: ${e.message}")
        }
    }

    fun getZoomRange(): Range<Float> {
        return camera?.cameraInfo?.zoomState?.value?.zoomRatio?.let { Range(1.0f, it) } ?: Range(1.0f, 10.0f)
    }

    fun getCurrentZoom(): Float {
        return camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1.0f
    }

    fun setZoomRatio(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun tapToFocus(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
        val cam = camera ?: return
        val factory = SurfaceOrientedMeteringPointFactory(viewWidth.toFloat(), viewHeight.toFloat())
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(2, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    fun applySettings(settings: CameraSettings) {
        val cam = camera ?: return
        val cameraControl = cam.cameraControl

        try {
            val exposureRange = cam.cameraInfo.exposureState.exposureCompensationRange
            if (exposureRange.contains(settings.exposureCompensation)) {
                cameraControl.setExposureCompensationIndex(settings.exposureCompensation)
            }

            cameraControl.setZoomRatio(settings.zoomRatio)

            val camera2Control = Camera2CameraControl.from(cameraControl)
            val builder = CaptureRequestOptions.Builder()

            when (settings.whiteBalanceMode) {
                WhiteBalanceMode.AUTO -> {
                    builder.setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE,
                        android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_AUTO
                    )
                }
                WhiteBalanceMode.MANUAL -> {
                    builder.setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE,
                        android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_OFF
                    )
                    val rggb = temperatureToRggb(settings.whiteBalanceTemperature)
                    builder.setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_GAINS,
                        rggb
                    )
                }
            }

            when (settings.focusMode) {
                FocusMode.AUTO -> {
                    builder.setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                        android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_AUTO
                    )
                }
                FocusMode.CONTINUOUS -> {
                    builder.setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                        android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    )
                }
                FocusMode.MANUAL -> {
                    builder.setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                        android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_OFF
                    )
                }
            }

            if (!settings.isIsoAuto) {
                builder.setCaptureRequestOption(
                    android.hardware.camera2.CaptureRequest.SENSOR_SENSITIVITY,
                    settings.iso
                )
                builder.setCaptureRequestOption(
                    android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE,
                    android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_OFF
                )
            } else {
                builder.setCaptureRequestOption(
                    android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE,
                    android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON
                )
            }

            camera2Control.setCaptureRequestOptions(builder.build())
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to apply camera settings", e)
        }
    }

    private fun temperatureToRggb(temperature: Int): android.hardware.camera2.params.RggbChannelVector {
        val temp = temperature.coerceIn(2000, 8000)
        val r = if (temp <= 4000) 1.0f else (temp - 4000f) / 2000f + 1f
        val b = if (temp >= 5500) 1.0f else (5500f - temp) / 2000f + 1f
        return android.hardware.camera2.params.RggbChannelVector(r, 1.0f, 1.0f, b)
    }

    fun startRecording(
        trackingNumber: String,
        videoRepository: com.express.video.repository.VideoRepository,
        resolution: VideoResolution,
        bitrateMbps: Int
    ): Boolean {
        val capture = videoCapture ?: return false
        if (isRecording) return false

        currentRecordingFile = videoRepository.getLocalVideoFile(trackingNumber)
        val outputFile = currentRecordingFile!!

        try {
            val outputOptions = FileOutputOptions.Builder(outputFile).build()

            recording = capture.output
                .prepareRecording(context, outputOptions)
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            Log.d("CameraManager", "Recording started")
                        }
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            if (event.hasError()) {
                                Log.e("CameraManager", "Recording error: ${event.error}")
                                onRecordingError?.invoke("录制失败: ${event.cause?.message}")
                                onRecordingComplete?.invoke(null)
                            } else {
                                Log.d("CameraManager", "Recording saved: ${outputFile.absolutePath}")
                                onRecordingComplete?.invoke(outputFile)
                            }
                            recording = null
                        }
                        is VideoRecordEvent.Status -> {}
                        is VideoRecordEvent.Pause -> {}
                        is VideoRecordEvent.Resume -> {}
                    }
                }
            return true
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to start recording", e)
            onRecordingError?.invoke("启动录制失败: ${e.message}")
            return false
        }
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
        isRecording = false
    }

    fun pauseRecording() {
        recording?.pause()
    }

    fun resumeRecording() {
        recording?.resume()
    }

    fun release() {
        stopRecording()
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
        videoCapture = null
        _preview = null
    }
}