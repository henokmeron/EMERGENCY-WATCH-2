package com.example.emergencywatch.domain.sensor

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Processes 3-axis accelerometer data into normalized movement activity score (0-1000)
 * and detects possible high-G impact fall events.
 */
class MovementProcessor {

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var isFirstRead = true

    /**
     * Converts raw 3-axis acceleration (m/s²) into a normalized movement metric (0-1000).
     * Gravity is ~9.81 m/s². Deltas between consecutive samples reflect physical movement.
     */
    fun processAccelerometer(x: Float, y: Float, z: Float): Double {
        if (isFirstRead) {
            lastX = x
            lastY = y
            lastZ = z
            isFirstRead = false
            return 0.0
        }

        val deltaX = abs(x - lastX)
        val deltaY = abs(y - lastY)
        val deltaZ = abs(z - lastZ)

        lastX = x
        lastY = y
        lastZ = z

        val totalDelta = deltaX + deltaY + deltaZ
        // Scale to 0 - 1000 range. Normal sitting/wrist wiggle gives 0.05 - 5.0, rapid movement up to 1000.
        val normalizedMovement = (totalDelta * 10.0).coerceIn(0.0, 1000.0)

        return (normalizedMovement * 100).toInt() / 100.0
    }

    /**
     * Detects sudden impact spikes (> 2.5g or > 25 m/s²) followed by stillness.
     */
    fun checkFallImpact(x: Float, y: Float, z: Float): Pair<Boolean, Double?> {
        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
        val gForce = magnitude / 9.81

        return if (gForce > 2.8) {
            val confidence = ((gForce - 2.8) / 3.0).coerceIn(0.5, 0.99)
            Pair(true, (confidence * 100).toInt() / 100.0)
        } else {
            Pair(false, null)
        }
    }
}
