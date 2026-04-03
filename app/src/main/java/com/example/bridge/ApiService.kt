package com.example.bridge

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// NO IMPORT for BuildConfig needed here if we use the full path below

interface TransactionApi {
    @POST("api/v1/transactions/")
    suspend fun postTransaction(
        @Header("Authorization") token: String,
        @Body data: TransactionRequest
    )
}

object RetrofitClient {
    // We use the full package path to force the compiler to find it
    private val BASE_URL: String = com.example.bridge.BuildConfig.BASE_URL

    val instance: TransactionApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TransactionApi::class.java)
    }
}