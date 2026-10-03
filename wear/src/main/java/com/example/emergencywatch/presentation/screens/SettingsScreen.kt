package com.example.emergencywatch.presentation.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * Device truth panel — must match what the watch actually measures/sends,
 * not Lovable baseline/demo numbers.
 */
@Composable
fun SettingsScreen(
    isPhysicalWatch: Boolean,
    patientName: String?,
    patientId: String?,
    deviceId: String?,
    heartRate: Int?,
    contactStatus: Boolean,
    batteryLevel: Int,
    sensorSourceLabel: String,
    lastUpload: String?,
    isSyncing: Boolean,
    onForceSync: () -> Unit,
    onOpenTestTimeline: () -> Unit,
    onDePair: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val sideInset = textSideInset()
    val transformationSpec = rememberTransformationSpec()

    fun shortId(id: String?): String {
        if (id.isNullOrBlank()) return "—"
        return if (id.length <= 10) id else id.take(8) + "…"
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = onBack,
                buttonSize = EdgeButtonSize.ExtraSmall,
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Text("Back")
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding
        ) {
            item {
                ListHeader {
                    Text(
                        text = if (isPhysicalWatch) "PHYSICAL · DEV" else "EMULATOR",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Text(
                    text = "Patient: ${patientName ?: "—"}",
                    modifier = Modifier.padding(horizontal = sideInset),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    text = "Patient ID: ${shortId(patientId)}",
                    modifier = Modifier.padding(horizontal = sideInset),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    text = "Device ID: ${shortId(deviceId)}",
                    modifier = Modifier.padding(horizontal = sideInset),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    text = "Live HR: ${heartRate?.toString() ?: "--"} · SpO2: --",
                    modifier = Modifier.padding(horizontal = sideInset),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    text = "Contact: ${if (contactStatus) "ON WRIST" else "DETACHED"} · Bat $batteryLevel%",
                    modifier = Modifier.padding(horizontal = sideInset),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (contactStatus) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    text = "Sensors: $sensorSourceLabel",
                    modifier = Modifier.padding(horizontal = sideInset),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    text = "Last sync: ${lastUpload ?: "never"}",
                    modifier = Modifier.padding(horizontal = sideInset),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    text = "If Lovable shows SpO2/HR when watch shows --, that is DEMO/BASELINE — not this watch.",
                    modifier = Modifier.padding(horizontal = sideInset),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            item {
                Button(
                    onClick = onForceSync,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(
                        text = if (isSyncing) "Syncing..." else "Force Sync",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Button(
                    onClick = onOpenTestTimeline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Text(
                        text = "🧪 Test Timeline",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Button(
                    onClick = onDePair,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        text = "De-pair Watch",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
