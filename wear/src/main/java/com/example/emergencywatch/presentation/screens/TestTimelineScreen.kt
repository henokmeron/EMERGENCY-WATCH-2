package com.example.emergencywatch.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.emergencywatch.data.local.TestEventEntity
import java.util.Locale

@Composable
fun TestTimelineScreen(
    events: List<TestEventEntity>,
    onRunTestScenario: (String) -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val sideInset = textSideInset()
    val transformationSpec = rememberTransformationSpec()

    val scenarios = listOf(
        "Normal Walking",
        "Normal Sitting",
        "Lying Down",
        "Watch on Desk",
        "Watch Removed from Wrist",
        "Controlled Fall Simulation",
        "Fall + Recovery",
        "Fall + Inactivity",
        "Manual SOS Test",
        "Alert Cancellation"
    )

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
                        text = "🧪 SENSOR TEST TIMELINE",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Text(
                    text = "Tap a scenario below to log real sensor state & event timeline:",
                    modifier = Modifier.padding(horizontal = sideInset),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            scenarios.forEach { scenario ->
                item {
                    Button(
                        onClick = { onRunTestScenario(scenario) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Text(
                            text = "▶ Run: $scenario",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            item {
                ListHeader {
                    Text(
                        text = "📜 Event History (${events.size})",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        text = "No test events recorded yet. Run a scenario above.",
                        modifier = Modifier.padding(horizontal = sideInset),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                events.forEach { event ->
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = sideInset)
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text(
                                    text = event.scenarioName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${event.recordedAt}\n" +
                                        "HR: ${event.heartRate?.toString() ?: "--"} BPM | " +
                                        "Contact: ${if (event.contactStatus) "ON" else "OFF"} | " +
                                        "Move: ${String.format(Locale.US, "%.2f", event.movement)}\n" +
                                        "Conclusion: ${event.sensorConclusion}\n" +
                                        "State: ${event.finalState}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
