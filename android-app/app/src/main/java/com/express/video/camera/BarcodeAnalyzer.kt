package com.express.video.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    private var isProcessing = false
    private var lastDetectedTime = 0L
    private val debounceTime = 500L

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectedTime < debounceTime) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessing = true
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                processBarcodes(barcodes)
            }
            .addOnFailureListener { e ->
                Log.e("BarcodeAnalyzer", "Barcode scanning failed", e)
            }
            .addOnCompleteListener {
                isProcessing = false
                imageProxy.close()
            }
    }

    private fun processBarcodes(barcodes: List<Barcode>) {
        for (barcode in barcodes) {
            val rawValue = barcode.rawValue
            if (!rawValue.isNullOrEmpty()) {
                Log.d("BarcodeAnalyzer", "Detected barcode: format=${barcode.format}, value=$rawValue")
                if (barcode.format == Barcode.FORMAT_CODE_128 ||
                    barcode.format == Barcode.FORMAT_CODE_39 ||
                    barcode.format == Barcode.FORMAT_CODE_93 ||
                    barcode.format == Barcode.FORMAT_EAN_13 ||
                    barcode.format == Barcode.FORMAT_EAN_8 ||
                    barcode.format == Barcode.FORMAT_UPC_A ||
                    barcode.format == Barcode.FORMAT_UPC_E ||
                    barcode.format == Barcode.FORMAT_QR_CODE
                ) {
                    lastDetectedTime = System.currentTimeMillis()
                    onBarcodeDetected(rawValue)
                    return
                }
            }
        }
    }
}
