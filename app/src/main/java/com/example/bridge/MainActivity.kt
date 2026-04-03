package com.example.bridge

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var logView: TextView
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 60, 50, 50) 
            setBackgroundColor(0xFF121212.toInt())
        }

        val title = TextView(this).apply {
            text = "BRIDGE DASHBOARD"
            textSize = 24f
            setTextColor(0xFFBB86FC.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 40)
        }

        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40)
            setBackgroundResource(android.R.drawable.presence_invisible)
        }

        statusText = TextView(this).apply {
            text = "  Initializing..."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        }

        statusRow.addView(statusDot)
        statusRow.addView(statusText)

        val btnTest = Button(this).apply { text = "Manual Sync Test" }
        val btnPermission = Button(this).apply { text = "Fix Notification Access" }

        logView = TextView(this).apply {
            text = "Logs:\n"
            setTextColor(0xFF03DAC6.toInt())
            textSize = 14f
            setPadding(0, 40, 0, 0)
        }

        root.addView(title)
        root.addView(statusRow)
        root.addView(btnTest)
        root.addView(btnPermission)
        root.addView(logView)
        setContentView(root)

        saveToken()

        btnTest.setOnClickListener { performTestSync("Manual Request") }
        btnPermission.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        performTestSync("Startup Check")
    }

    private fun saveToken() {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val securePrefs = EncryptedSharedPreferences.create(
                "secret_prefs", masterKeyAlias, this,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val token = com.example.bridge.BuildConfig.JWT_TOKEN
            securePrefs.edit().putString("auth_token", token).apply()
            logView.append("> Token Vault Secured (Len: ${token.length})\n")
        } catch (e: Exception) {
            logView.append("> Vault Error: ${e.message}\n")
        }
    }

    private fun performTestSync(reason: String) {
        statusText.text = "  Syncing..."
        
        scope.launch {
            try {
                val rawToken = com.example.bridge.BuildConfig.JWT_TOKEN
                val authHeader = if (rawToken.startsWith("Bearer", ignoreCase = true)) {
                    rawToken 
                } else {
                    "Bearer $rawToken"
                }
                
                // EXACT MATCH FOR YOUR CURL PAYLOAD
                val testData = TransactionRequest(
                    amount = 1.0,           // Changed from 0.0 to 1.0
                    category = "string",     // Matches your curl exactly
                    description = "string",  // Matches your curl exactly
                    type = "string",         // Matches your curl exactly
                    account_id = com.example.bridge.BuildConfig.ACCOUNT_UUID
                )
                
                withContext(Dispatchers.IO) {
                    RetrofitClient.instance.postTransaction(authHeader, testData)
                }
                
                statusText.text = "  Vercel Online"
                statusDot.setBackgroundColor(0xFF03DAC5.toInt()) 
                logView.append("> Success: 201 Created\n")
            } catch (e: Exception) {
                statusText.text = "  Data Error (422?)"
                statusDot.setBackgroundColor(0xFFCF6679.toInt())
                logView.append("> Error: ${e.localizedMessage}\n")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}