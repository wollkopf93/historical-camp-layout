package de.voculupo.historicalcamplayout.features.planner.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasPanClampTest {
    private val canvas = Size(1000f, 800f)

    private fun visibleWidth(
        pan: Offset,
        parcel: Size,
    ): Float {
        val left = centeredParcelOriginPx(canvas, parcel).x + pan.x
        return minOf(left + parcel.width, canvas.width) - maxOf(left, 0f)
    }

    @Test
    fun smallPan_isKept() {
        val pan = Offset(30f, -20f)
        assertEquals(pan, clampCanvasPan(pan, canvas, Size(400f, 300f)))
    }

    @Test
    fun hugePan_keepsHalfTheParcelOnScreen() {
        listOf(Size(200f, 150f), Size(3000f, 2500f)).forEach { parcel ->
            listOf(Offset(1e6f, 1e6f), Offset(-1e6f, -1e6f)).forEach { pan ->
                val clamped = clampCanvasPan(pan, canvas, parcel)
                val keep = minOf(parcel.width, canvas.width) / 2f
                assertTrue(visibleWidth(clamped, parcel) >= keep - 0.5f, "parcel $parcel pan $pan -> $clamped")
            }
        }
    }

    @Test
    fun zeroSizes_doNotThrow() {
        clampCanvasPan(Offset(5f, 5f), Size.Zero, Size.Zero) // coerceIn throws if the range were inverted
    }
}
