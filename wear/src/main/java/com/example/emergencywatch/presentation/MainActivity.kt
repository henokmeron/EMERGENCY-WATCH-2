package com.example.emergencywatch.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.AppScaffold
import com.example.emergencywatch.presentation.screens.MonitoringScreen
import com.example.emergencywatch.presentation.screens.PairingScreen
import com.example.emergencywatch.presentation.screens.RevokedScreen
import com.example.emergencywatch.presentation.screens.SettingsScreen
import com.example.emergencywatch.presentation.screens.SosScreen
import com.example.emergencywatch.presentation.screens.TestTimelineScreen
import com.example.emergencywatch.presentation.screens.WelcomeScreen
import com.example.emergencywatch.presentation.theme.EmergencyWatchTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val body = grants[Manifest.permission.BODY_SENSORS]
            ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                == PackageManager.PERMISSION_GRANTED)
        val activity = grants[Manifest.permission.ACTIVITY_RECOGNITION]
            ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED)
        Log.i(
            "EWPipeline",
            "Permission result BODY_SENSORS=$body ACTIVITY_RECOGNITION=$activity " +
                "BODY_SENSORS_BACKGROUND=${
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS_BACKGROUND) ==
                        PackageManager.PERMISSION_GRANTED
                }"
        )
        if (body) {
            viewModel.checkAuthState()
            maybeRequestBackgroundBodySensors()
        } else {
            Log.e("EWPipeline", "BODY_SENSORS denied — telemetry cannot collect heart rate")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            EmergencyWatchTheme {
                AppScaffold {
                    val state by viewModel.uiState.collectAsState()
                    WearAppContent(
                        state = state,
                        onStartPairing = { viewModel.navigateTo(Screen.PAIRING) },
                        onCodeChange = { viewModel.updatePairingCode(it) },
                        onSubmitPairing = { viewModel.submitPairing() },
                        onPairingBack = { viewModel.navigateTo(Screen.WELCOME) },
                        onTriggerSos = { viewModel.triggerSosEvent() },
                        onCancelFall = { viewModel.cancelFallEvent() },
                        onOpenSettings = { viewModel.navigateTo(Screen.SETTINGS) },
                        onForceSync = { viewModel.forceSyncTelemetry() },
                        onOpenTestTimeline = { viewModel.navigateTo(Screen.TEST_TIMELINE) },
                        onRunTestScenario = { viewModel.runTestScenario(it) },
                        onTestTimelineBack = { viewModel.navigateTo(Screen.SETTINGS) },
                        onDePair = { viewModel.dePairWatch() },
                        onSettingsBack = { viewModel.navigateTo(Screen.MONITORING) },
                        onResetAndPair = { viewModel.dePairWatch() }
                    )
                }
            }
        }

        ensurePermissionsThenStart()
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.checkAuthState()
        }
    }

    private fun ensurePermissionsThenStart() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.BODY_SENSORS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        if (Build.VERSION.SDK_INT >= 36) {
            val readHr = "android.permission.health.READ_HEART_RATE"
            if (ContextCompat.checkSelfPermission(this, readHr)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(readHr)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.i("EWPipeline", "Requesting runtime permissions: $permissionsToRequest")
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.i("EWPipeline", "Runtime permissions already granted — starting auth/sensor pipeline")
            viewModel.checkAuthState()
            maybeRequestBackgroundBodySensors()
        }
    }

    private fun maybeRequestBackgroundBodySensors() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS_BACKGROUND)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Log.w(
            "EWPipeline",
            "Requesting BODY_SENSORS_BACKGROUND for continuous watch HR while monitoring"
        )
        permissionLauncher.launch(arrayOf(Manifest.permission.BODY_SENSORS_BACKGROUND))
    }
}

@Composable
fun WearAppContent(
    state: UiState,
    onStartPairing: () -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmitPairing: () -> Unit,
    onPairingBack: () -> Unit,
    onTriggerSos: () -> Unit,
    onCancelFall: () -> Unit,
    onOpenSettings: () -> Unit,
    onForceSync: () -> Unit,
    onOpenTestTimeline: () -> Unit,
    onRunTestScenario: (String) -> Unit,
    onTestTimelineBack: () -> Unit,
    onDePair: () -> Unit,
    onSettingsBack: () -> Unit,
    onResetAndPair: () -> Unit
) {
    when (state.currentScreen) {
        Screen.WELCOME -> {
            WelcomeScreen(onStartPairing = onStartPairing)
        }
        Screen.PAIRING -> {
            PairingScreen(
                code = state.pairingCode,
                isPairing = state.isPairing,
                errorMessage = state.pairingError,
                onCodeChange = onCodeChange,
                onSubmit = onSubmitPairing,
                onBack = onPairingBack
            )
        }
        Screen.MONITORING -> {
            MonitoringScreen(
                heartRate = state.heartRate,
                contactStatus = state.contactStatus,
                batteryLevel = state.batteryLevel,
                queuedCount = state.queuedCount,
                statusMessage = state.statusMessage,
                isPhysicalWatch = state.isPhysicalWatch,
                sensorSourceLabel = state.sensorSourceLabel,
                patientName = state.patientName,
                onTriggerSos = onTriggerSos,
                onOpenSettings = onOpenSettings
            )
        }
        Screen.SOS -> {
            SosScreen(
                onConfirmSos = onTriggerSos,
                onCancel = onCancelFall
            )
        }
        Screen.SETTINGS -> {
            SettingsScreen(
                isPhysicalWatch = state.isPhysicalWatch,
                patientName = state.patientName,
                patientId = state.patientId ?: state.watchConfig?.patientId,
                deviceId = state.deviceId,
                heartRate = state.heartRate,
                contactStatus = state.contactStatus,
                batteryLevel = state.batteryLevel,
                sensorSourceLabel = state.sensorSourceLabel,
                lastUpload = state.lastUploadTime,
                isSyncing = state.isSyncing,
                onForceSync = onForceSync,
                onOpenTestTimeline = onOpenTestTimeline,
                onDePair = onDePair,
                onBack = onSettingsBack
            )
        }
        Screen.TEST_TIMELINE -> {
            TestTimelineScreen(
                events = state.testEvents,
                onRunTestScenario = onRunTestScenario,
                onBack = onTestTimelineBack
            )
        }
        Screen.REVOKED -> {
            RevokedScreen(
                onResetAndPair = onResetAndPair
            )
        }
    }
}
