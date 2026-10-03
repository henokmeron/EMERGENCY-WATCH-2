package com.example.emergencywatch.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "test_event_timeline")
data class TestEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "scenario_name") val scenarioName: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "epoch_ms") val epochMs: Long,
    @ColumnInfo(name = "heart_rate") val heartRate: Int?,
    @ColumnInfo(name = "contact_status") val contactStatus: Boolean,
    @ColumnInfo(name = "movement") val movement: Double,
    @ColumnInfo(name = "battery_level") val batteryLevel: Int,
    @ColumnInfo(name = "sensor_conclusion") val sensorConclusion: String,
    @ColumnInfo(name = "user_action") val userAction: String, // "PENDING", "CANCELLED", "CONFIRMED", "NONE"
    @ColumnInfo(name = "final_state") val finalState: String, // "NORMAL", "ALERT_LOGGED", "CANCELLED", "SOS"
    @ColumnInfo(name = "synced_to_backend") val syncedToBackend: Boolean = false
)
