package com.example.emergencywatch.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android Keystore-backed secure storage manager.
 * Stores device_token, device_id, and patient_id safely without plain text persistence.
 */
class SecureAuthManager(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "emergency_watch_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getDeviceToken(): String? {
        return prefs.getString(KEY_DEVICE_TOKEN, null)
    }

    fun saveAuthData(deviceToken: String, deviceId: String, patientId: String) {
        prefs.edit()
            .putString(KEY_DEVICE_TOKEN, deviceToken)
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_PATIENT_ID, patientId)
            .apply()
    }

    fun updateDeviceToken(newToken: String) {
        val currentToken = getDeviceToken()
        prefs.edit()
            .putString(KEY_PREVIOUS_TOKEN, currentToken)
            .putString(KEY_DEVICE_TOKEN, newToken)
            .putLong(KEY_TOKEN_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getDeviceId(): String? {
        return prefs.getString(KEY_DEVICE_ID, null)
    }

    fun getPatientId(): String? {
        return prefs.getString(KEY_PATIENT_ID, null)
    }

    fun isPaired(): Boolean {
        return !getDeviceToken().isNull_or_empty()
    }

    fun clearAuthData() {
        prefs.edit().clear().apply()
    }

    private fun String?.isNull_or_empty(): Boolean {
        return this == null || this.trim().isEmpty()
    }

    companion object {
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_PREVIOUS_TOKEN = "previous_device_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PATIENT_ID = "patient_id"
        private const val KEY_TOKEN_UPDATED_AT = "token_updated_at"
    }
}
