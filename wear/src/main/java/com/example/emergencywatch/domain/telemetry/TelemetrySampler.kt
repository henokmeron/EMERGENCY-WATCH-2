package com.example.emergencywatch.domain.telemetry

import android.os.SystemClock

/**
 * Thread-safe gate that allows at most one telemetry persist per sampling interval.
 * Uses monotonic elapsed time (not wall clock) so sleep/NTP adjustments cannot
 * open or close the gate incorrectly.
 *
 * Does not affect raw sensor callbacks or fall-detection processing — callers
 * invoke this only at Room persistence time.
 */
class TelemetrySampler(
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val lock = Any()
    private var lastAcceptedElapsedMs: Long = UNSET

    /**
     * @return true if a telemetry reading should be persisted now.
     */
    fun tryAcquire(intervalMs: Long): Boolean {
        val interval = intervalMs.coerceAtLeast(1L)
        synchronized(lock) {
            val now = elapsedRealtimeMs()
            if (lastAcceptedElapsedMs == UNSET || now - lastAcceptedElapsedMs >= interval) {
                lastAcceptedElapsedMs = now
                return true
            }
            return false
        }
    }

    fun reset() {
        synchronized(lock) {
            lastAcceptedElapsedMs = UNSET
        }
    }

    companion object {
        private const val UNSET = -1L
    }
}
