package com.example.emergencywatch.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

// EdgeButtonSize.Medium height plus a small gap
private val SOS_BUTTON_RESERVE = 74.dp

/**
 * Shows ONLY live watch values. Never invent HR/SpO2.
 * If HR is null, display "--" — do not show Lovable baseline numbers.
 */
@Composable
fun MonitoringScreen(
    heartRate: Int?,
    contactStatus: Boolean,
    batteryLevel: Int,
    queuedCount: Int,
    statusMessage: String?,
    isPhysicalWatch: Boolean,
    sensorSourceLabel: String,
    patientName: String?,
    onTriggerSos: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val diameter = screenDiameter()

    ScreenScaffold { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = diameter * 0.10f,
                        bottom = SOS_BUTTON_RESERVE,
                        start = diameter * 0.08f,
                        end = diameter * 0.08f
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
            ) {
                Text(
                    text = if (isPhysicalWatch) "PHYSICAL WATCH · DEV" else "EMULATOR",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPhysicalWatch) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                if (!patientName.isNullOrBlank()) {
                    Text(
                        text = patientName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "${if (contactStatus) "ON WRIST" else "OFF WRIST"} · 🔋$batteryLevel%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (contactStatus) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Row {
                    Text(
                        text = heartRate?.toString() ?: "--",
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            heartRate == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            heartRate > 130 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = " BPM",
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = if (heartRate == null) {
                        "Live HR unavailable · $sensorSourceLabel"
                    } else {
                        "Live · $sensorSourceLabel"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Watch never fabricates SpO2 — show explicit absent marker so UI
                // cannot be confused with Lovable baseline SpO2 (often 97%).
                Text(
                    text = "SpO2: -- (not measured)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                if (queuedCount > 0) {
                    Text(
                        text = "Queued: $queuedCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                if (!statusMessage.isNullOrBlank()) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                CompactButton(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Text("⚙ Status")
                }
            }

            EdgeButton(
                onClick = onTriggerSos,
                modifier = Modifier.align(Alignment.BottomCenter),
                buttonSize = EdgeButtonSize.Medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(
                    text = "EMERGENCY SOS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}
