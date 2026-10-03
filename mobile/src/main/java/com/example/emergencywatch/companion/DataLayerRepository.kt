package com.example.emergencywatch.companion

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Phone ↔ watch communication via the official Wearable Data Layer APIs.
 */
class DataLayerRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dataClient: DataClient = Wearable.getDataClient(appContext)
    private val messageClient: MessageClient = Wearable.getMessageClient(appContext)
    private val nodeClient: NodeClient = Wearable.getNodeClient(appContext)

    private val _watchStatus = MutableStateFlow(WatchStatusSnapshot())
    val watchStatus: StateFlow<WatchStatusSnapshot> = _watchStatus.asStateFlow()

    private val _pairResult = MutableStateFlow<PairAssistResult?>(null)
    val pairResult: StateFlow<PairAssistResult?> = _pairResult.asStateFlow()

    private val messageListener = MessageClient.OnMessageReceivedListener { event ->
        when (event.path) {
            CompanionPaths.PAIR_RESULT_PATH -> handlePairResult(event)
        }
    }

    private val dataListener = DataClient.OnDataChangedListener { buffer ->
        buffer.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == CompanionPaths.STATUS_PATH
            ) {
                applyStatus(DataMapItem.fromDataItem(event.dataItem).dataMap, connected = true)
            }
        }
    }

    fun start() {
        dataClient.addListener(dataListener)
        messageClient.addListener(messageListener)
    }

    fun stop() {
        dataClient.removeListener(dataListener)
        messageClient.removeListener(messageListener)
    }

    suspend fun refreshConnectedNodes(): List<Node> = withContext(Dispatchers.IO) {
        try {
            val nodes = nodeClient.connectedNodes.await()
            val primary = nodes.firstOrNull()
            _watchStatus.update {
                it.copy(
                    connected = nodes.isNotEmpty(),
                    nodeId = primary?.id,
                    nodeDisplayName = primary?.displayName
                )
            }
            if (nodes.isEmpty()) {
                _watchStatus.update {
                    it.copy(
                        connected = false,
                        heartRate = null,
                        batteryLevel = null,
                        sosActive = false
                    )
                }
            } else {
                // Pull any existing status DataItem
                readExistingStatus()
            }
            nodes
        } catch (t: Throwable) {
            Log.w(TAG, "Node refresh failed: ${t.javaClass.simpleName}")
            emptyList()
        }
    }

    private suspend fun readExistingStatus() {
        try {
            val items = dataClient.dataItems.await()
            items.forEach { item ->
                if (item.uri.path == CompanionPaths.STATUS_PATH) {
                    applyStatus(DataMapItem.fromDataItem(item).dataMap, connected = true)
                }
            }
            items.release()
        } catch (t: Throwable) {
            Log.w(TAG, "Status read failed: ${t.javaClass.simpleName}")
        }
    }

    suspend fun requestStatusRefresh() {
        sendToAllWatches(CompanionPaths.STATUS_REQUEST_PATH, ByteArray(0))
    }

    suspend fun requestForceSync(): Boolean =
        sendToAllWatches(CompanionPaths.FORCE_SYNC_PATH, ByteArray(0))

    suspend fun sendPairCodeToWatch(code: String): Boolean {
        _pairResult.value = null
        val payload = JSONObject()
            .put(CompanionPaths.KEY_PAIR_CODE, code.trim())
            .toString()
            .toByteArray(Charsets.UTF_8)
        return sendToAllWatches(CompanionPaths.PAIR_REQUEST_PATH, payload)
    }

    fun clearPairResult() {
        _pairResult.value = null
    }

    private suspend fun sendToAllWatches(path: String, data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) return@withContext false
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, path, data).await()
                }
                true
            } catch (t: Throwable) {
                Log.w(TAG, "sendMessage($path) failed: ${t.javaClass.simpleName}")
                false
            }
        }

    private fun handlePairResult(event: MessageEvent) {
        try {
            val json = JSONObject(String(event.data, Charsets.UTF_8))
            _pairResult.value = PairAssistResult(
                ok = json.optBoolean(CompanionPaths.KEY_PAIR_OK, false),
                message = json.optString(CompanionPaths.KEY_PAIR_MESSAGE, ""),
                deviceId = json.optString(CompanionPaths.KEY_DEVICE_ID, "").ifBlank { null },
                patientId = json.optString(CompanionPaths.KEY_PATIENT_ID, "").ifBlank { null }
            )
        } catch (t: Throwable) {
            _pairResult.value = PairAssistResult(ok = false, message = "Invalid pair result")
        }
    }

    private fun applyStatus(map: com.google.android.gms.wearable.DataMap, connected: Boolean) {
        val hr = if (map.containsKey(CompanionPaths.KEY_HEART_RATE)) {
            map.getInt(CompanionPaths.KEY_HEART_RATE)
        } else null
        _watchStatus.update { current ->
            current.copy(
                connected = connected || current.connected,
                isPaired = map.getBoolean(CompanionPaths.KEY_PAIRED, false),
                batteryLevel = map.getInt(CompanionPaths.KEY_BATTERY, current.batteryLevel ?: -1)
                    .takeIf { it >= 0 },
                heartRate = hr,
                contactStatus = map.getBoolean(CompanionPaths.KEY_CONTACT, true),
                queuedReadings = map.getInt(CompanionPaths.KEY_QUEUED, 0),
                lastUploadTime = map.getString(CompanionPaths.KEY_LAST_UPLOAD)?.ifBlank { null },
                sensorSource = map.getString(CompanionPaths.KEY_SENSOR_SOURCE)?.ifBlank { null },
                capabilitiesJson = map.getString(CompanionPaths.KEY_CAPABILITIES)?.ifBlank { null },
                currentScreen = map.getString(CompanionPaths.KEY_SCREEN)?.ifBlank { null },
                sosActive = map.getBoolean(CompanionPaths.KEY_SOS_ACTIVE, false),
                statusMessage = map.getString(CompanionPaths.KEY_STATUS_MESSAGE)?.ifBlank { null },
                patientId = map.getString(CompanionPaths.KEY_PATIENT_ID)?.ifBlank { null },
                deviceId = map.getString(CompanionPaths.KEY_DEVICE_ID)?.ifBlank { null },
                patientName = map.getString(CompanionPaths.KEY_PATIENT_NAME)?.ifBlank { null },
                cloudOk = map.getBoolean(CompanionPaths.KEY_CLOUD_OK, false),
                updatedAtEpochMs = map.getLong(CompanionPaths.KEY_UPDATED_AT, 0L),
                appVersion = map.getString(CompanionPaths.KEY_APP_VERSION)?.ifBlank { null }
            )
        }
    }

    companion object {
        private const val TAG = "DataLayerRepo"
    }
}

data class PairAssistResult(
    val ok: Boolean,
    val message: String,
    val deviceId: String? = null,
    val patientId: String? = null
)
