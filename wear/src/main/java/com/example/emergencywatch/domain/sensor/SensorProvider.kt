package com.example.emergencywatch.domain.sensor

import android.content.Context

interface SensorProvider {
    fun initialize(context: Context)
    fun isAvailable(): Boolean
    fun getCapabilities(): Map<String, Boolean>
    fun startTracking(onDataReceived: (SensorData) -> Unit)
    fun stopTracking()
}
