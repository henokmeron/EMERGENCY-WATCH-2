package com.example.emergencywatch.companion

import android.util.Log
import com.example.emergencywatch.data.remote.NetworkClient
import com.example.emergencywatch.data.repository.AuthRepository
import com.example.emergencywatch.data.repository.PairResult
import com.example.emergencywatch.data.security.SecureAuthManager
import com.example.emergencywatch.service.HeartbeatWorker
import com.example.emergencywatch.service.SensorForegroundService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Minimal Data Layer bridge so the phone companion can request status,
 * forward a pairing code, or ask for a telemetry flush — without changing
 * the watch's sensor/telemetry/backend pipeline.
 */
class CompanionListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            CompanionPaths.STATUS_REQUEST_PATH -> {
                // Status is published by MainViewModel / publisher; nudge a refresh via broadcast-less no-op.
                Log.i(TAG, "Status request received from phone")
            }
            CompanionPaths.PAIR_REQUEST_PATH -> handlePairRequest(messageEvent)
            CompanionPaths.FORCE_SYNC_PATH -> {
                Log.i(TAG, "Force sync request received from phone")
                // Actual sync is driven by the running service / ViewModel; start service if paired.
                val auth = SecureAuthManager(applicationContext)
                if (auth.isPaired()) {
                    SensorForegroundService.startService(applicationContext)
                }
            }
            else -> Log.d(TAG, "Ignored Data Layer path ${messageEvent.path}")
        }
    }

    private fun handlePairRequest(messageEvent: MessageEvent) {
        scope.launch {
            val nodeId = messageEvent.sourceNodeId
            val messageClient = Wearable.getMessageClient(applicationContext)
            try {
                val payload = JSONObject(String(messageEvent.data, Charsets.UTF_8))
                val code = payload.optString(CompanionPaths.KEY_PAIR_CODE, "").trim()
                val authManager = SecureAuthManager(applicationContext)
                val api = NetworkClient.createWatchApiService(authManager)
                val authRepo = AuthRepository(api, authManager)

                val resultJson = JSONObject()
                when (val result = authRepo.pairWatch(code)) {
                    is PairResult.Success -> {
                        SensorForegroundService.startService(applicationContext)
                        HeartbeatWorker.schedule(applicationContext)
                        resultJson.put(CompanionPaths.KEY_PAIR_OK, true)
                        resultJson.put(CompanionPaths.KEY_PAIR_MESSAGE, "Paired")
                        resultJson.put(CompanionPaths.KEY_DEVICE_ID, result.deviceId)
                        resultJson.put(CompanionPaths.KEY_PATIENT_ID, result.patientId)
                    }
                    is PairResult.Error -> {
                        resultJson.put(CompanionPaths.KEY_PAIR_OK, false)
                        resultJson.put(CompanionPaths.KEY_PAIR_MESSAGE, result.message)
                    }
                }
                messageClient.sendMessage(
                    nodeId,
                    CompanionPaths.PAIR_RESULT_PATH,
                    resultJson.toString().toByteArray(Charsets.UTF_8)
                ).await()
            } catch (t: Throwable) {
                Log.e(TAG, "Pair request failed: ${t.javaClass.simpleName}")
                try {
                    val err = JSONObject()
                        .put(CompanionPaths.KEY_PAIR_OK, false)
                        .put(CompanionPaths.KEY_PAIR_MESSAGE, t.localizedMessage ?: "Pair failed")
                        .toString()
                        .toByteArray(Charsets.UTF_8)
                    messageClient.sendMessage(nodeId, CompanionPaths.PAIR_RESULT_PATH, err).await()
                } catch (_: Throwable) {
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CompanionListener"
    }
}
