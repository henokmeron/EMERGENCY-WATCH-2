package com.example.emergencywatch.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    const val BASE_URL = "https://med-eye.lovable.app"

    val gson: Gson = GsonBuilder().setLenient().create()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader("Authorization")
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: WatchApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(WatchApiService::class.java)
    }

    /**
     * Lightweight reachability probe — does not require a device token.
     * Treats any HTTP response (including 401/404) as "backend reachable".
     */
    fun probeBackendReachable(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/api/public/watch/config")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                response.code in 100..599
            }
        } catch (_: Exception) {
            false
        }
    }
}
