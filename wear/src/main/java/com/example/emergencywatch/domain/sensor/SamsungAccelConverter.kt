package com.example.emergencywatch.domain.sensor

/**
 * Converts Samsung Health Sensor SDK accelerometer raw integers to m/s².
 *
 * Per ValueKey.AccelerometerSet documentation:
 * m/s² = (9.81 / (16383.75 / 4.0)) * rawValue
 */
object SamsungAccelConverter {
    private const val RAW_TO_MS2 = 9.81f / (16383.75f / 4.0f)

    fun toMetersPerSecondSquared(raw: Int): Float = RAW_TO_MS2 * raw
}
