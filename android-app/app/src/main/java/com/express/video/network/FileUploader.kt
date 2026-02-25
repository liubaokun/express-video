package com.express.video.network

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

sealed class UploadResult {
    data class Success(val message: String) : UploadResult()
    data class Error(val message: String) : UploadResult()
    data class Progress(val percent: Int) : UploadResult()
}

class FileUploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun uploadFile(
        serverAddress: String,
        serverPort: Int,
        file: File,
        trackingNumber: String,
        onProgress: (UploadResult) -> Unit
    ) {
        Thread {
            try {
                val url = "http://$serverAddress:$serverPort/upload"

                val requestBody = file.asRequestBody("video/mp4".toMediaType())
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("trackingNumber", trackingNumber)
                    .addFormDataPart("file", file.name, requestBody)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(multipartBody)
                    .build()

                onProgress(UploadResult.Progress(0))

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    onProgress(UploadResult.Progress(100))
                    val responseBody = response.body?.string() ?: "上传成功"
                    onProgress(UploadResult.Success(responseBody))
                    Log.d("FileUploader", "Upload successful: $responseBody")
                } else {
                    val errorMsg = "上传失败: ${response.code} ${response.message}"
                    onProgress(UploadResult.Error(errorMsg))
                    Log.e("FileUploader", errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "上传异常: ${e.message}"
                onProgress(UploadResult.Error(errorMsg))
                Log.e("FileUploader", "Upload failed", e)
            }
        }.start()
    }

    fun testConnection(
        serverAddress: String,
        serverPort: Int,
        onResult: (Boolean, String) -> Unit
    ) {
        Thread {
            try {
                val url = "http://$serverAddress:$serverPort/ping"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    onResult(true, "连接成功")
                } else {
                    onResult(false, "连接失败: ${response.code}")
                }
            } catch (e: Exception) {
                onResult(false, "连接失败: ${e.message}")
            }
        }.start()
    }
}
