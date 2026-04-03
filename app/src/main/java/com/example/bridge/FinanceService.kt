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

    // Helper to show debugging messages on screen
    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.phonepe.app") return

        val text = sbn.notification.extras.getCharSequence("android.text")?.toString() ?: ""
        
        // --- DEBUG: This will show you EXACTLY what the app hears ---
        showToast("PhonePe Heard: ${text.take(20)}...")

        // Improved Regex: More flexible with spaces and dots
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

            showToast("Match Found! ₹$amountValue. Syncing...")
            syncToVercel(payload)
        } else {
            // If you see this Toast, our Regex pattern is wrong for your message
            showToast("Regex Mismatch for message!")
            android.util.Log.d("BRIDGE_DEBUG", "No match for: $text")
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
                    showToast("Sync Successful: 201")
                } catch (e: Exception) {
                    showToast("Sync Failed: ${e.localizedMessage}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BRIDGE_ERROR", "Vault Error: ${e.message}")
        }
    }
}