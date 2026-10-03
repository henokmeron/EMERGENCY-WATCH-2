package com.example.emergencywatch.service

import android.content.Context
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.emergencywatch.data.config.WatchConfigStore
import com.example.emergencywatch.data.local.AppDatabase
import com.example.emergencywatch.data.remote.NetworkClient
import com.example.emergencywatch.data.repository.AuthRepository
import com.example.emergencywatch.data.repository.TelemetryRepository
import com.example.emergencywatch.data.security.SecureAuthManager
import com.example.emergencywatch.domain.sensor.SensorManagerFacade
import java.util.concurrent.TimeUnit

class HeartbeatWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val authManager = SecureAuthManager(applicationContext)
        if (!authManager.isPaired()) {
            return Result.success()
        }

        val configStore = WatchConfigStore(applicationContext)
        val apiService = NetworkClient.createWatchApiService(authManager)
        val authRepo = AuthRepository(apiService, authManager)
        val db = AppDatabase.getInstance(applicationContext)
        val telemetryRepo = TelemetryRepository(apiService, db.telemetryDao(), authRepo, configStore)

        val batteryLevel = readBatteryLevel(applicationContext)
        val sensorCaps = tagSensors(SensorManagerFacade(applicationContext).getCapabilities())

        telemetryRepo.sendHeartbeat(batteryLevel, sensorCaps)

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "EmergencyWatchHeartbeatWorker"

        fun readBatteryLevel(context: Context): Int {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        }

        /** Adds physical/emulator flags to the existing HeartbeatRequest.sensors map. */
        fun tagSensors(caps: Map<String, Boolean>): Map<String, Boolean> {
            val isPhysical = !android.os.Build.FINGERPRINT.contains("generic", ignoreCase = true) &&
                !android.os.Build.MODEL.contains("sdk", ignoreCase = true)
            return caps + mapOf(
                "physical_watch" to isPhysical,
                "dev_build" to true,
                "emulator" to !isPhysical
            )
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(300, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
