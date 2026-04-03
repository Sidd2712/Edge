package com.example.bridge

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
// Note: We will access BuildConfig directly to avoid import issues

interface TransactionApi {
    @POST("api/transactions/")
    suspend fun postTransaction(
        @Header("Authorization") token: String,
        @Body data: TransactionRequest
    )
}

object RetrofitClient {
    private val BASE_URL: String = com.example.bridge.BuildConfig.BASE_URL

    val instance: TransactionApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TransactionApi::class.java)
    }
}