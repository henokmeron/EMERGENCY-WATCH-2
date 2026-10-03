package com.example.emergencywatch.companion

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Publishes a non-secret watch status snapshot to the phone via the Data Layer.
 * Does not include device tokens.
 */
class CompanionStatusPublisher(context: Context) {

    private val dataClient = Wearable.getDataClient(context.applicationContext)

    suspend fun publish(status: WatchCompanionStatus) = withContext(Dispatchers.IO) {
        try {
            val request = PutDataMapRequest.create(CompanionPaths.STATUS_PATH).apply {
                dataMap.putBoolean(CompanionPaths.KEY_PAIRED, status.isPaired)
                dataMap.putInt(CompanionPaths.KEY_BATTERY, status.batteryLevel)
                if (status.heartRate != null) {
                    dataMap.putInt(CompanionPaths.KEY_HEART_RATE, status.heartRate)
                } else {
                    dataMap.remove(CompanionPaths.KEY_HEART_RATE)
                }
                dataMap.putBoolean(CompanionPaths.KEY_CONTACT, status.contactStatus)
                dataMap.putInt(CompanionPaths.KEY_QUEUED, status.queuedCount)
                dataMap.putString(CompanionPaths.KEY_LAST_UPLOAD, status.lastUploadTime.orEmpty())
                dataMap.putString(CompanionPaths.KEY_SENSOR_SOURCE, status.sensorSource)
                dataMap.putString(CompanionPaths.KEY_CAPABILITIES, status.capabilitiesJson)
                dataMap.putString(CompanionPaths.KEY_SCREEN, status.currentScreen)
                dataMap.putBoolean(CompanionPaths.KEY_SOS_ACTIVE, status.sosActive)
                dataMap.putString(CompanionPaths.KEY_STATUS_MESSAGE, status.statusMessage.orEmpty())
                dataMap.putString(CompanionPaths.KEY_PATIENT_ID, status.patientId.orEmpty())
                dataMap.putString(CompanionPaths.KEY_DEVICE_ID, status.deviceId.orEmpty())
                dataMap.putString(CompanionPaths.KEY_PATIENT_NAME, status.patientName.orEmpty())
                dataMap.putBoolean(CompanionPaths.KEY_CLOUD_OK, status.cloudOk)
                dataMap.putLong(CompanionPaths.KEY_UPDATED_AT, status.updatedAtEpochMs)
                dataMap.putString(CompanionPaths.KEY_APP_VERSION, status.appVersion)
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to publish companion status: ${t.javaClass.simpleName}")
        }
    }

    companion object {
        private const val TAG = "CompanionStatusPub"
    }
}

data class WatchCompanionStatus(
    val isPaired: Boolean,
    val batteryLevel: Int,
    val heartRate: Int?,
    val contactStatus: Boolean,
    val queuedCount: Int,
    val lastUploadTime: String?,
    val sensorSource: String,
    val capabilitiesJson: String,
    val currentScreen: String,
    val sosActive: Boolean,
    val statusMessage: String?,
    val patientId: String?,
    val deviceId: String?,
    val patientName: String?,
    val cloudOk: Boolean,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0"
)
