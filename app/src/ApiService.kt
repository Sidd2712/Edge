package com.example.bridge

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface TransactionApi {
    @POST("api/v1/transactions/")
    suspend fun postTransaction(
        @Header("Authorization") token: String,
        @Body data: TransactionRequest
    )
}

object RetrofitClient {
    private const val BASE_URL = BuildConfig.BASE_URL

    val instance: TransactionApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TransactionApi::class.java)
    }
}