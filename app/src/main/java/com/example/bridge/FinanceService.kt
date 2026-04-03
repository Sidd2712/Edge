package com.example.bridge

import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.Toast
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FinanceService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // --- STEP 2: LIFECYCLE HOOKS ---
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        showToast("👂 Finance Bridge: LISTENING")
        android.util.Log.d("BRIDGE_DEBUG", "Service bound to Android OS")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        showToast("❌ Finance Bridge: DISCONNECTED")
    }

    // --- NOTIFICATION HANDLER ---

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val text = sbn.notification.extras.getCharSequence("android.text")?.toString() ?: ""

        // DEBUG: Uncomment the line below to see EVERY notification your phone gets
        // showToast("Heard from: $packageName") 


        // 1. Log the full text to your Fedora terminal (via adb logcat)
        android.util.Log.d("BRIDGE_DEBUG", "PhonePe Raw Text: $text")
        showToast("PhonePe: ${text.take(15)}...")

        // 2. Robust Regex (Handles Rs., ₹, dots, and spaces)
        val walletRegex = "paid Rs\\.\\s*([\\d,.]+)\\s*via PhonePe wallet".toRegex(RegexOption.IGNORE_CASE)
        val match = walletRegex.find(text)

        if (match != null) {
            val amountValue = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
            
            val payload = TransactionRequest(
                amount = amountValue,
                category = "PhonePe Wallet",
                description = "PhonePe Wallet Payment",
                type = "expense",
                account_id = com.example.bridge.BuildConfig.ACCOUNT_UUID
            )

            showToast("✅ Match! ₹$amountValue. Syncing...")
            syncToVercel(payload)
        } else {
            // If it hits here, the text doesn't match our "paid Rs... via wallet" pattern
            android.util.Log.e("BRIDGE_DEBUG", "Regex Mismatch: $text")
        }
    }

    private fun syncToVercel(data: TransactionRequest) {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val securePrefs = EncryptedSharedPreferences.create(
                "secret_prefs", masterKeyAlias, this,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            val rawToken = securePrefs.getString("auth_token", "") ?: ""
            val authHeader = if (rawToken.startsWith("Bearer", ignoreCase = true)) rawToken else "Bearer $rawToken"

            scope.launch {
                try {
                    RetrofitClient.instance.postTransaction(authHeader, data)
                    showToast("🚀 Vercel Sync: 201")
                } catch (e: Exception) {
                    showToast("⚠️ Sync Error: ${e.localizedMessage}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BRIDGE_ERROR", "Vault Error: ${e.message}")
        }
    }
}