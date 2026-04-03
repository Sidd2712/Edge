package com.example.bridge

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class FinanceService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 1. Only listen to PhonePe
        if (sbn.packageName != "com.phonepe.app") return

        val text = sbn.notification.extras.getCharSequence("android.text")?.toString() ?: ""
        
        // 2. Targeted Wallet Regex
        // Matches: "You've paid Rs. 1 via PhonePe wallet" or "Rs. 1,250.50"
        val walletRegex = "You've paid Rs\\.\\s*([\\d,]+(?:\\.\\d+)?)\\s*via PhonePe wallet".toRegex()
        val match = walletRegex.find(text)

        if (match != null) {
            // Extract amount and remove commas (e.g., "1,000" -> "1000")
            val amountCleaned = match.groupValues[1].replace(",", "")
            val amountValue = amountCleaned.toDoubleOrNull() ?: 0.0
            
            // Create a unique ID so we don't log the same notification twice
            val idempotencyKey = UUID.nameUUIDFromBytes(text.toByteArray()).toString()

            val payload = TransactionRequest(
                account_id = com.example.bridge.BuildConfig.ACCOUNT_UUID,
                amount = amountValue,
                category = "PhonePe Wallet",
                description = "PhonePe Wallet Payment",
                type = "expense",
                idempotency_key = idempotencyKey
            )

            syncToVercel(payload)
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
            
            val token = securePrefs.getString("auth_token", "") ?: ""

            scope.launch {
                try {
                    RetrofitClient.instance.postTransaction(token, data)
                } catch (e: Exception) {
                    // Log error to ADB for your Fedora terminal
                    android.util.Log.e("BRIDGE_ERROR", "Sync failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}