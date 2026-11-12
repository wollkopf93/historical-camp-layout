package de.voculupo.historicalcamplayout.platform

import androidx.compose.ui.Modifier

expect fun Modifier.platformScrollZoom(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
): Modifier

/** System back (Android button/gesture). No-op on desktop. */
@androidx.compose.runtime.Composable
expect fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
