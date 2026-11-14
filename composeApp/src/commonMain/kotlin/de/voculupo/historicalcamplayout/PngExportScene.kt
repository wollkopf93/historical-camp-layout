package de.voculupo.historicalcamplayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

/** Size and scale of the PNG export; the image itself is drawn by the planner canvas code. */
data class PngExportScene(
    val widthPx: Int,
    val heightPx: Int,
    val pixelsPerMeter: Float,
    /** The parcel in image pixels; its top-left is where the plan's origin lands. */
    val parcel: Rect,
)

private const val MAX_EXPORT_PX = 6000

fun buildPngExportScene(
    project: PlannerProject,
    presets: List<TentPreset>,
    paddingMeters: Float = 1.0f,
): PngExportScene {
    val presetsById = presets.associateBy { it.id }
    val hidden = GeometryEngine.joinHiddenRopeIndices(project, presets)
    var minX = 0f
    var minY = 0f
    var maxX = project.parcel.widthMeters
    var maxY = project.parcel.heightMeters
    project.assets.forEach { asset ->
        val preset = presetsById[asset.presetId] ?: return@forEach
        val body = GeometryEngine.assetBodyAabb(asset, preset)
        minX = min(minX, body.left)
        minY = min(minY, body.top)
        maxX = max(maxX, body.right)
        maxY = max(maxY, body.bottom)
        GeometryEngine.visibleRopeSegments(asset, hidden[asset.id].orEmpty()).forEach { (_, rope) ->
            minX = min(minX, min(rope.start.x, rope.end.x))
            minY = min(minY, min(rope.start.y, rope.end.y))
            maxX = max(maxX, max(rope.start.x, rope.end.x))
            maxY = max(maxY, max(rope.start.y, rope.end.y))
        }
    }
    minX -= paddingMeters
    minY -= paddingMeters
    maxX += paddingMeters
    maxY += paddingMeters

    // Scale down rather than crop: the longest side must fit in MAX_EXPORT_PX.
    val ppm = min(project.pixelsPerMeter.coerceAtLeast(20f), MAX_EXPORT_PX / max(maxX - minX, maxY - minY))
    return PngExportScene(
        widthPx = ((maxX - minX) * ppm).toInt().coerceIn(800, MAX_EXPORT_PX),
        heightPx = ((maxY - minY) * ppm).toInt().coerceIn(600, MAX_EXPORT_PX),
        pixelsPerMeter = ppm,
        parcel =
            Rect(
                offset = Offset(-minX * ppm, -minY * ppm),
                size = Size(project.parcel.widthMeters * ppm, project.parcel.heightMeters * ppm),
            ),
    )
}
