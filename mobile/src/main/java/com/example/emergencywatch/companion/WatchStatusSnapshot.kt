package com.example.emergencywatch.companion

/**
 * Phone-side view of a connected watch. Extensible for multi-watch/patient later.
 */
data class WatchStatusSnapshot(
    val nodeId: String? = null,
    val nodeDisplayName: String? = null,
    val connected: Boolean = false,
    val isPaired: Boolean = false,
    val batteryLevel: Int? = null,
    val heartRate: Int? = null,
    val contactStatus: Boolean? = null,
    val queuedReadings: Int = 0,
    val lastUploadTime: String? = null,
    val sensorSource: String? = null,
    val capabilitiesJson: String? = null,
    val currentScreen: String? = null,
    val sosActive: Boolean = false,
    val statusMessage: String? = null,
    val patientId: String? = null,
    val deviceId: String? = null,
    val patientName: String? = null,
    val cloudOk: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
    val appVersion: String? = null
)
