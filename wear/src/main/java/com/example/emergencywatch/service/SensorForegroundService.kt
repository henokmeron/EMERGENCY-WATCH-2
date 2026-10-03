package com.example.emergencywatch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.emergencywatch.R
import com.example.emergencywatch.data.config.WatchConfigStore
import com.example.emergencywatch.data.local.AppDatabase
import com.example.emergencywatch.data.remote.NetworkClient
import com.example.emergencywatch.data.repository.AuthRepository
import com.example.emergencywatch.data.repository.TelemetryRepository
import com.example.emergencywatch.data.repository.TelemetrySyncResult
import com.example.emergencywatch.data.security.SecureAuthManager
import com.example.emergencywatch.domain.sensor.SensorData
import com.example.emergencywatch.domain.sensor.SensorManagerFacade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SensorForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var sensorFacade: SensorManagerFacade
    private lateinit var telemetryRepo: TelemetryRepository
    private lateinit var authManager: SecureAuthManager
    private lateinit var configStore: WatchConfigStore

    private var latestSensorData: SensorData? = null
    @Volatile private var monitoringStarted = false
    private var sensorCallbackCount = 0L
    private var roomInsertCount = 0L

    override fun onCreate() {
        super.onCreate()
        authManager = SecureAuthManager(applicationContext)
        configStore = WatchConfigStore(applicationContext)
        val apiService = NetworkClient.createWatchApiService(authManager)
        val authRepo = AuthRepository(apiService, authManager)
        val db = AppDatabase.getInstance(applicationContext)
        telemetryRepo = TelemetryRepository(apiService, db.telemetryDao(), authRepo, configStore)

        sensorFacade = SensorManagerFacade(applicationContext)

        Log.i(
            TAG,
            "onCreate paired=${authManager.isPaired()} tokenPresent=${!authManager.getDeviceToken().isNullOrBlank()} " +
                "sample=${configStore.sampleIntervalSeconds()}s upload=${configStore.uploadIntervalSeconds()}s"
        )
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Emergency Monitoring Active")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!monitoringStarted) {
            monitoringStarted = true
            startMonitoringLoop()
        }

        return START_STICKY
    }

    private fun startMonitoringLoop() {
        Log.i(TAG, "startMonitoringLoop source=${sensorFacade.getActiveSource()}")

        // Refresh operational intervals from backend when the service starts
        serviceScope.launch {
            if (authManager.isPaired()) {
                val config = telemetryRepo.fetchConfig()
                Log.i(
                    TAG,
                    "fetchConfig result=${config != null} sample=${configStore.sampleIntervalSeconds()}s " +
                        "upload=${configStore.uploadIntervalSeconds()}s patient=${config?.patientId}"
                )
            } else {
                Log.e(TAG, "Not paired — sensors may run but uploads will be skipped")
            }
        }

        // Raw sensor callbacks stay continuous (fall detection uses the raw stream).
        // TelemetryRepository.recordReading applies the sample-interval gate.
        sensorFacade.startTracking { sensorData ->
            latestSensorData = sensorData
            sensorCallbackCount++
            if (sensorCallbackCount == 1L || sensorCallbackCount % 50L == 0L) {
                Log.i(
                    TAG,
                    "sensorCallback#$sensorCallbackCount hr=${sensorData.heartRate} " +
                        "contact=${sensorData.contactStatus} movement=${"%.3f".format(sensorData.movement)} " +
                        "source=${sensorFacade.getActiveSource()}"
                )
            }
            serviceScope.launch {
                val inserted = telemetryRepo.recordReading(sensorData)
                if (inserted) {
                    roomInsertCount++
                    Log.i(
                        TAG,
                        "Room insert #$roomInsertCount hr=${sensorData.heartRate} " +
                            "at=${sensorData.recordedAt} queued=${telemetryRepo.getUnsyncedCount()}"
                    )
                }
            }
        }

        // Upload cadence follows upload_interval_seconds (default 60s)
        serviceScope.launch {
            // First sync soon after start so we can verify HTTP without waiting a full minute
            delay(15_000L)
            while (true) {
                if (authManager.isPaired()) {
                    val result = telemetryRepo.syncTelemetryBatch()
                    when (result) {
                        is TelemetrySyncResult.Success ->
                            Log.i(TAG, "sync OK accepted=${result.acceptedCount} remaining=${result.remainingQueued}")
                        is TelemetrySyncResult.Empty ->
                            Log.w(TAG, "sync EMPTY — Room has no unsynced readings")
                        is TelemetrySyncResult.Error ->
                            Log.e(TAG, "sync FAIL code=${result.code} message=${result.message}")
                    }
                } else {
                    Log.e(TAG, "sync skipped — not paired")
                }
                delay(configStore.uploadIntervalMs())
            }
        }

        // WorkManager periodic work runs at most every 15 min, so battery on the dashboard
        // went stale. Send heartbeat from the running service on its own cadence.
        serviceScope.launch {
            delay(5_000L)
            while (true) {
                if (authManager.isPaired()) {
                    val battery = HeartbeatWorker.readBatteryLevel(applicationContext)
                    val sensors = HeartbeatWorker.tagSensors(sensorFacade.getCapabilities())
                    val config = telemetryRepo.sendHeartbeat(battery, sensors)
                    Log.i(TAG, "heartbeat battery=$battery% ok=${config != null}")
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        sensorFacade.stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Emergency Watch Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows continuous emergency health sensor monitoring status"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Emergency Watch")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.splash_icon)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "EWPipeline"
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "emergency_watch_monitoring_channel"

        fun startService(context: Context) {
            val intent = Intent(context, SensorForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SensorForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
