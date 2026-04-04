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

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.phonepe.app") return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val fullContent = "$title $text"
        
        android.util.Log.d("BRIDGE_DEBUG", "PhonePe Detected: $fullContent")

        // --- THE REGEX ARSENAL ---

        // 1. Debit Regex (Your existing working pattern)
        val debitRegex = "paid Rs\\.\\s*([\\d,.]+)\\s*via PhonePe wallet".toRegex(RegexOption.IGNORE_CASE)

        // 2. New Credit Regex (Matches: "Money recieved... has sent your ₹ 40")
        // Note: handles 'recieved' or 'received' and the ₹ symbol
        val creditRegex = "Money rec[ei]+ved .*? has sent your [₹Rs\\.]+\\s*([\\d,.]+)".toRegex(RegexOption.IGNORE_CASE)

        val debitMatch = debitRegex.find(fullContent)
        val creditMatch = creditRegex.find(fullContent)

        when {
            debitMatch != null -> {
                val amount = debitMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
                sync(amount, "PhonePe Debit", "expense")
            }
            creditMatch != null -> {
                val amount = creditMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
                showToast("💰 Income Received: ₹$amount")
                sync(amount, "PhonePe Credit", "income")
            }
            else -> {
                // Helpful for catching new formats in Fedora terminal
                android.util.Log.d("BRIDGE_DEBUG", "No Regex match for: $fullContent")
            }
        }
    }

    private fun sync(amountValue: Double, desc: String, transactionType: String) {
        val payload = TransactionRequest(
            amount = amountValue,
            category = "PhonePe",
            description = desc,
            type = transactionType,
            account_id = com.example.bridge.BuildConfig.ACCOUNT_UUID
        )

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
                    RetrofitClient.instance.postTransaction(authHeader, payload)
                    showToast("🚀 $transactionType Sync: 201")
                } catch (e: Exception) {
                    showToast("⚠️ Sync Error: ${e.localizedMessage}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BRIDGE_ERROR", "Vault Error: ${e.message}")
        }
    }
}