package com.express.video.camera

import android.content.Context
import android.util.Log
import android.util.Range
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.OutputOptions
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

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var preview: Preview? = null
    private var currentRecordingFile: File? = null

    var isRecording: Boolean = false
        private set

    var onRecordingComplete: ((File?) -> Unit)? = null
    var onRecordingError: ((String) -> Unit)? = null

    private val mainExecutor: ExecutorService = ContextCompat.getMainExecutor(context)
        as ExecutorService

    fun initialize(onReady: (() -> Unit)? = null) {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                setupCamera()
                onReady?.invoke()
            } catch (e: Exception) {
                Log.e("CameraManager", "Camera initialization failed", e)
                onRecordingError?.invoke("摄像头初始化失败: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupCamera() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        preview = Preview.Builder()
            .build()
            .also {
                it.surfaceProvider = previewView.surfaceProvider
            }

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()

        videoCapture = VideoCapture.withOutput(recorder)

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                videoCapture
            )
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to bind camera use cases", e)
        }
    }

    fun applySettings(settings: CameraSettings) {
        val cam = camera ?: return
        val cameraControl = cam.cameraControl
        val cameraInfo = cam.cameraInfo

        try {
            val exposureRange = cameraInfo.exposureState.exposureCompensationRange
            if (exposureRange.contains(settings.exposureCompensation)) {
                cameraControl.setExposureCompensationIndex(settings.exposureCompensation)
            }

            val meteringPointFactory = SurfaceOrientedMeteringPointFactory(
                1f, 1f
            )
            val meteringPoint = meteringPointFactory.createPoint(0.5f, 0.5f)
            val action = FocusMeteringAction.Builder(meteringPoint)
                .setAutoCancelDuration(0, TimeUnit.SECONDS)
                .build()

            when (settings.focusMode) {
                FocusMode.AUTO -> {
                    cameraControl.startFocusAndMetering(action)
                }
                FocusMode.CONTINUOUS -> {
                }
                FocusMode.MANUAL -> {
                    cameraControl.cancelFocusAndMetering()
                }
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to apply camera settings", e)
        }
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
                        is VideoRecordEvent.Status -> {
                        }
                        is VideoRecordEvent.Pause -> {
                        }
                        is VideoRecordEvent.Resume -> {
                        }
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
        preview = null
    }
}
