package com.example.emergencywatch.data.config

import com.example.emergencywatch.data.remote.model.WatchConfig

/**
 * Operational timing used by telemetry sampling and upload.
 * Defaults: sample 5s, upload 60s, batch 100.
 */
interface WatchOperationalConfig {
    fun sampleIntervalSeconds(): Int
    fun uploadIntervalSeconds(): Int
    fun maxBatchSize(): Int
    fun sampleIntervalMs(): Long = sampleIntervalSeconds() * 1000L
    fun uploadIntervalMs(): Long = uploadIntervalSeconds() * 1000L
    fun updateFrom(config: WatchConfig)
}
