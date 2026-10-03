package com.example.emergencywatch.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * Confirmed Backend API Models for Emergency Monitoring API.
 * Base URL: https://med-eye.lovable.app
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

data class LocationDto(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lng") val lng: Double,
    @SerializedName("accuracy_m") val accuracyM: Double
)

data class ReadingDto(
    @SerializedName("reading_id") val readingId: String,
    @SerializedName("recorded_at") val recordedAt: String,
    @SerializedName("heart_rate") val heartRate: Int? = null,
    @SerializedName("contact_status") val contactStatus: Boolean,
    @SerializedName("spo2") val spo2: Int? = null,
    @SerializedName("movement") val movement: Double = 0.0,
    @SerializedName("fall_detected") val fallDetected: Boolean = false,
    @SerializedName("fall_confidence") val fallConfidence: Double? = null,
    @SerializedName("location") val location: LocationDto? = null
)

data class TelemetryRequest(
    @SerializedName("readings") val readings: List<ReadingDto>
)

data class InvalidReadingItem(
    @SerializedName("index") val index: Int,
    @SerializedName("reading_id") val readingId: String,
    @SerializedName("issues") val issues: List<String>? = null
)

data class AlertItem(
    @SerializedName("alert_id") val alertId: String,
    @SerializedName("priority") val priority: String,
    @SerializedName("reasons") val reasons: List<String>? = null
)

data class TelemetryResponse(
    @SerializedName("status") val status: String,
    @SerializedName("accepted") val accepted: Int = 0,
    @SerializedName("duplicates") val duplicates: Int = 0,
    @SerializedName("rejected_out_of_window") val rejectedOutOfWindow: Int = 0,
    @SerializedName("invalid") val invalid: Int = 0,
    @SerializedName("invalid_readings") val invalidReadings: List<InvalidReadingItem>? = null,
    @SerializedName("event_logged") val eventLogged: Boolean? = null,
    @SerializedName("alerts") val alerts: List<AlertItem>? = null
)

data class EventRequest(
    @SerializedName("event_id") val eventId: String,
    @SerializedName("type") val type: String, // "fall" | "sos" | "fall_cancelled"
    @SerializedName("recorded_at") val recordedAt: String,
    @SerializedName("heart_rate") val heartRate: Int? = null,
    @SerializedName("spo2") val spo2: Int? = null,
    @SerializedName("contact_status") val contactStatus: Boolean? = null,
    @SerializedName("fall_confidence") val fallConfidence: Double? = null,
    @SerializedName("location") val location: LocationDto? = null
)

data class EventResponse(
    @SerializedName("status") val status: String,
    @SerializedName("event_logged") val eventLogged: Boolean? = false,
    @SerializedName("alert_id") val alertId: String? = null,
    @SerializedName("priority") val priority: String? = null,
    @SerializedName("duplicate") val duplicate: Boolean? = null
)

data class HeartbeatRequest(
    @SerializedName("battery") val battery: Int? = null,
    @SerializedName("app_version") val appVersion: String? = null,
    @SerializedName("os_version") val osVersion: String? = null,
    @SerializedName("sensors") val sensors: Map<String, Boolean>? = null,
    @SerializedName("queued_readings") val queuedReadings: Int? = null
)

data class ThresholdsConfig(
    @SerializedName("hr_deviation_pct") val hrDeviationPct: Double? = null,
    @SerializedName("sustained_minutes") val sustainedMinutes: Int? = null,
    @SerializedName("spo2_drop_points") val spo2DropPoints: Double? = null,
    @SerializedName("stillness_movement") val stillnessMovement: Double? = null,
    @SerializedName("absolute_hr_max") val absoluteHrMax: Int? = null
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
    @SerializedName("fall_cancel_window_seconds") val fallCancelWindowSeconds: Int? = 30,
    @SerializedName("thresholds") val thresholds: ThresholdsConfig? = null
)

data class HeartbeatResponse(
    @SerializedName("status") val status: String,
    @SerializedName("config") val config: WatchConfig? = null
)

data class ConfigResponse(
    @SerializedName("status") val status: String,
    @SerializedName("config") val config: WatchConfig? = null
)

data class TokenRefreshResponse(
    @SerializedName("status") val status: String,
    @SerializedName("device_token") val deviceToken: String,
    @SerializedName("previous_token_valid_seconds") val previousTokenValidSeconds: Int? = 600
)

data class ApiErrorResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("issues") val issues: List<String>? = null
)
