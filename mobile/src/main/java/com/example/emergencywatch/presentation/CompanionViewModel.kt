package com.example.emergencywatch.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.emergencywatch.companion.DataLayerRepository
import com.example.emergencywatch.companion.PairAssistResult
import com.example.emergencywatch.companion.WatchStatusSnapshot
import com.example.emergencywatch.data.remote.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CompanionScreen {
    DASHBOARD,
    PAIRING,
    SETTINGS
}

data class CompanionUiState(
    val screen: CompanionScreen = CompanionScreen.DASHBOARD,
    val watch: WatchStatusSnapshot = WatchStatusSnapshot(),
    val backendReachable: Boolean? = null,
    val isRefreshing: Boolean = false,
    val pairingCode: String = "",
    val isPairing: Boolean = false,
    val pairResult: PairAssistResult? = null,
    val bannerMessage: String? = null
)

class CompanionViewModel(application: Application) : AndroidViewModel(application) {

    private val dataLayer = DataLayerRepository(application)

    private val _uiState = MutableStateFlow(CompanionUiState())
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        dataLayer.start()
        viewModelScope.launch {
            dataLayer.watchStatus.collect { status ->
                _uiState.update { it.copy(watch = status) }
            }
        }
        viewModelScope.launch {
            dataLayer.pairResult.collect { result ->
                _uiState.update { it.copy(pairResult = result, isPairing = false) }
                if (result?.ok == true) {
                    _uiState.update {
                        it.copy(
                            bannerMessage = "Watch paired successfully",
                            screen = CompanionScreen.DASHBOARD,
                            pairingCode = ""
                        )
                    }
                    refreshAll()
                }
            }
        }
        refreshAll()
        startPolling()
    }

    fun navigate(screen: CompanionScreen) {
        _uiState.update { it.copy(screen = screen) }
        if (screen != CompanionScreen.PAIRING) {
            dataLayer.clearPairResult()
            _uiState.update { it.copy(pairResult = null) }
        }
    }

    fun updatePairingCode(code: String) {
        val cleaned = code.filter { it.isLetterOrDigit() }.take(8).uppercase()
        _uiState.update { it.copy(pairingCode = cleaned) }
    }

    fun submitPairingAssist() {
        val code = _uiState.value.pairingCode
        if (code.length < 6) {
            _uiState.update { it.copy(bannerMessage = "Enter the pairing code (6–8 characters)") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isPairing = true, pairResult = null) }
            dataLayer.clearPairResult()
            val sent = dataLayer.sendPairCodeToWatch(code)
            if (!sent) {
                _uiState.update {
                    it.copy(
                        isPairing = false,
                        bannerMessage = "No watch connected. Open the watch app and keep Bluetooth on."
                    )
                }
            } else {
                _uiState.update { it.copy(bannerMessage = "Pairing code sent to watch…") }
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            dataLayer.refreshConnectedNodes()
            dataLayer.requestStatusRefresh()
            val reachable = withContext(Dispatchers.IO) { NetworkClient.probeBackendReachable() }
            _uiState.update { it.copy(isRefreshing = false, backendReachable = reachable) }
        }
    }

    fun requestForceSync() {
        viewModelScope.launch {
            val ok = dataLayer.requestForceSync()
            _uiState.update {
                it.copy(bannerMessage = if (ok) "Sync requested on watch" else "Watch not connected")
            }
        }
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                dataLayer.refreshConnectedNodes()
                val reachable = withContext(Dispatchers.IO) { NetworkClient.probeBackendReachable() }
                _uiState.update { it.copy(backendReachable = reachable) }
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        dataLayer.stop()
        super.onCleared()
    }
}
