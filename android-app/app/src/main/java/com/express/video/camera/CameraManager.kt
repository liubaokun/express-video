package com.express.video.camera

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
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
import com.express.video.model.VideoResolution
import com.express.video.model.WhiteBalanceMode
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Executor

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
    private var camera2Control: Camera2CameraControl? = null
    
    private var currentRecordingFile: File? = null
    private var isInitialized = false

    var isRecording: Boolean = false
        private set

    var onRecordingComplete: ((File?) -> Unit)? = null
    var onRecordingError: ((String?) -> Unit)? = null

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
                    onRecordingError?.invoke("Camera initialization failed: cannot get camera provider")
                    return@addListener
                }
                setupCamera()
                isInitialized = true
                onReady?.invoke()
            } catch (e: Exception) {
                Log.e("CameraManager", "Camera initialization failed", e)
                onRecordingError?.invoke("Camera initialization failed: ${e.message}")
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
            
            setupContinuousFocus()
            
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to bind camera use cases", e)
            onRecordingError?.invoke("Failed to bind camera: ${e.message}")
        }
    }

    private fun setupContinuousFocus() {
        val cam = camera ?: return
        try {
            camera2Control = Camera2CameraControl.from(cam.cameraControl)
            
            camera2Control?.setCaptureRequestOptions(
                androidx.camera.camera2.interop.CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    )
                    .build()
            )
            Log.d("CameraManager", "Continuous video focus enabled")
        } catch (e: Exception) {
            Log.w("CameraManager", "Failed to set continuous focus", e)
        }
    }

    fun setWhiteBalance(whiteBalance: Int) {
        val control = camera2Control ?: return
        try {
            control.setCaptureRequestOptions(
                androidx.camera.camera2.interop.CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AWB_MODE,
                        whiteBalance
                    )
                    .build()
            )
            Log.d("CameraManager", "White balance set to: $whiteBalance")
        } catch (e: Exception) {
            Log.w("CameraManager", "Failed to set white balance", e)
        }
    }

    fun setColorTemperature(colorTemp: Int) {
        val mode = WhiteBalanceMode.fromColorTemp(colorTemp)
        setWhiteBalance(mode.mode)
        Log.d("CameraManager", "Color temp $colorTemp K -> preset ${mode.label}")
    }

    fun getZoomRange(): Range<Float> {
        return camera?.cameraInfo?.zoomState?.value?.let { zoomState ->
            Range(1.0f, zoomState.maxZoomRatio)
        } ?: Range(1.0f, 10.0f)
    }

    fun getCurrentZoom(): Float {
        return camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1.0f
    }

    fun setZoomRatio(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
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

        Log.d("CameraManager", "Starting recording to: ${outputFile.absolutePath}")

        try {
            val outputOptions = FileOutputOptions.Builder(outputFile).build()

            recording = capture.output
                .prepareRecording(context, outputOptions)
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            Log.d("CameraManager", "Recording started successfully")
                        }
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            if (event.hasError()) {
                                Log.e("CameraManager", "Recording error: ${event.error}, cause: ${event.cause}")
                                onRecordingError?.invoke("Recording failed: ${event.cause?.message}")
                                onRecordingComplete?.invoke(null)
                            } else {
                                Log.d("CameraManager", "Recording saved: ${outputFile.absolutePath}, size: ${outputFile.length()}")
                                onRecordingComplete?.invoke(outputFile)
                            }
                            recording = null
                        }
                        is VideoRecordEvent.Status -> {
                            Log.d("CameraManager", "Recording status: duration=${event.recordingStats.recordedDurationNanos}")
                        }
                        is VideoRecordEvent.Pause -> {
                            Log.d("CameraManager", "Recording paused")
                        }
                        is VideoRecordEvent.Resume -> {
                            Log.d("CameraManager", "Recording resumed")
                        }
                    }
                }
            return true
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to start recording", e)
            onRecordingError?.invoke("Failed to start recording: ${e.message}")
            return false
        }
    }

    fun stopRecording() {
        Log.d("CameraManager", "Stopping recording")
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
        camera2Control = null
        isInitialized = false
    }
}