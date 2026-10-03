package com.example.emergencywatch.presentation

import android.app.Application
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.emergencywatch.companion.CompanionStatusPublisher
import com.example.emergencywatch.companion.WatchCompanionStatus
import com.example.emergencywatch.data.config.WatchConfigStore
import com.example.emergencywatch.data.local.AppDatabase
import com.example.emergencywatch.data.local.TestEventEntity
import com.example.emergencywatch.data.remote.NetworkClient
import com.example.emergencywatch.data.remote.model.WatchConfig
import com.example.emergencywatch.data.repository.AuthRepository
import com.example.emergencywatch.data.repository.PairResult
import com.example.emergencywatch.data.repository.TelemetryRepository
import com.example.emergencywatch.data.repository.TelemetrySyncResult
import com.example.emergencywatch.data.security.SecureAuthManager
import com.example.emergencywatch.domain.sensor.SensorData
import com.example.emergencywatch.domain.sensor.SensorManagerFacade
import com.example.emergencywatch.domain.testing.TestScenarioRecorder
import com.example.emergencywatch.service.HeartbeatWorker
import com.example.emergencywatch.service.SensorForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class Screen {
    WELCOME,
    PAIRING,
    MONITORING,
    SOS,
    SETTINGS,
    TEST_TIMELINE,
    REVOKED
}

data class UiState(
    val isPaired: Boolean = false,
    val currentScreen: Screen = Screen.WELCOME,
    val pairingCode: String = "",
    val isPairing: Boolean = false,
    val pairingError: String? = null,
    val heartRate: Int? = null,
    val contactStatus: Boolean = true,
    val batteryLevel: Int = 100,
    val queuedCount: Int = 0,
    val lastUploadTime: String? = null,
    val sensorCapabilities: Map<String, Boolean> = emptyMap(),
    val sensorSourceLabel: String = "Starting…",
    val deviceId: String? = null,
    val patientId: String? = null,
    val patientName: String? = null,
    val watchConfig: WatchConfig? = null,
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    val sosCountdownSeconds: Int = 5,
    val testEvents: List<TestEventEntity> = emptyList(),
    /** True when this build is running on a real Galaxy Watch (not emulator demo). */
    val isPhysicalWatch: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = SecureAuthManager(application)
    private val configStore = WatchConfigStore(application)
    private val apiService = NetworkClient.createWatchApiService(authManager)
    private val authRepository = AuthRepository(apiService, authManager)
    private val db = AppDatabase.getInstance(application)
    private val telemetryRepository = TelemetryRepository(
        apiService,
        db.telemetryDao(),
        authRepository,
        configStore
    )
    private val sensorFacade = SensorManagerFacade(application)
    private val testScenarioRecorder = TestScenarioRecorder(db.testEventDao())
    private val companionPublisher = CompanionStatusPublisher(application)
    @Volatile private var lastCloudOk: Boolean = false

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                loadBatteryLevel()
                delay(30_000L)
            }
        }
        viewModelScope.launch {
            db.testEventDao().observeRecentEvents().collectLatest { events ->
                _uiState.update { it.copy(testEvents = events) }
            }
        }
        _uiState.update {
            it.copy(
                isPaired = authRepository.isPaired(),
                currentScreen = if (authRepository.isPaired()) Screen.MONITORING else Screen.WELCOME,
                sensorCapabilities = sensorFacade.getCapabilities(),
                sensorSourceLabel = shortSensorLabel(sensorFacade.getActiveSource()),
                deviceId = authManager.getDeviceId(),
                patientId = authManager.getPatientId(),
                isPhysicalWatch = isPhysicalDevice()
            )
        }
        viewModelScope.launch {
            _uiState
                .map { it.toCompanionSnapshot() }
                .distinctUntilChanged()
                .collectLatest { snapshot ->
                    companionPublisher.publish(snapshot)
                }
        }
    }

    fun checkAuthState() {
        val paired = authRepository.isPaired()
        Log.i(
            "EWPipeline",
            "checkAuthState paired=$paired deviceId=${authManager.getDeviceId()} " +
                "patientId=${authManager.getPatientId()} tokenPresent=${!authManager.getDeviceToken().isNullOrBlank()} " +
                "physical=${isPhysicalDevice()} source=${sensorFacade.getActiveSource()}"
        )
        if (paired) {
            _uiState.update {
                it.copy(
                    isPaired = true,
                    currentScreen = Screen.MONITORING,
                    deviceId = authManager.getDeviceId(),
                    patientId = authManager.getPatientId(),
                    sensorSourceLabel = shortSensorLabel(sensorFacade.getActiveSource()),
                    isPhysicalWatch = isPhysicalDevice()
                )
            }
            SensorForegroundService.startService(getApplication())
            HeartbeatWorker.schedule(getApplication())
            startSensorCollection()
            refreshConfig()
        } else {
            _uiState.update { it.copy(isPaired = false, currentScreen = Screen.WELCOME) }
        }
    }

    fun navigateTo(screen: Screen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun updatePairingCode(code: String) {
        _uiState.update { it.copy(pairingCode = code, pairingError = null) }
    }

    fun submitPairing() {
        val code = _uiState.value.pairingCode
        if (code.isBlank()) {
            _uiState.update { it.copy(pairingError = "Please enter an 8-character pairing code") }
            return
        }

        _uiState.update { it.copy(isPairing = true, pairingError = null) }

        viewModelScope.launch {
            when (val result = authRepository.pairWatch(code)) {
                is PairResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isPairing = false,
                            isPaired = true,
                            currentScreen = Screen.MONITORING,
                            pairingCode = "",
                            pairingError = null
                        )
                    }
                    SensorForegroundService.startService(getApplication())
                    HeartbeatWorker.schedule(getApplication())
                    startSensorCollection()
                    refreshConfig()
                }
                is PairResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isPairing = false,
                            pairingError = result.message
                        )
                    }
                }
            }
        }
    }

    private fun startSensorCollection() {
        sensorFacade.startTracking { data ->
            _uiState.update {
                it.copy(
                    heartRate = data.heartRate,
                    contactStatus = data.contactStatus,
                    sensorSourceLabel = shortSensorLabel(sensorFacade.getActiveSource()),
                    sensorCapabilities = sensorFacade.getCapabilities()
                )
            }
        }
    }

    fun triggerSosEvent() {
        viewModelScope.launch {
            val currentData = SensorData(
                heartRate = _uiState.value.heartRate,
                contactStatus = _uiState.value.contactStatus
            )
            telemetryRepository.sendEvent("sos", currentData)
            testScenarioRecorder.recordTestEvent(
                scenarioName = "Manual SOS Test",
                sensorData = currentData,
                batteryLevel = _uiState.value.batteryLevel,
                userAction = "CONFIRMED",
                finalState = "SOS_DISPATCHED"
            )
            _uiState.update { it.copy(statusMessage = "SOS alert transmitted!") }
        }
    }

    fun cancelFallEvent() {
        viewModelScope.launch {
            val currentData = SensorData(
                heartRate = _uiState.value.heartRate,
                contactStatus = _uiState.value.contactStatus
            )
            telemetryRepository.sendEvent("fall_cancelled", currentData)
            testScenarioRecorder.recordTestEvent(
                scenarioName = "Alert Cancellation",
                sensorData = currentData,
                batteryLevel = _uiState.value.batteryLevel,
                userAction = "CANCELLED",
                finalState = "ALERT_CANCELLED"
            )
            _uiState.update { it.copy(currentScreen = Screen.MONITORING, statusMessage = "Fall alert cancelled") }
        }
    }

    fun runTestScenario(scenarioName: String) {
        viewModelScope.launch {
            val currentData = SensorData(
                heartRate = _uiState.value.heartRate,
                contactStatus = _uiState.value.contactStatus
            )
            val action = when {
                scenarioName.contains("SOS", ignoreCase = true) -> "CONFIRMED"
                scenarioName.contains("Cancellation", ignoreCase = true) -> "CANCELLED"
                else -> "NONE"
            }
            val finalState = when {
                scenarioName.contains("SOS", ignoreCase = true) -> "SOS_SENT"
                scenarioName.contains("Cancellation", ignoreCase = true) -> "CANCELLED"
                scenarioName.contains("Fall", ignoreCase = true) -> "ALERT_LOGGED"
                else -> "NORMAL"
            }
            testScenarioRecorder.recordTestEvent(
                scenarioName = scenarioName,
                sensorData = currentData,
                batteryLevel = _uiState.value.batteryLevel,
                userAction = action,
                finalState = finalState
            )
            if (scenarioName.contains("SOS", ignoreCase = true)) {
                telemetryRepository.sendEvent("sos", currentData)
            } else if (scenarioName.contains("Fall", ignoreCase = true) && !scenarioName.contains("Cancellation", ignoreCase = true)) {
                telemetryRepository.sendEvent("fall", currentData)
            }
            _uiState.update { it.copy(statusMessage = "Logged test: $scenarioName") }
        }
    }

    fun forceSyncTelemetry() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            when (val result = telemetryRepository.syncTelemetryBatch()) {
                is TelemetrySyncResult.Success -> {
                    val timestamp = SensorData.getCurrentIsoTimestamp()
                    lastCloudOk = true
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            queuedCount = result.remainingQueued,
                            lastUploadTime = timestamp,
                            statusMessage = "Synced ${result.acceptedCount} readings"
                        )
                    }
                }
                is TelemetrySyncResult.Empty -> {
                    lastCloudOk = true
                    _uiState.update { it.copy(isSyncing = false, statusMessage = "No readings queued") }
                }
                is TelemetrySyncResult.Error -> {
                    lastCloudOk = false
                    if (result.code == "revoked") {
                        _uiState.update { it.copy(isSyncing = false, isPaired = false, currentScreen = Screen.REVOKED) }
                    } else {
                        _uiState.update { it.copy(isSyncing = false, statusMessage = result.message) }
                    }
                }
            }
        }
    }

    fun dePairWatch() {
        authRepository.handleRevocation()
        SensorForegroundService.stopService(getApplication())
        HeartbeatWorker.cancel(getApplication())
        sensorFacade.stopTracking()
        _uiState.update {
            it.copy(
                isPaired = false,
                currentScreen = Screen.WELCOME,
                pairingCode = "",
                pairingError = null
            )
        }
    }

    private fun refreshConfig() {
        viewModelScope.launch {
            val config = telemetryRepository.fetchConfig()
            if (config != null) {
                _uiState.update {
                    it.copy(
                        watchConfig = config,
                        patientId = config.patientId ?: authManager.getPatientId(),
                        patientName = config.patientName,
                        deviceId = authManager.getDeviceId()
                    )
                }
            }
        }
    }

    private fun loadBatteryLevel() {
        val bm = getApplication<Application>().getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        _uiState.update { it.copy(batteryLevel = level) }
    }

    private fun UiState.toCompanionSnapshot(): WatchCompanionStatus {
        val caps = JSONObject()
        sensorCapabilities.forEach { (k, v) -> caps.put(k, v) }
        caps.put("physical_watch", isPhysicalWatch)
        caps.put("dev_build", true)
        return WatchCompanionStatus(
            isPaired = isPaired,
            batteryLevel = batteryLevel,
            heartRate = heartRate,
            contactStatus = contactStatus,
            queuedCount = queuedCount,
            lastUploadTime = lastUploadTime,
            sensorSource = sensorFacade.getActiveSource(),
            capabilitiesJson = caps.toString(),
            currentScreen = currentScreen.name,
            sosActive = currentScreen == Screen.SOS,
            statusMessage = statusMessage,
            patientId = patientId ?: watchConfig?.patientId ?: authManager.getPatientId(),
            deviceId = deviceId ?: authManager.getDeviceId(),
            patientName = patientName ?: watchConfig?.patientName,
            cloudOk = lastCloudOk && isPaired
        )
    }

    companion object {
        fun isPhysicalDevice(): Boolean {
            return !Build.FINGERPRINT.contains("generic", ignoreCase = true) &&
                !Build.MODEL.contains("sdk", ignoreCase = true) &&
                !Build.MODEL.contains("Emulator", ignoreCase = true)
        }

        fun shortSensorLabel(full: String): String {
            return when {
                full.contains("Samsung", ignoreCase = true) -> "Samsung SDK"
                full.contains("Android", ignoreCase = true) -> "Android fallback"
                else -> full.take(18)
            }
        }
    }
}
