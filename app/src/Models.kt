package com.example.bridge

data class TransactionRequest(
    val account_id: String,
    val amount: Double,
    val description: String,
    val type: String,
    val idempotency_key: String // To prevent double-spending
)