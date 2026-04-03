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
import java.util.UUID

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
            text = "SECURE FINANCE BRIDGE"
            textSize = 24f
            setTextColor(0xFFBB86FC.toInt())
            gravity = Gravity.CENTER
        }

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
        }

        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40)
            setBackgroundResource(android.R.drawable.presence_invisible)
        }

        statusText = TextView(this).apply {
            text = "  Initializing..."
            setTextColor(0xFFFFFFFF.toInt())
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
            
            // DEBUG LINE 1: This tells us if the secret exists
            logView.append("> Token stored (Len: ${token.length})\n")
        } catch (e: Exception) {
            logView.append("> Vault Error: ${e.message}\n")
        }
    }

    private fun performTestSync(reason: String) {
        statusText.text = "  Syncing..."
        
        scope.launch {
            try {
                val rawToken = com.example.bridge.BuildConfig.JWT_TOKEN
                
                // DEBUG LINE 2: Shows the start of the token so you can verify it
                val preview = if (rawToken.length > 5) rawToken.take(5) + "..." else "EMPTY"
                logView.append("> Syncing with token starting: $preview\n")

                // DEBUG LINE 3: SMART PREFIX (Fixes the "Bearer Bearer" issue)
                val authHeader = if (rawToken.startsWith("Bearer", ignoreCase = true)) {
                    rawToken 
                } else {
                    "Bearer $rawToken"
                }
                
                val testData = TransactionRequest(
                    amount = 0.0,
                    category = "Debug",
                    description = reason,
                    type = "income",
                    account_id = com.example.bridge.BuildConfig.ACCOUNT_UUID,
                    idempotency_key = UUID.randomUUID().toString()
                )
                
                withContext(Dispatchers.IO) {
                    RetrofitClient.instance.postTransaction(authHeader, testData)
                }
                
                statusText.text = "  Vercel Connected!"
                statusDot.setBackgroundColor(0xFF03DAC5.toInt()) 
                logView.append("> Success: 201 Created\n")
            } catch (e: Exception) {
                statusText.text = "  Auth Failed"
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