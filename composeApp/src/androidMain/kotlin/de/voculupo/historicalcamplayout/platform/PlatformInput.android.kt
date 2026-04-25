package de.voculupo.historicalcamplayout.platform

import androidx.compose.ui.Modifier

actual fun Modifier.platformScrollZoom(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
): Modifier = this
