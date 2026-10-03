package com.example.emergencywatch.domain.sensor

import android.content.Context
import android.util.Log
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey

/**
 * Real Samsung Health Sensor SDK integration (v1.4.1 AAR).
 *
 * Connects via [HealthTrackingService], obtains continuous HR and accelerometer
 * [HealthTracker] instances, and feeds readings into the existing SensorData pipeline.
 *
 * [isAvailable] is true only after connection succeeds and tracker listeners are registered.
 */
class SamsungHealthSensorProvider : SensorProvider {

    private var appContext: Context? = null
    private var sdkPresent = false

    @Volatile
    private var isConnected = false

    @Volatile
    private var trackingReady = false

    private var healthTrackingService: HealthTrackingService? = null
    private var heartRateTracker: HealthTracker? = null
    private var accelerometerTracker: HealthTracker? = null

    private var dataCallback: ((SensorData) -> Unit)? = null
    private var onConnectionFailed: ((String) -> Unit)? = null
    private var onConnectionSucceeded: (() -> Unit)? = null

    private val movementProcessor = MovementProcessor()

    private var currentHeartRate: Int? = null
    private var currentContactStatus: Boolean = false
    private var currentMovement: Double = 0.0
    private var currentFallDetected: Boolean = false
    private var currentFallConfidence: Double? = null

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.i(TAG, "HealthTrackingService connection succeeded")
            isConnected = true
            val setupOk = setupTrackers()
            if (setupOk) {
                trackingReady = true
                onConnectionSucceeded?.invoke()
            } else {
                Log.e(TAG, "Connected but required trackers could not be started")
                isConnected = false
                trackingReady = false
                disconnectQuietly()
                onConnectionFailed?.invoke("Required Samsung trackers unavailable")
            }
        }

        override fun onConnectionEnded() {
            Log.w(TAG, "HealthTrackingService connection ended")
            isConnected = false
            trackingReady = false
        }

        override fun onConnectionFailed(exception: HealthTrackerException) {
            val reason = buildSafeFailureReason(exception)
            Log.e(TAG, "HealthTrackingService connection failed: $reason")
            isConnected = false
            trackingReady = false
            onConnectionFailed?.invoke(reason)
        }
    }

    private val heartRateListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: List<DataPoint>) {
            dataPoints.forEach { processHeartRateDataPoint(it) }
            dispatchData()
        }

        override fun onFlushCompleted() {
            Log.d(TAG, "Heart rate flush completed")
        }

        override fun onError(error: HealthTracker.TrackerError) {
            Log.e(TAG, "Heart rate tracker error: ${error.name}")
            Log.e("EWPipeline", "Samsung HR tracker error=${error.name}")
            // Without this, we stay "connected" but never emit HR → empty Lovable charts.
            if (error == HealthTracker.TrackerError.PERMISSION_ERROR ||
                error == HealthTracker.TrackerError.SDK_POLICY_ERROR
            ) {
                trackingReady = false
                onConnectionFailed?.invoke("Samsung HR ${error.name}")
            }
        }
    }

    private val accelerometerListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: List<DataPoint>) {
            dataPoints.forEach { processAccelerometerDataPoint(it) }
            dispatchData()
        }

        override fun onFlushCompleted() {
            Log.d(TAG, "Accelerometer flush completed")
        }

        override fun onError(error: HealthTracker.TrackerError) {
            Log.e(TAG, "Accelerometer tracker error: ${error.name}")
            Log.e("EWPipeline", "Samsung accel tracker error=${error.name}")
            // Accel alone failing is often SDK_POLICY (developer mode / partner registration).
            // Keep HR if it works; only fail-over when HR also cannot run.
            if (error == HealthTracker.TrackerError.SDK_POLICY_ERROR && heartRateTracker == null) {
                trackingReady = false
                onConnectionFailed?.invoke("Samsung accel ${error.name}")
            }
        }
    }

    fun setConnectionCallbacks(
        onSucceeded: (() -> Unit)?,
        onFailed: ((String) -> Unit)?
    ) {
        onConnectionSucceeded = onSucceeded
        onConnectionFailed = onFailed
    }

    fun isSdkPresent(): Boolean = sdkPresent

    override fun initialize(context: Context) {
        appContext = context.applicationContext
        sdkPresent = try {
            Class.forName("com.samsung.android.service.health.tracking.HealthTrackingService")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        if (!sdkPresent) {
            Log.w(TAG, "Samsung HealthTrackingService class not found on classpath")
        }
    }

    override fun isAvailable(): Boolean = sdkPresent && isConnected && trackingReady

    override fun getCapabilities(): Map<String, Boolean> {
        val active = isAvailable()
        return mapOf(
            SensorType.HEART_RATE.keyName to (active && heartRateTracker != null),
            SensorType.ACCELEROMETER.keyName to (active && accelerometerTracker != null),
            SensorType.PPG.keyName to false,
            SensorType.SKIN_TEMPERATURE.keyName to false,
            SensorType.SPO2.keyName to false,
            SensorType.ECG.keyName to false,
            SensorType.EDA.keyName to false,
            SensorType.BIA.keyName to false
        )
    }

    override fun startTracking(onDataReceived: (SensorData) -> Unit) {
        dataCallback = onDataReceived
        if (!sdkPresent) {
            onConnectionFailed?.invoke("Samsung Health Sensor SDK not present")
            return
        }
        val context = appContext
        if (context == null) {
            onConnectionFailed?.invoke("Samsung provider not initialized")
            return
        }

        try {
            if (healthTrackingService == null) {
                healthTrackingService = HealthTrackingService(connectionListener, context)
            }
            healthTrackingService?.connectService()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start HealthTrackingService: ${t.javaClass.simpleName}")
            isConnected = false
            trackingReady = false
            onConnectionFailed?.invoke(t.javaClass.simpleName)
        }
    }

    private fun setupTrackers(): Boolean {
        val service = healthTrackingService ?: return false

        val supported = try {
            service.trackingCapability.supportHealthTrackerTypes
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to read tracker capabilities: ${t.javaClass.simpleName}")
            emptyList()
        }

        var hrOk = false
        var accelOk = false

        if (supported.contains(HealthTrackerType.HEART_RATE_CONTINUOUS)) {
            try {
                heartRateTracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                heartRateTracker?.setEventListener(heartRateListener)
                hrOk = heartRateTracker != null
                Log.i(TAG, "HEART_RATE_CONTINUOUS tracker listener registered")
                // Log first few HR samples via existing processHeartRateDataPoint path
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start HEART_RATE_CONTINUOUS: ${t.javaClass.simpleName}")
                heartRateTracker = null
            }
        } else {
            Log.w(TAG, "HEART_RATE_CONTINUOUS not in supported tracker list: $supported")
        }

        if (supported.contains(HealthTrackerType.ACCELEROMETER_CONTINUOUS)) {
            try {
                accelerometerTracker = service.getHealthTracker(HealthTrackerType.ACCELEROMETER_CONTINUOUS)
                accelerometerTracker?.setEventListener(accelerometerListener)
                accelOk = accelerometerTracker != null
                Log.i(TAG, "ACCELEROMETER_CONTINUOUS tracker listener registered")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start ACCELEROMETER_CONTINUOUS: ${t.javaClass.simpleName}")
                accelerometerTracker = null
            }
        } else {
            Log.w(TAG, "ACCELEROMETER_CONTINUOUS not in supported tracker list: $supported")
        }

        Log.i("EWPipeline", "Samsung trackers hrOk=$hrOk accelOk=$accelOk supported=$supported")
        return hrOk || accelOk
    }

    private fun processHeartRateDataPoint(dataPoint: DataPoint) {
        try {
            val status = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS)
            if (status != null) {
                // -3 = wearable detached (Samsung HEART_RATE_STATUS table)
                currentContactStatus = status != -3
            }

            val hr = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE)
            if (hr != null && hr in 1..300) {
                // Prefer successful measurements (status 1); still accept valid BPM otherwise
                if (status == null || status == 1 || status == -2 || status == -8 || status == -10) {
                    if (currentHeartRate == null || currentHeartRate != hr) {
                        Log.i("EWPipeline", "Samsung HR raw=$hr status=$status")
                    }
                    currentHeartRate = hr
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "HR datapoint parse failed: ${t.javaClass.simpleName}")
        }
    }

    private fun processAccelerometerDataPoint(dataPoint: DataPoint) {
        try {
            val rawX = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_X) ?: return
            val rawY = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Y) ?: return
            val rawZ = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Z) ?: return

            val x = SamsungAccelConverter.toMetersPerSecondSquared(rawX)
            val y = SamsungAccelConverter.toMetersPerSecondSquared(rawY)
            val z = SamsungAccelConverter.toMetersPerSecondSquared(rawZ)

            currentMovement = movementProcessor.processAccelerometer(x, y, z)
            val (fall, conf) = movementProcessor.checkFallImpact(x, y, z)
            currentFallDetected = fall
            currentFallConfidence = conf
        } catch (t: Throwable) {
            Log.w(TAG, "Accel datapoint parse failed: ${t.javaClass.simpleName}")
        }
    }

    private fun dispatchData() {
        val data = SensorData(
            heartRate = currentHeartRate,
            contactStatus = currentContactStatus,
            spo2 = null,
            movement = currentMovement,
            fallDetected = currentFallDetected,
            fallConfidence = currentFallConfidence
        )
        dataCallback?.invoke(data)

        if (currentFallDetected) {
            currentFallDetected = false
            currentFallConfidence = null
        }
    }

    override fun stopTracking() {
        try {
            heartRateTracker?.unsetEventListener()
        } catch (_: Throwable) {
        }
        try {
            accelerometerTracker?.unsetEventListener()
        } catch (_: Throwable) {
        }
        heartRateTracker = null
        accelerometerTracker = null
        trackingReady = false
        disconnectQuietly()
        dataCallback = null
        onConnectionFailed = null
        onConnectionSucceeded = null
    }

    private fun disconnectQuietly() {
        try {
            healthTrackingService?.disconnectService()
        } catch (_: Throwable) {
        }
        isConnected = false
    }

    private fun buildSafeFailureReason(exception: HealthTrackerException): String {
        val code = try {
            exception.errorCode
        } catch (_: Throwable) {
            -1
        }
        return when (code) {
            HealthTrackerException.PACKAGE_NOT_INSTALLED -> "PACKAGE_NOT_INSTALLED"
            HealthTrackerException.OLD_PLATFORM_VERSION -> "OLD_PLATFORM_VERSION"
            else -> "errorCode=$code"
        }
    }

    companion object {
        private const val TAG = "SamsungHealthSensor"
    }
}
