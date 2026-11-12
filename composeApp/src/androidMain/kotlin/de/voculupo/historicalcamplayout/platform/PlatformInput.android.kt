package de.voculupo.historicalcamplayout.platform

import androidx.compose.ui.Modifier

actual fun Modifier.platformScrollZoom(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
): Modifier = this

@androidx.compose.runtime.Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
