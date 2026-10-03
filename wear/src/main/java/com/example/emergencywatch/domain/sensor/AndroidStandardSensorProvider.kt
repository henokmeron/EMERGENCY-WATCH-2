package com.example.emergencywatch.domain.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.emergencywatch.util.PipelineLog

/**
 * Contact rules (physical Galaxy Watch):
 * 1. TYPE_LOW_LATENCY_OFFBODY_DETECT is authoritative when it has delivered at least one event
 *    (1.0 = on body, 0.0 = off body).
 * 2. Valid PPG heart rate can only *confirm* on-body; it never overrides an off-body reading.
 * 3. If off-body still says ON but no valid HR arrives for [HR_STALE_MS], treat as OFF.
 *    Covers stuck on-body events while the watch sits on a desk/charger.
 * 4. Default is OFF until proven otherwise (never start as Attached).
 */
class AndroidStandardSensorProvider : SensorProvider, SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var heartRateSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var offBodySensor: Sensor? = null

    private val movementProcessor = MovementProcessor()

    private var currentHeartRate: Int? = null
    private var currentMovement: Double = 0.0
    private var currentFallDetected: Boolean = false
    private var currentFallConfidence: Double? = null

    /** Last value from the off-body sensor; null until the first event. */
    private var offBodyOn: Boolean? = null
    private var lastValidHrAtMs: Long = 0L

    private var dataCallback: ((SensorData) -> Unit)? = null

    override fun initialize(context: Context) {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        heartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        offBodySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
            ?: sensorManager?.getSensorList(Sensor.TYPE_ALL)?.firstOrNull { sensor ->
                sensor.stringType.contains("offbody", ignoreCase = true)
            }
    }

    override fun isAvailable(): Boolean {
        return sensorManager != null && (heartRateSensor != null || accelerometerSensor != null)
    }

    override fun getCapabilities(): Map<String, Boolean> {
        return mapOf(
            SensorType.HEART_RATE.keyName to (heartRateSensor != null),
            SensorType.ACCELEROMETER.keyName to (accelerometerSensor != null),
            SensorType.SPO2.keyName to false,
            SensorType.PPG.keyName to false,
            SensorType.SKIN_TEMPERATURE.keyName to false,
            SensorType.ECG.keyName to false,
            SensorType.EDA.keyName to false,
            SensorType.BIA.keyName to false
        )
    }

    override fun startTracking(onDataReceived: (SensorData) -> Unit) {
        dataCallback = onDataReceived
        offBodyOn = null
        lastValidHrAtMs = 0L
        currentHeartRate = null

        heartRateSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometerSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        offBodySensor?.let { sensor ->
            // On-change sensors often deliver the *current* state on register + flush.
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            sensorManager?.flush(this)
            PipelineLog.i("offbody registered type=${sensor.stringType}")
        } ?: PipelineLog.w("offbody sensor missing — contact will require valid HR")
    }

    override fun stopTracking() {
        sensorManager?.unregisterListener(this)
        dataCallback = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                if (event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT) {
                    currentHeartRate = null
                    dispatchData()
                    return
                }
                val hr = event.values.firstOrNull()?.toInt()
                if (hr != null && hr in 1..300) {
                    currentHeartRate = hr
                    lastValidHrAtMs = System.currentTimeMillis()
                    dispatchData()
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                currentMovement = movementProcessor.processAccelerometer(x, y, z)
                val (fall, confidence) = movementProcessor.checkFallImpact(x, y, z)
                currentFallDetected = fall
                currentFallConfidence = confidence

                dispatchData()
            }
            else -> {
                // TYPE_LOW_LATENCY_OFFBODY_DETECT or Samsung string-type offbody sensors
                if (event.sensor.type == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT ||
                    event.sensor.stringType.contains("offbody", ignoreCase = true)
                ) {
                    // Android contract: 1.0f = on body, 0.0f = off body
                    val value = event.values.firstOrNull() ?: 0.0f
                    offBodyOn = value >= 0.5f
                    if (offBodyOn != true) currentHeartRate = null
                    PipelineLog.i("offbody event value=$value contact=${resolveContact()}")
                    dispatchData()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun resolveContact(): Boolean {
        val now = System.currentTimeMillis()
        val recentHr = lastValidHrAtMs > 0L && (now - lastValidHrAtMs) <= HR_STALE_MS

        val offBody = offBodyOn
        if (offBody != null) {
            // Off-body sensor says OFF → always OFF.
            if (!offBody) return false
            // Off-body says ON: require recent HR when the HR sensor exists, otherwise
            // a stuck ON event while the watch sits on a desk stays "Attached" forever.
            if (heartRateSensor != null) {
                if (!recentHr && lastValidHrAtMs == 0L) {
                    // Never got HR since start — do not claim Attached yet.
                    return false
                }
                if (!recentHr) {
                    return false
                }
            }
            return true
        }

        // No off-body events yet: only claim contact when PPG is producing values.
        return recentHr
    }

    private fun dispatchData() {
        val contact = resolveContact()
        val hr = if (contact) currentHeartRate else null
        val data = SensorData(
            heartRate = hr,
            contactStatus = contact,
            spo2 = null,
            movement = currentMovement,
            fallDetected = currentFallDetected,
            fallConfidence = currentFallConfidence
        )
        dataCallback?.invoke(data)

        // Reset one-shot fall flag after emission
        if (currentFallDetected) {
            currentFallDetected = false
            currentFallConfidence = null
        }
    }

    companion object {
        /** If no valid HR for this long, do not report on-wrist (desk / charger / stuck flag). */
        private const val HR_STALE_MS = 45_000L
    }
}
