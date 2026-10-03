package com.example.emergencywatch.data.repository

import android.os.Build
import com.example.emergencywatch.data.remote.NetworkClient
import com.example.emergencywatch.data.remote.WatchApiService
import com.example.emergencywatch.data.remote.model.ApiErrorResponse
import com.example.emergencywatch.data.remote.model.PairRequest
import com.example.emergencywatch.data.security.SecureAuthManager

sealed class PairResult {
    data class Success(val deviceId: String, val patientId: String) : PairResult()
    data class Error(val code: String, val message: String) : PairResult()
}

sealed class RefreshResult {
    object Success : RefreshResult()
    data class Error(val code: String, val message: String) : RefreshResult()
}

class AuthRepository(
    private val apiService: WatchApiService,
    private val authManager: SecureAuthManager
) {

    fun isPaired(): Boolean = authManager.isPaired()

    fun getDeviceId(): String? = authManager.getDeviceId()

    fun getPatientId(): String? = authManager.getPatientId()

    suspend fun pairWatch(pairingCode: String): PairResult {
        val cleanCode = pairingCode.replace(" ", "").trim()
        if (cleanCode.length < 6 || cleanCode.length > 20) {
            return PairResult.Error("invalid_length", "Pairing code must be between 6 and 20 characters")
        }

        val request = PairRequest(
            code = cleanCode,
            model = "${Build.MODEL ?: "Samsung Galaxy Watch"} PHYSICAL-DEV",
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) PHYSICAL",
            appVersion = "1.0.0-PHYSICAL-DEV"
        )

        return try {
            val response = apiService.pairWatch(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                authManager.saveAuthData(
                    deviceToken = body.deviceToken,
                    deviceId = body.deviceId,
                    patientId = body.patientId
                )
                PairResult.Success(body.deviceId, body.patientId)
            } else {
                val errorBody = response.errorBody()?.string()
                val apiError = parseError(errorBody)
                PairResult.Error(
                    code = apiError.code ?: "pairing_failed",
                    message = apiError.detail ?: "Failed to pair watch with code provided"
                )
            }
        } catch (e: Exception) {
            PairResult.Error("network_error", e.localizedMessage ?: "Network connection error")
        }
    }

    suspend fun refreshToken(): RefreshResult {
        return try {
            val response = apiService.refreshToken()
            if (response.isSuccessful && response.body() != null) {
                val newToken = response.body()!!.deviceToken
                authManager.updateDeviceToken(newToken)
                RefreshResult.Success
            } else {
                val errorBody = response.errorBody()?.string()
                val apiError = parseError(errorBody)
                if (apiError.code == "revoked" || apiError.code == "invalid_token") {
                    handleRevocation()
                }
                RefreshResult.Error(apiError.code ?: "refresh_failed", apiError.detail ?: "Token refresh failed")
            }
        } catch (e: Exception) {
            RefreshResult.Error("network_error", e.localizedMessage ?: "Network error during token refresh")
        }
    }

    fun handleRevocation() {
        authManager.clearAuthData()
    }

    private fun parseError(errorJson: String?): ApiErrorResponse {
        if (errorJson.isNull_or_empty()) return ApiErrorResponse(code = "unknown_error", detail = "Unknown error occurred")
        return try {
            NetworkClient.gson.fromJson(errorJson, ApiErrorResponse::class.java)
        } catch (e: Exception) {
            ApiErrorResponse(code = "parse_error", detail = errorJson)
        }
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()
}
