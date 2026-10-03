package com.example.emergencywatch.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Smallest screen dimension; equals the display diameter on round watches. */
@Composable
internal fun screenDiameter(): Dp {
    val config = LocalConfiguration.current
    return minOf(config.screenWidthDp, config.screenHeightDp).dp
}

/** Extra side inset for multi-line text so wrapped lines stay clear of the curved edge. */
@Composable
internal fun textSideInset(): Dp = screenDiameter() * 0.06f
