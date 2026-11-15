package de.voculupo.historicalcamplayout.features.planner.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.Vec2

/** Lets UI tests find the canvas to click and drag on it. */
internal const val PLANNER_CANVAS_TAG = "planner-canvas"

internal enum class RopeDragPart {
    Anchor,
    Stake,
}

internal data class RopeHandleHit(
    val ropeIndex: Int,
    val part: RopeDragPart,
    val pointPx: Offset,
)

internal fun parcelSizePx(project: PlannerProject): Size =
    Size(
        width = project.parcel.widthMeters * project.pixelsPerMeter,
        height = project.parcel.heightMeters * project.pixelsPerMeter,
    )

internal fun centeredParcelOriginPx(
    canvasSizePx: Size,
    parcelSizePx: Size,
): Offset {
    val x = ((canvasSizePx.width - parcelSizePx.width) / 2f).coerceAtLeast(16f)
    val y = ((canvasSizePx.height - parcelSizePx.height) / 2f).coerceAtLeast(16f)
    return Offset(x, y)
}

/** Limits the map pan so at least half the parcel (or half the screen, for a big parcel) stays visible. */
internal fun clampCanvasPan(
    pan: Offset,
    canvasSizePx: Size,
    parcelSizePx: Size,
): Offset {
    val centered = centeredParcelOriginPx(canvasSizePx, parcelSizePx)
    val keepX = minOf(parcelSizePx.width, canvasSizePx.width) / 2f
    val keepY = minOf(parcelSizePx.height, canvasSizePx.height) / 2f
    // Parcel spans [centered + pan, centered + pan + parcelSize]; keep keepX/keepY of it inside the canvas.
    return Offset(
        pan.x.coerceIn(keepX - parcelSizePx.width - centered.x, canvasSizePx.width - keepX - centered.x),
        pan.y.coerceIn(keepY - parcelSizePx.height - centered.y, canvasSizePx.height - keepY - centered.y),
    )
}

internal fun Vec2.toOffsetPx(
    pixelsPerMeter: Float = 1f,
    originPx: Offset = Offset.Zero,
): Offset = Offset(originPx.x + x * pixelsPerMeter, originPx.y + y * pixelsPerMeter)

internal fun Vec2.toOffset(): Offset = Offset(x, y)

internal fun ropeHandleHits(
    asset: PlacedAsset,
    pixelsPerMeter: Float,
    parcelOriginPx: Offset,
    hiddenRopeIndices: Set<Int> = emptySet(),
): List<RopeHandleHit> {
    val segments = GeometryEngine.visibleRopeSegments(asset, hiddenRopeIndices)
    return ArrayList<RopeHandleHit>(segments.size * 2).apply {
        segments.forEach { (index, seg) ->
            add(RopeHandleHit(index, RopeDragPart.Anchor, seg.start.toOffsetPx(pixelsPerMeter, parcelOriginPx)))
            add(RopeHandleHit(index, RopeDragPart.Stake, seg.end.toOffsetPx(pixelsPerMeter, parcelOriginPx)))
        }
    }
}
