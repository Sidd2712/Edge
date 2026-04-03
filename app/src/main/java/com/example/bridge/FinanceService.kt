package com.example.bridge

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FinanceService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 1. Filter for PhonePe only
        if (sbn.packageName != "com.phonepe.app") return

        val text = sbn.notification.extras.getCharSequence("android.text")?.toString() ?: ""
        
        // 2. Wallet-specific Regex
        val walletRegex = "You've paid Rs\\.\\s*([\\d,]+(?:\\.\\d+)?)\\s*via PhonePe wallet".toRegex()
        val match = walletRegex.find(text)

        if (match != null) {
            val amountValue = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
            
            // 3. Create payload matching your EXACT 5-field schema (fixes 422)
            val payload = TransactionRequest(
                amount = amountValue,
                category = "PhonePe Wallet",
                description = "PhonePe Wallet Payment",
                type = "expense",
                account_id = com.example.bridge.BuildConfig.ACCOUNT_UUID
            )

            android.util.Log.d("BRIDGE_DEBUG", "Notification matched! Amount: $amountValue")
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
            
            val rawToken = securePrefs.getString("auth_token", "") ?: ""
            
            // 4. Smart Bearer Logic (Ensures no "Bearer Bearer" 401 error)
            val authHeader = if (rawToken.startsWith("Bearer", ignoreCase = true)) {
                rawToken 
            } else {
                "Bearer $rawToken"
            }

            scope.launch {
                try {
                    // Call the API
                    RetrofitClient.instance.postTransaction(authHeader, data)
                    android.util.Log.d("BRIDGE_DEBUG", "Service Sync: 201 Created")
                } catch (e: Exception) {
                    // This will catch 422 if the model still doesn't match
                    android.util.Log.e("BRIDGE_ERROR", "Service Sync failed: ${e.localizedMessage}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BRIDGE_ERROR", "Vault/Service Error: ${e.message}")
        }
    }
}