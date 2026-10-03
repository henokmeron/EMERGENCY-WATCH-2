package com.example.emergencywatch.domain.sensor

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class SensorData(
    val heartRate: Int? = null,
    val contactStatus: Boolean = false,
    val spo2: Int? = null,
    val movement: Double = 0.0,
    val fallDetected: Boolean = false,
    val fallConfidence: Double? = null,
    val recordedAt: String = getCurrentIsoTimestamp()
) {
    companion object {
        fun getCurrentIsoTimestamp(): String {
            val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            df.timeZone = TimeZone.getTimeZone("UTC")
            return df.format(Date())
        }
    }
}
