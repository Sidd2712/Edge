package com.example.bridge

data class TransactionRequest(
    val amount: Double,
    val category: String,      // Added this
    val description: String,
    val type: String,
    val account_id: String,
    val idempotency_key: String? = null // Optional, but good for retries
)