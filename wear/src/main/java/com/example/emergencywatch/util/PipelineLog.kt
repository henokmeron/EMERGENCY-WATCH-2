package com.example.emergencywatch.util

import android.util.Log

/**
 * Safe Log wrapper — android.util.Log throws on plain JVM unit tests.
 */
object PipelineLog {
    private const val TAG = "EWPipeline"

    fun i(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Throwable) {
            // no-op in unit tests
        }
    }

    fun w(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Throwable) {
        }
    }

    fun e(message: String) {
        try {
            Log.e(TAG, message)
        } catch (_: Throwable) {
        }
    }
}
