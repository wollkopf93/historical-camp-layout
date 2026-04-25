package de.voculupo.historicalcamplayout.platform

import androidx.compose.ui.Modifier

expect fun Modifier.platformScrollZoom(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
): Modifier
