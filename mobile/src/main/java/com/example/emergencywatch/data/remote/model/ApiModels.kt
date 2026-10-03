package com.example.emergencywatch.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * Backend API models mirrored from :wear — same JSON contracts for
 * https://med-eye.lovable.app. Do not invent new schemas here.
 */

data class PairRequest(
    @SerializedName("code") val code: String,
    @SerializedName("model") val model: String? = null,
    @SerializedName("os_version") val osVersion: String? = null,
    @SerializedName("app_version") val appVersion: String? = null
)

data class PairResponse(
    @SerializedName("status") val status: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("patient_id") val patientId: String,
    @SerializedName("device_token") val deviceToken: String
)

data class WatchConfig(
    @SerializedName("patient_id") val patientId: String? = null,
    @SerializedName("patient_name") val patientName: String? = null,
    @SerializedName("baseline_heart_rate") val baselineHeartRate: Double? = null,
    @SerializedName("baseline_spo2") val baselineSpo2: Double? = null,
    @SerializedName("upload_interval_seconds") val uploadIntervalSeconds: Int? = 60,
    @SerializedName("sample_interval_seconds") val sampleIntervalSeconds: Int? = 5,
    @SerializedName("heartbeat_interval_seconds") val heartbeatIntervalSeconds: Int? = 300,
    @SerializedName("max_batch_size") val maxBatchSize: Int? = 100,
    @SerializedName("fall_cancel_window_seconds") val fallCancelWindowSeconds: Int? = 30
)

data class ConfigResponse(
    @SerializedName("status") val status: String,
    @SerializedName("config") val config: WatchConfig? = null
)

data class ApiErrorResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("detail") val detail: String? = null
)
