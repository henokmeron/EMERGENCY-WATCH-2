package com.example.emergencywatch.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telemetry_records")
data class TelemetryEntity(
    @PrimaryKey
    @ColumnInfo(name = "reading_id")
    val readingId: String,

    @ColumnInfo(name = "recorded_at")
    val recordedAt: String,

    @ColumnInfo(name = "created_at_epoch")
    val createdAtEpoch: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "heart_rate")
    val heartRate: Int? = null,

    @ColumnInfo(name = "contact_status")
    val contactStatus: Boolean = true,

    @ColumnInfo(name = "spo2")
    val spo2: Int? = null,

    @ColumnInfo(name = "movement")
    val movement: Double = 0.0,

    @ColumnInfo(name = "fall_detected")
    val fallDetected: Boolean = false,

    @ColumnInfo(name = "fall_confidence")
    val fallConfidence: Double? = null,

    @ColumnInfo(name = "lat")
    val lat: Double? = null,

    @ColumnInfo(name = "lng")
    val lng: Double? = null,

    @ColumnInfo(name = "accuracy_m")
    val accuracyM: Double? = null
)
