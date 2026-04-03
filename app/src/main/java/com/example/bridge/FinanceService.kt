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
        
        // Targeted Wallet Regex
        val walletRegex = "You've paid Rs\\.\\s*([\\d,]+(?:\\.\\d+)?)\\s*via PhonePe wallet".toRegex()
        val match = walletRegex.find(text)

        if (match != null) {
            val amountValue = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
            
            // Create the payload matching your successful CURL schema
            val payload = TransactionRequest(
                amount = amountValue,
                category = "PhonePe Wallet", // Matches your API requirement
                description = "PhonePe Wallet Payment",
                type = "expense",
                account_id = com.example.bridge.BuildConfig.ACCOUNT_UUID,
                idempotency_key = UUID.nameUUIDFromBytes(text.toByteArray()).toString()
            )

            // DEBUG: See the match in your Fedora terminal
            android.util.Log.d("BRIDGE_DEBUG", "Match Found! Amount: $amountValue. Syncing...")

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
            
            // Get the raw token and add the "Bearer " prefix (CRITICAL for your API)
            val rawToken = securePrefs.getString("auth_token", "") ?: ""
            val authHeader = "Bearer $rawToken"

            scope.launch {
                try {
                    val response = RetrofitClient.instance.postTransaction(authHeader, data)
                    android.util.Log.d("BRIDGE_DEBUG", "Sync Success: 201 Created")
                } catch (e: Exception) {
                    android.util.Log.e("BRIDGE_ERROR", "Sync failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BRIDGE_ERROR", "Vault/Service Error: ${e.message}")
        }
    }
}