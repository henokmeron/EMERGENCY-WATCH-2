package com.example.emergencywatch.domain.sensor

import android.content.Context
import android.util.Log

/**
 * Selects Samsung Health Sensor SDK when a real tracker connection succeeds;
 * otherwise falls back to Android SensorManager. Never reports Samsung as active
 * merely because the SDK class is on the classpath.
 */
class SensorManagerFacade(private val context: Context) {

    private val samsungProvider = SamsungHealthSensorProvider()
    private val androidProvider = AndroidStandardSensorProvider()

    @Volatile
    private var activeProvider: SensorProvider = androidProvider

    @Volatile
    private var usingSamsung = false

    init {
        samsungProvider.initialize(context)
        androidProvider.initialize(context)
    }

    fun getActiveSource(): String {
        return if (usingSamsung && samsungProvider.isAvailable()) {
            "Samsung Health Sensor SDK v1.4.1 (Physical Galaxy Watch 4)"
        } else {
            "Android SensorManager (Test / Emulator Fallback Engine)"
        }
    }

    fun getCapabilities(): Map<String, Boolean> {
        return activeProvider.getCapabilities()
    }

    fun startTracking(onDataReceived: (SensorData) -> Unit) {
        usingSamsung = false
        activeProvider = androidProvider

        if (!samsungProvider.isSdkPresent()) {
            Log.i(TAG, "Samsung SDK absent; using Android SensorManager fallback")
            activeProvider = androidProvider
            androidProvider.startTracking(onDataReceived)
            return
        }

        var fallbackStarted = false
        fun startAndroidFallback(reason: String) {
            if (fallbackStarted) return
            fallbackStarted = true
            usingSamsung = false
            try {
                samsungProvider.stopTracking()
            } catch (_: Throwable) {
            }
            activeProvider = androidProvider
            Log.w(TAG, "Falling back to Android sensors: $reason")
            Log.w("EWPipeline", "ACTIVE_SOURCE=Android SensorManager fallback reason=$reason")
            androidProvider.startTracking(onDataReceived)
        }

        samsungProvider.setConnectionCallbacks(
            onSucceeded = {
                usingSamsung = true
                activeProvider = samsungProvider
                Log.i(TAG, "Active sensor source switched to Samsung Health Sensor SDK")
                Log.i("EWPipeline", "ACTIVE_SOURCE=Samsung Health Sensor SDK")
            },
            onFailed = { reason ->
                startAndroidFallback(reason)
            }
        )

        // Start Samsung connection attempt. Fallback runs only if connection/setup fails.
        Log.i("EWPipeline", "Attempting Samsung HealthTrackingService.connectService()")
        samsungProvider.startTracking(onDataReceived)
    }

    fun stopTracking() {
        samsungProvider.stopTracking()
        androidProvider.stopTracking()
        usingSamsung = false
        activeProvider = androidProvider
    }

    companion object {
        private const val TAG = "SensorManagerFacade"
    }
}
