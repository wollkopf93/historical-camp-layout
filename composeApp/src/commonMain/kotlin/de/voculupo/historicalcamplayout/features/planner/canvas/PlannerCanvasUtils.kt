package de.voculupo.historicalcamplayout.features.planner.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.Vec2

internal enum class RopeDragPart {
    Anchor,
    Stake,
}

internal data class RopeHandleHit(
    val ropeIndex: Int,
    val part: RopeDragPart,
    val pointPx: Offset,
)

internal fun parcelSizePx(project: PlannerProject): Size = Size(
    width = project.parcel.widthMeters * project.pixelsPerMeter,
    height = project.parcel.heightMeters * project.pixelsPerMeter,
)

internal fun centeredParcelOriginPx(canvasSizePx: Size, parcelSizePx: Size): Offset {
    val x = ((canvasSizePx.width - parcelSizePx.width) / 2f).coerceAtLeast(16f)
    val y = ((canvasSizePx.height - parcelSizePx.height) / 2f).coerceAtLeast(16f)
    return Offset(x, y)
}

internal fun Vec2.toOffsetPx(pixelsPerMeter: Float = 1f, originPx: Offset = Offset.Zero): Offset =
    Offset(originPx.x + x * pixelsPerMeter, originPx.y + y * pixelsPerMeter)

internal fun Vec2.toOffset(): Offset = Offset(x, y)

internal fun ropeHandleHits(
    asset: PlacedAsset,
    preset: TentPreset,
    pixelsPerMeter: Float,
    parcelOriginPx: Offset,
): List<RopeHandleHit> {
    val segments = GeometryEngine.ropeSegments(asset, preset)
    return ArrayList<RopeHandleHit>(segments.size * 2).apply {
        segments.forEachIndexed { index, seg ->
            add(RopeHandleHit(index, RopeDragPart.Anchor, seg.start.toOffsetPx(pixelsPerMeter, parcelOriginPx)))
            add(RopeHandleHit(index, RopeDragPart.Stake, seg.end.toOffsetPx(pixelsPerMeter, parcelOriginPx)))
        }
    }
}

