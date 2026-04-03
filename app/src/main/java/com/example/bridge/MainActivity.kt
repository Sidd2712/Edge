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

        // --- UI Setup ---
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
            text = "System Logs:\n"
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

        // --- STEP 1: ADD SERVICE STATUS CHECK HERE ---
        val isEnabled = isNotificationServiceEnabled()
        if (isEnabled) {
            logView.append("> Service: LISTENING 👂\n")
        } else {
            logView.append("> Service: DISCONNECTED ❌\n")
            statusText.text = "  Action Required: Enable Service"
            statusDot.setBackgroundColor(0xFFCF6679.toInt()) // Red
        }

        saveToken()

        btnTest.setOnClickListener { performTestSync("Manual Request") }
        btnPermission.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        performTestSync("Startup Check")
    }

    // This checks if the Android OS has actually "bound" to your service
    private fun isNotificationServiceEnabled(): Boolean {
        val cn = android.content.ComponentName(this, FinanceService::class.java)
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
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
            logView.append("> Token stored (Len: ${token.length})\n")
        } catch (e: Exception) {
            logView.append("> Vault Error: ${e.message}\n")
        }
    }

    private fun performTestSync(reason: String) {
        // Only update UI if we aren't already in an error state from the service check
        if (statusText.text.contains("Initializing")) {
            statusText.text = "  Syncing..."
        }
        
        scope.launch {
            try {
                val rawToken = com.example.bridge.BuildConfig.JWT_TOKEN
                val authHeader = if (rawToken.startsWith("Bearer", ignoreCase = true)) {
                    rawToken 
                } else {
                    "Bearer $rawToken"
                }
                
                val testData = TransactionRequest(
                    amount = 1.0,
                    category = "string",
                    description = reason,
                    type = "string",
                    account_id = com.example.bridge.BuildConfig.ACCOUNT_UUID
                )
                
                withContext(Dispatchers.IO) {
                    RetrofitClient.instance.postTransaction(authHeader, testData)
                }
                
                // On Success, we only turn Green if the Service is also listening
                if (isNotificationServiceEnabled()) {
                    statusText.text = "  System Online"
                    statusDot.setBackgroundColor(0xFF03DAC5.toInt()) // Teal
                }
                logView.append("> Sync Success: 201 Created\n")
            } catch (e: Exception) {
                statusText.text = "  Connection Error"
                statusDot.setBackgroundColor(0xFFCF6679.toInt())
                logView.append("> Sync Error: ${e.localizedMessage}\n")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}