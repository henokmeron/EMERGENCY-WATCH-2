package com.example.emergencywatch.data.config

import android.content.Context
import com.example.emergencywatch.data.remote.model.WatchConfig

/**
 * Persists operational timing from backend /config so the foreground service
 * and telemetry repository share the same sample/upload/batch settings.
 * Defaults match the live backend contract when config has not been fetched yet.
 */
class WatchConfigStore(context: Context) : WatchOperationalConfig {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun updateFrom(config: WatchConfig) {
        val sample = (config.sampleIntervalSeconds ?: DEFAULT_SAMPLE_INTERVAL_SECONDS).coerceAtLeast(1)
        val upload = (config.uploadIntervalSeconds ?: DEFAULT_UPLOAD_INTERVAL_SECONDS).coerceAtLeast(1)
        val batch = (config.maxBatchSize ?: DEFAULT_MAX_BATCH_SIZE).coerceAtLeast(1)
        prefs.edit()
            .putInt(KEY_SAMPLE_INTERVAL_SECONDS, sample)
            .putInt(KEY_UPLOAD_INTERVAL_SECONDS, upload)
            .putInt(KEY_MAX_BATCH_SIZE, batch)
            .apply()
    }

    override fun sampleIntervalSeconds(): Int =
        prefs.getInt(KEY_SAMPLE_INTERVAL_SECONDS, DEFAULT_SAMPLE_INTERVAL_SECONDS).coerceAtLeast(1)

    override fun uploadIntervalSeconds(): Int =
        prefs.getInt(KEY_UPLOAD_INTERVAL_SECONDS, DEFAULT_UPLOAD_INTERVAL_SECONDS).coerceAtLeast(1)

    override fun maxBatchSize(): Int =
        prefs.getInt(KEY_MAX_BATCH_SIZE, DEFAULT_MAX_BATCH_SIZE).coerceAtLeast(1)

    companion object {
        const val DEFAULT_SAMPLE_INTERVAL_SECONDS = 5
        const val DEFAULT_UPLOAD_INTERVAL_SECONDS = 60
        const val DEFAULT_MAX_BATCH_SIZE = 100

        private const val PREFS_NAME = "emergency_watch_operational_config"
        private const val KEY_SAMPLE_INTERVAL_SECONDS = "sample_interval_seconds"
        private const val KEY_UPLOAD_INTERVAL_SECONDS = "upload_interval_seconds"
        private const val KEY_MAX_BATCH_SIZE = "max_batch_size"
    }
}
