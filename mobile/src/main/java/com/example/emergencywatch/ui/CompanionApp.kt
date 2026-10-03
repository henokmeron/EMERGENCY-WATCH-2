package com.example.emergencywatch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.emergencywatch.companion.WatchStatusSnapshot
import com.example.emergencywatch.data.remote.NetworkClient
import com.example.emergencywatch.presentation.CompanionScreen
import com.example.emergencywatch.presentation.CompanionUiState
import com.example.emergencywatch.presentation.CompanionViewModel
import com.example.emergencywatch.ui.theme.Danger
import com.example.emergencywatch.ui.theme.OkGreen
import com.example.emergencywatch.ui.theme.TealPrimary
import com.example.emergencywatch.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionApp(viewModel: CompanionViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.bannerMessage) {
        val msg = state.bannerMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.clearBanner()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Emergency Watch", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Phone companion",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    TextButton(onClick = viewModel::refreshAll) {
                        Text(if (state.isRefreshing) "…" else "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.screen == CompanionScreen.DASHBOARD,
                    onClick = { viewModel.navigate(CompanionScreen.DASHBOARD) },
                    icon = { StatusDot(connected = state.watch.connected) },
                    label = { Text("Status") }
                )
                NavigationBarItem(
                    selected = state.screen == CompanionScreen.PAIRING,
                    onClick = { viewModel.navigate(CompanionScreen.PAIRING) },
                    icon = { Text("⎘", style = MaterialTheme.typography.titleMedium) },
                    label = { Text("Pair") }
                )
                NavigationBarItem(
                    selected = state.screen == CompanionScreen.SETTINGS,
                    onClick = { viewModel.navigate(CompanionScreen.SETTINGS) },
                    icon = { Text("⚙", style = MaterialTheme.typography.titleMedium) },
                    label = { Text("Settings") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(padding)
        ) {
            when (state.screen) {
                CompanionScreen.DASHBOARD -> DashboardScreen(state, viewModel)
                CompanionScreen.PAIRING -> PairingAssistScreen(state, viewModel)
                CompanionScreen.SETTINGS -> SettingsScreen(state, viewModel)
            }
        }
    }
}

@Composable
private fun DashboardScreen(state: CompanionUiState, viewModel: CompanionViewModel) {
    val watch = state.watch
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ConnectionHero(watch)

        AnimatedVisibility(visible = !watch.connected) {
            AlertBanner(
                title = "Watch disconnected",
                body = "Bluetooth link to the Galaxy Watch is down. The watch can still upload telemetry on its own Wi‑Fi/LTE when available.",
                color = Warning
            )
        }

        AnimatedVisibility(visible = watch.sosActive) {
            AlertBanner(
                title = "Emergency / SOS active",
                body = watch.statusMessage ?: "Watch is on the SOS screen.",
                color = Danger
            )
        }

        SectionTitle("Live watch")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                label = "Heart rate",
                value = watch.heartRate?.let { "$it" } ?: "--",
                unit = "BPM",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Battery",
                value = watch.batteryLevel?.let { "$it" } ?: "--",
                unit = "%",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                label = "On wrist",
                value = when (watch.contactStatus) {
                    true -> "YES"
                    false -> "NO"
                    null -> "--"
                },
                unit = "",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Queue",
                value = "${watch.queuedReadings}",
                unit = "rdgs",
                modifier = Modifier.weight(1f)
            )
        }

        SectionTitle("Device health")
        StatusRow("Paired to backend", if (watch.isPaired) "Paired" else "Not paired", watch.isPaired)
        StatusRow("Sensor source", watch.sensorSource ?: "Unknown", watch.sensorSource != null)
        StatusRow("Watch screen", watch.currentScreen ?: "—", true)
        StatusRow("Last telemetry upload", watch.lastUploadTime ?: "Never / unknown", watch.lastUploadTime != null)
        StatusRow(
            "Watch ↔ cloud",
            when {
                !watch.connected -> "Unknown (disconnected)"
                watch.cloudOk -> "OK (watch reported)"
                else -> "Waiting for confirmed sync"
            },
            watch.cloudOk
        )
        StatusRow(
            "Phone ↔ cloud",
            when (state.backendReachable) {
                true -> "Reachable (${NetworkClient.BASE_URL})"
                false -> "Unreachable"
                null -> "Checking…"
            },
            state.backendReachable == true
        )

        if (!watch.patientName.isNullOrBlank() || !watch.patientId.isNullOrBlank()) {
            SectionTitle("Patient")
            StatusRow("Name", watch.patientName ?: "—", true)
            StatusRow("Patient ID", shortenId(watch.patientId), true)
            StatusRow("Device ID", shortenId(watch.deviceId), true)
        }

        Button(
            onClick = viewModel::requestForceSync,
            enabled = watch.connected,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
        ) {
            Text("Ask watch to sync telemetry")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PairingAssistScreen(state: CompanionUiState, viewModel: CompanionViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Pair watch", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "The watch keeps the device token and talks to the backend directly. " +
                "Enter the pairing code here and the phone will send it to the watch over the Wear Data Layer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        StatusRow(
            "Watch link",
            if (state.watch.connected) "Connected (${state.watch.nodeDisplayName ?: "watch"})" else "Not connected",
            state.watch.connected
        )

        OutlinedTextField(
            value = state.pairingCode,
            onValueChange = viewModel::updatePairingCode,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pairing code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            textStyle = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
            )
        )

        Button(
            onClick = viewModel::submitPairingAssist,
            enabled = !state.isPairing && state.watch.connected,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isPairing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (state.isPairing) "Pairing…" else "Send code to watch")
        }

        state.pairResult?.let { result ->
            AlertBanner(
                title = if (result.ok) "Paired" else "Pairing failed",
                body = result.message,
                color = if (result.ok) OkGreen else Danger
            )
        }

        Text("Or pair on the watch", style = MaterialTheme.typography.titleMedium)
        Text(
            "1. Open Emergency Watch on the Galaxy Watch\n" +
                "2. Tap Pair Watch\n" +
                "3. Enter the code from the monitoring portal\n" +
                "4. Return here — status updates automatically when linked",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsScreen(state: CompanionUiState, viewModel: CompanionViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        StatusRow("Backend", NetworkClient.BASE_URL, true)
        StatusRow("Phone app version", "1.0", true)
        StatusRow("Watch app version", state.watch.appVersion ?: "—", state.watch.appVersion != null)
        StatusRow(
            "Data Layer",
            if (state.watch.connected) "Active" else "Idle / disconnected",
            state.watch.connected
        )

        Text(
            "Telemetry sampling, heartbeat, and SOS stay on the watch. " +
                "This companion shows health and can assist pairing — it does not replace the watch pipeline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FilledTonalButton(onClick = viewModel::refreshAll, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh status")
        }

        Text(
            "Future-ready: multi-watch patient list, notifications, remote config, and escalation can plug into this companion shell without changing the watch backend contracts.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConnectionHero(watch: WatchStatusSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(connected = watch.connected, large = true)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (watch.connected) "Watch connected" else "Watch disconnected",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = watch.nodeDisplayName ?: "No Wear OS node nearby",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, ok: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (ok) OkGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
        )
    }
}

@Composable
private fun AlertBanner(title: String, body: String, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(14.dp)
    ) {
        Text(title, color = color, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusDot(connected: Boolean, large: Boolean = false) {
    Box(
        modifier = Modifier
            .size(if (large) 14.dp else 10.dp)
            .clip(CircleShape)
            .background(if (connected) OkGreen else Danger)
    )
}

private fun shortenId(id: String?): String {
    if (id.isNullOrBlank()) return "—"
    return if (id.length <= 12) id else id.take(8) + "…"
}
