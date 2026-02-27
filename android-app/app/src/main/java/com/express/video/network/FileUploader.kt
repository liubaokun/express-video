package com.express.video.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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

    private val mainHandler = Handler(Looper.getMainLooper())

    fun uploadFile(
        serverAddress: String,
        serverPort: Int,
        file: File,
        trackingNumber: String,
        onProgress: (UploadResult) -> Unit
    ) {
        Thread {
            try {
                if (serverAddress.isBlank()) {
                    mainHandler.post { 
                        onProgress(UploadResult.Error("服务器地址未配置，请先在设置中扫描二维码或手动输入服务器地址"))
                    }
                    return@Thread
                }

                val url = "http://$serverAddress:$serverPort/upload"
                Log.d("FileUploader", "Starting upload to: $url, file: ${file.absolutePath}, size: ${file.length()}")

                if (!file.exists()) {
                    mainHandler.post { 
                        onProgress(UploadResult.Error("视频文件不存在: ${file.absolutePath}"))
                    }
                    return@Thread
                }

                if (file.length() == 0L) {
                    mainHandler.post { 
                        onProgress(UploadResult.Error("视频文件大小为0，录制可能失败"))
                    }
                    return@Thread
                }

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

                mainHandler.post { onProgress(UploadResult.Progress(0)) }

                Log.d("FileUploader", "Sending request...")
                val response = client.newCall(request).execute()
                Log.d("FileUploader", "Response code: ${response.code}")

                if (response.isSuccessful) {
                    mainHandler.post { onProgress(UploadResult.Progress(100)) }
                    val responseBody = response.body?.string() ?: "上传成功"
                    mainHandler.post { onProgress(UploadResult.Success(responseBody)) }
                    Log.d("FileUploader", "Upload successful: $responseBody")
                } else {
                    val errorMsg = "服务器返回错误: ${response.code} ${response.message}"
                    mainHandler.post { onProgress(UploadResult.Error(errorMsg)) }
                    Log.e("FileUploader", errorMsg)
                }
            } catch (e: UnknownHostException) {
                val errorMsg = "无法连接到服务器 $serverAddress:$serverPort，请检查网络和服务器地址"
                Log.e("FileUploader", errorMsg, e)
                mainHandler.post { onProgress(UploadResult.Error(errorMsg)) }
            } catch (e: ConnectException) {
                val errorMsg = "连接被拒绝，请确认服务器已启动 (地址: $serverAddress:$serverPort)"
                Log.e("FileUploader", errorMsg, e)
                mainHandler.post { onProgress(UploadResult.Error(errorMsg)) }
            } catch (e: SocketTimeoutException) {
                val errorMsg = "连接超时，请检查网络连接"
                Log.e("FileUploader", errorMsg, e)
                mainHandler.post { onProgress(UploadResult.Error(errorMsg)) }
            } catch (e: Exception) {
                val errorMsg = "上传失败: ${e.javaClass.simpleName} - ${e.message}"
                Log.e("FileUploader", errorMsg, e)
                mainHandler.post { onProgress(UploadResult.Error(errorMsg)) }
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
                if (serverAddress.isBlank()) {
                    mainHandler.post { onResult(false, "服务器地址为空") }
                    return@Thread
                }

                val url = "http://$serverAddress:$serverPort/ping"
                Log.d("FileUploader", "Testing connection to: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    mainHandler.post { onResult(true, "连接成功") }
                } else {
                    mainHandler.post { onResult(false, "服务器返回: ${response.code}") }
                }
            } catch (e: UnknownHostException) {
                mainHandler.post { onResult(false, "无法解析服务器地址: $serverAddress") }
            } catch (e: ConnectException) {
                mainHandler.post { onResult(false, "连接被拒绝，请确认服务器已启动") }
            } catch (e: SocketTimeoutException) {
                mainHandler.post { onResult(false, "连接超时") }
            } catch (e: Exception) {
                mainHandler.post { onResult(false, "连接失败: ${e.message}") }
            }
        }.start()
    }
}
