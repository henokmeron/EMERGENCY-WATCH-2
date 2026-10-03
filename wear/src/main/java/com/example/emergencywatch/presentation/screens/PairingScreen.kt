package com.example.emergencywatch.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

private const val MAX_PAIRING_CODE_LENGTH = 8

private val DIGIT_KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
private val LETTERS_A_M = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M")
private val LETTERS_N_Z = listOf("N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z")

private val KEYPAD_PAGES = listOf(
    "0-9" to DIGIT_KEYS,
    "A-M" to LETTERS_A_M,
    "N-Z" to LETTERS_N_Z
)

// Every page fits a 5 x 3 grid (13 letters max)
private const val KEY_COLUMNS = 5
private const val KEY_ROWS = 3
private val KEY_GAP = 3.dp

// EdgeButtonSize.ExtraSmall height plus a small gap
private val PAIR_BUTTON_RESERVE = 50.dp

@Composable
fun PairingScreen(
    code: String,
    isPairing: Boolean,
    errorMessage: String?,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val diameter = screenDiameter()

    // Fixed, non-scrolling layout sized from the screen diameter so that
    // the whole keypad and the PAIR button are visible together.
    ScreenScaffold { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = diameter * 0.14f, bottom = PAIR_BUTTON_RESERVE),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val displayCode = if (code.isBlank()) "________" else code.padEnd(MAX_PAIRING_CODE_LENGTH, '_')
                Text(
                    text = displayCode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(horizontal = diameter * 0.12f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                if (isPairing) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(diameter * 0.15f))
                    }
                } else {
                    val tabWidth = diameter * 0.17f
                    val tabHeight = diameter * 0.115f
                    Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                        KEYPAD_PAGES.forEachIndexed { index, (label, _) ->
                            KeypadButton(
                                label = label,
                                width = tabWidth,
                                height = tabHeight,
                                containerColor = if (pageIndex == index) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (pageIndex == index) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                onClick = { pageIndex = index }
                            )
                        }
                        KeypadButton(
                            label = "DEL",
                            width = tabWidth,
                            height = tabHeight,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = {
                                if (code.isNotEmpty()) onCodeChange(code.dropLast(1))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(KEY_GAP))

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Largest key that fits both the remaining height and the circle's width
                        val byHeight = (maxHeight - KEY_GAP * (KEY_ROWS - 1)) / KEY_ROWS
                        val byWidth = (diameter * 0.74f - KEY_GAP * (KEY_COLUMNS - 1)) / KEY_COLUMNS
                        val keySize = minOf(byHeight, byWidth)

                        Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                            KEYPAD_PAGES[pageIndex].second.chunked(KEY_COLUMNS).forEach { rowKeys ->
                                Row(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    horizontalArrangement = Arrangement.spacedBy(KEY_GAP)
                                ) {
                                    rowKeys.forEach { key ->
                                        KeypadButton(
                                            label = key,
                                            width = keySize,
                                            height = keySize,
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                            onClick = {
                                                if (code.length < MAX_PAIRING_CODE_LENGTH) {
                                                    onCodeChange(code + key)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isPairing) {
                EdgeButton(
                    onClick = onSubmit,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    buttonSize = EdgeButtonSize.ExtraSmall,
                    enabled = code.length >= 6
                ) {
                    Text("PAIR")
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    width: Dp,
    height: Dp,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val fontSize = with(LocalDensity.current) { (minOf(width, height) * 0.45f).toSp() }
    Button(
        onClick = onClick,
        modifier = Modifier.size(width = width, height = height),
        // Default 14dp side padding leaves no room for labels on small keys
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            fontSize = fontSize,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
