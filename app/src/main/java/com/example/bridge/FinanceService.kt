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
        if (sbn.packageName != "com.phonepe.app") return

        val text = sbn.notification.extras.getCharSequence("android.text")?.toString() ?: ""
        
        // Match example: "Paid to Blinkit DEBIT ₹303"
        val regex = "Paid to (.+?) DEBIT ₹([\\d,]+(?:\\.\\d+)?)".toRegex()
        val match = regex.find(text)

        match?.let {
            val merchant = it.groupValues[1]
            val amountCleaned = it.groupValues[2].replace(",", "")
            val amountValue = amountCleaned.toDoubleOrNull() ?: 0.0
            
            val hash = UUID.nameUUIDFromBytes(text.toByteArray()).toString()

            val payload = TransactionRequest(
                account_id = com.example.bridge.BuildConfig.ACCOUNT_UUID,
                amount = amountValue,
                description = "Paid to $merchant",
                type = "expense",
                idempotency_key = hash
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
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}