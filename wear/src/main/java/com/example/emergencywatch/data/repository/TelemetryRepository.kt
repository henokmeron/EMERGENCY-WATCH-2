package com.example.emergencywatch.data.repository

import android.os.Build
import com.example.emergencywatch.data.config.WatchConfigStore
import com.example.emergencywatch.data.config.WatchOperationalConfig
import com.example.emergencywatch.data.local.TelemetryDao
import com.example.emergencywatch.data.local.TelemetryEntity
import com.example.emergencywatch.data.remote.NetworkClient
import com.example.emergencywatch.data.remote.WatchApiService
import com.example.emergencywatch.data.remote.model.ApiErrorResponse
import com.example.emergencywatch.data.remote.model.EventRequest
import com.example.emergencywatch.data.remote.model.EventResponse
import com.example.emergencywatch.data.remote.model.HeartbeatRequest
import com.example.emergencywatch.data.remote.model.ReadingDto
import com.example.emergencywatch.data.remote.model.TelemetryRequest
import com.example.emergencywatch.data.remote.model.WatchConfig
import com.example.emergencywatch.domain.sensor.SensorData
import com.example.emergencywatch.domain.telemetry.TelemetrySampler
import java.util.UUID

sealed class TelemetrySyncResult {
    data class Success(val acceptedCount: Int, val remainingQueued: Int) : TelemetrySyncResult()
    data class Error(val code: String, val message: String) : TelemetrySyncResult()
    object Empty : TelemetrySyncResult()
}

class TelemetryRepository(
    private val apiService: WatchApiService,
    private val telemetryDao: TelemetryDao,
    private val configStore: WatchOperationalConfig,
    private val telemetrySampler: TelemetrySampler = TelemetrySampler(),
    private val onAuthRevoked: () -> Unit = {}
) {
    constructor(
        apiService: WatchApiService,
        telemetryDao: TelemetryDao,
        authRepository: AuthRepository,
        configStore: WatchOperationalConfig,
        telemetrySampler: TelemetrySampler = TelemetrySampler()
    ) : this(
        apiService = apiService,
        telemetryDao = telemetryDao,
        configStore = configStore,
        telemetrySampler = telemetrySampler,
        onAuthRevoked = { authRepository.handleRevocation() }
    )

    /**
     * Persists at most one reading per configured [WatchConfigStore.sampleIntervalSeconds].
     * Raw sensor / fall processing must call this only for telemetry; the gate does not
     * slow sensor callbacks themselves.
     *
     * @return true if a Room row was inserted
     */
    suspend fun recordReading(sensorData: SensorData): Boolean {
        if (!telemetrySampler.tryAcquire(configStore.sampleIntervalMs())) {
            return false
        }

        val entity = TelemetryEntity(
            readingId = UUID.randomUUID().toString(),
            recordedAt = sensorData.recordedAt,
            heartRate = sensorData.heartRate,
            contactStatus = sensorData.contactStatus,
            spo2 = sensorData.spo2,
            movement = sensorData.movement,
            fallDetected = sensorData.fallDetected,
            fallConfidence = sensorData.fallConfidence
        )
        telemetryDao.insertReading(entity)

        // Expire items older than 24 hours (86,400,000 ms)
        val cutoff = System.currentTimeMillis() - 86_400_000L
        telemetryDao.deleteOldReadings(cutoff)
        return true
    }

    suspend fun syncTelemetryBatch(): TelemetrySyncResult {
        val batchLimit = configStore.maxBatchSize()
        val unsyncedEntities = telemetryDao.getUnsyncedReadings(limit = batchLimit)
        if (unsyncedEntities.isEmpty()) {
            return TelemetrySyncResult.Empty
        }

        val readingDtos = unsyncedEntities.map { entity ->
            ReadingDto(
                readingId = entity.readingId,
                recordedAt = entity.recordedAt,
                heartRate = entity.heartRate,
                contactStatus = entity.contactStatus,
                spo2 = entity.spo2,
                movement = entity.movement,
                fallDetected = entity.fallDetected,
                fallConfidence = entity.fallConfidence
            )
        }

        val request = TelemetryRequest(readings = readingDtos)
        com.example.emergencywatch.util.PipelineLog.i(
            "POST /api/public/watch/telemetry count=${readingDtos.size} " +
                "firstHr=${readingDtos.firstOrNull()?.heartRate} firstAt=${readingDtos.firstOrNull()?.recordedAt}"
        )

        return try {
            val response = apiService.sendTelemetry(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                com.example.emergencywatch.util.PipelineLog.i(
                    "telemetry HTTP ${response.code()} accepted=${body.accepted} duplicates=${body.duplicates} " +
                        "invalid=${body.invalid} rejected_out_of_window=${body.rejectedOutOfWindow}"
                )

                // Remove accepted, duplicate, invalid, or out-of-window readings from queue
                val idsToRemove = mutableSetOf<String>()

                // 1. All submitted reading IDs are considered acknowledged when accepted > 0
                idsToRemove.addAll(unsyncedEntities.map { it.readingId })

                // 2. Specific invalid reading IDs reported
                body.invalidReadings?.forEach { invalidItem ->
                    idsToRemove.add(invalidItem.readingId)
                }

                telemetryDao.deleteReadings(idsToRemove.toList())

                val remainingCount = telemetryDao.getUnsyncedCount()
                TelemetrySyncResult.Success(acceptedCount = body.accepted, remainingQueued = remainingCount)
            } else {
                val errorJson = response.errorBody()?.string()
                val error = parseError(errorJson)
                com.example.emergencywatch.util.PipelineLog.e(
                    "telemetry HTTP ${response.code()} code=${error.code} detail=${error.detail} body=$errorJson"
                )
                if (error.code == "revoked" || error.code == "invalid_token") {
                    onAuthRevoked()
                }
                TelemetrySyncResult.Error(error.code ?: "http_error", error.detail ?: "Telemetry upload failed")
            }
        } catch (e: Exception) {
            com.example.emergencywatch.util.PipelineLog.e(
                "telemetry network failure: ${e.javaClass.simpleName}: ${e.message}"
            )
            TelemetrySyncResult.Error("network_failure", e.localizedMessage ?: "Network connection failure")
        }
    }

    suspend fun sendEvent(
        eventType: String, // "fall" | "sos" | "fall_cancelled"
        sensorData: SensorData
    ): Result<EventResponse> {
        val request = EventRequest(
            eventId = UUID.randomUUID().toString(), // Always generate a NEW UUID
            type = eventType,
            recordedAt = sensorData.recordedAt,
            heartRate = sensorData.heartRate,
            spo2 = sensorData.spo2,
            contactStatus = sensorData.contactStatus,
            fallConfidence = sensorData.fallConfidence
        )

        return try {
            val response = apiService.sendEvent(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorJson = response.errorBody()?.string()
                val error = parseError(errorJson)
                if (error.code == "revoked" || error.code == "invalid_token") {
                    onAuthRevoked()
                }
                Result.failure(Exception("${error.code}: ${error.detail}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendHeartbeat(
        batteryLevel: Int,
        sensorsCapabilities: Map<String, Boolean>
    ): WatchConfig? {
        val queuedCount = telemetryDao.getUnsyncedCount()
        val request = HeartbeatRequest(
            battery = batteryLevel,
            appVersion = if (isPhysicalAppVersion()) "1.0.0-PHYSICAL-DEV" else "1.0.0-EMULATOR",
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ${Build.MODEL}",
            sensors = sensorsCapabilities,
            queuedReadings = queuedCount
        )

        return try {
            val response = apiService.sendHeartbeat(request)
            if (response.isSuccessful && response.body() != null) {
                val config = response.body()!!.config
                if (config != null) {
                    configStore.updateFrom(config)
                }
                config
            } else {
                val errorJson = response.errorBody()?.string()
                val error = parseError(errorJson)
                if (error.code == "revoked" || error.code == "invalid_token") {
                    onAuthRevoked()
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchConfig(): WatchConfig? {
        return try {
            val response = apiService.getConfig()
            if (response.isSuccessful && response.body() != null) {
                val config = response.body()!!.config
                if (config != null) {
                    configStore.updateFrom(config)
                }
                config
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getQueuedCountSync(): Int = 0 // Used by UI preview or non-coroutine calls

    suspend fun getUnsyncedCount(): Int = telemetryDao.getUnsyncedCount()

    private fun parseError(errorJson: String?): ApiErrorResponse {
        if (errorJson.isNullOrBlank()) return ApiErrorResponse(code = "unknown_error", detail = "Unknown response error")
        return try {
            NetworkClient.gson.fromJson(errorJson, ApiErrorResponse::class.java)
        } catch (e: Exception) {
            ApiErrorResponse(code = "parse_error", detail = errorJson)
        }
    }

    private fun isPhysicalAppVersion(): Boolean {
        return !Build.FINGERPRINT.contains("generic", ignoreCase = true) &&
            !Build.MODEL.contains("sdk", ignoreCase = true) &&
            !Build.MODEL.contains("Emulator", ignoreCase = true)
    }
}
