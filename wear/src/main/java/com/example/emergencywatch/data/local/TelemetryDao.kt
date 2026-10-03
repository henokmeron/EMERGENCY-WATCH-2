package com.example.emergencywatch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TelemetryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: TelemetryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadings(readings: List<TelemetryEntity>)

    @Query("SELECT * FROM telemetry_records ORDER BY created_at_epoch ASC LIMIT :limit")
    suspend fun getUnsyncedReadings(limit: Int = 100): List<TelemetryEntity>

    @Query("DELETE FROM telemetry_records WHERE reading_id IN (:readingIds)")
    suspend fun deleteReadings(readingIds: List<String>)

    @Query("DELETE FROM telemetry_records WHERE created_at_epoch < :cutoffEpochMillis")
    suspend fun deleteOldReadings(cutoffEpochMillis: Long)

    @Query("SELECT COUNT(*) FROM telemetry_records")
    suspend fun getUnsyncedCount(): Int

    @Query("DELETE FROM telemetry_records")
    suspend fun clearAll()
}
