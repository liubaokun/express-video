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
        val file = File(dir, "$safeFileName.mp4")
        Log.d(TAG, "Video file path: ${file.absolutePath}")
        return file
    }

    fun saveToMediaStore(trackingNumber: String, file: File): Boolean {
        Log.d(TAG, "Starting to save video to MediaStore: ${file.absolutePath}, exists: ${file.exists()}, size: ${if (file.exists()) file.length() else 0}")
        
        if (!file.exists()) {
            Log.e(TAG, "Source file does not exist")
            return false
        }
        
        if (file.length() == 0L) {
            Log.e(TAG, "Source file is empty")
            return false
        }

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
        
        if (uri == null) {
            Log.e(TAG, "Failed to create MediaStore entry")
            return false
        }
        
        Log.d(TAG, "MediaStore URI created: $uri")

        var outputStream: OutputStream? = null
        var inputStream: FileInputStream? = null
        try {
            outputStream = resolver.openOutputStream(uri)
            if (outputStream == null) {
                Log.e(TAG, "Failed to open output stream")
                return false
            }
            
            inputStream = FileInputStream(file)
            val bytesCopied = inputStream.copyTo(outputStream)
            Log.d(TAG, "Copied $bytesCopied bytes to MediaStore")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                val updated = resolver.update(uri, contentValues, null, null)
                Log.d(TAG, "Updated IS_PENDING, rows affected: $updated")
            }

            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { path, uri ->
                Log.d(TAG, "MediaScanner completed: path=$path, uri=$uri")
            }
            
            Log.d(TAG, "Video saved successfully to MediaStore")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save video to MediaStore", e)
            try {
                resolver.delete(uri, null, null)
            } catch (deleteError: Exception) {
                Log.e(TAG, "Failed to delete incomplete entry", deleteError)
            }
            return false
        } finally {
            try {
                outputStream?.close()
                inputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing streams", e)
            }
        }
    }

    fun deleteLocalFile(file: File): Boolean {
        return if (file.exists()) {
            val deleted = file.delete()
            Log.d(TAG, "Deleted local file: ${file.absolutePath}, success: $deleted")
            deleted
        } else {
            true
        }
    }
}