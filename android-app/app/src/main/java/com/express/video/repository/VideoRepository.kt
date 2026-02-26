package com.express.video.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

class VideoRepository(private val context: Context) {

    companion object {
        const val COLLECTION_NAME = "ExpressVideo"
        private const val TAG = "VideoRepository"
    }

    fun getLocalVideoFile(trackingNumber: String): File {
        val dir = File(context.cacheDir, COLLECTION_NAME)
        if (!dir.exists()) {
            val created = dir.mkdirs()
            Log.d(TAG, "Created cache directory: ${dir.absolutePath}, success: $created")
        }
        val safeFileName = trackingNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(dir, "$safeFileName.mp4")
    }

    fun saveToMediaStore(trackingNumber: String, file: File): Boolean {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$trackingNumber.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$COLLECTION_NAME")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return false

        var outputStream: OutputStream? = null
        var inputStream: FileInputStream? = null
        try {
            outputStream = resolver.openOutputStream(uri)
            inputStream = FileInputStream(file)
            inputStream.copyTo(outputStream!!)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            outputStream?.close()
            inputStream?.close()
        }
    }

    fun deleteLocalFile(file: File): Boolean {
        return if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }
}
