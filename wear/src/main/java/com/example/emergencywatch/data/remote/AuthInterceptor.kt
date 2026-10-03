package com.example.emergencywatch.data.remote

import com.example.emergencywatch.data.security.SecureAuthManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val authManager: SecureAuthManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // Do NOT attach Authorization header to pairing endpoint
        if (path.endsWith("/pair")) {
            return chain.proceed(originalRequest)
        }

        val token = authManager.getDeviceToken()
        return if (!token.isNullOrBlank()) {
            val authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
