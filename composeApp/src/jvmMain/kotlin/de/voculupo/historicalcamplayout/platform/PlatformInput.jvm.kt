package de.voculupo.historicalcamplayout.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.platformScrollZoom(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
): Modifier = this.onPointerEvent(PointerEventType.Scroll) { event ->
    val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
    if (scrollY == 0f) return@onPointerEvent
    if (scrollY < 0f) onZoomIn() else onZoomOut()
    event.changes.forEach { change ->
        if (change.scrollDelta != Offset.Zero) {
            change.consume()
        }
    }
}

