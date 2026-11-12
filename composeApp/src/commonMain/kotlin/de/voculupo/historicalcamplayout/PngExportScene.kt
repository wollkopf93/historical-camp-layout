package de.voculupo.historicalcamplayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

/** One placed asset, already transformed into image pixel coordinates. */
data class PngExportAsset(
    val label: String,
    val outline: List<Offset>,
    /** Visible ropes only (ropes hidden by sun-sail joins are left out), as start/stake pairs. */
    val ropes: List<Pair<Offset, Offset>>,
    val center: Offset,
)

/**
 * Platform-neutral geometry for the PNG export. Each platform only paints these primitives,
 * so the canvas, the desktop export and the Android export can't drift apart.
 */
data class PngExportScene(
    val widthPx: Int,
    val heightPx: Int,
    val pixelsPerMeter: Float,
    val parcel: Rect,
    val gridXs: List<Float>,
    val gridYs: List<Float>,
    val zones: List<List<Offset>>,
    val assets: List<PngExportAsset>,
)

private const val MAX_EXPORT_PX = 6000

fun buildPngExportScene(
    project: PlannerProject,
    presets: List<TentPreset>,
    paddingMeters: Float = 1.0f,
): PngExportScene {
    val presetsById = presets.associateBy { it.id }
    val hidden = GeometryEngine.joinHiddenRopeIndices(project, presets)
    val placed =
        project.assets.mapNotNull { asset ->
            val preset = presetsById[asset.presetId] ?: return@mapNotNull null
            Triple(asset, preset, GeometryEngine.visibleRopeSegments(asset, hidden[asset.id].orEmpty()).map { it.value })
        }

    var minX = 0f
    var minY = 0f
    var maxX = project.parcel.widthMeters
    var maxY = project.parcel.heightMeters
    placed.forEach { (asset, preset, ropes) ->
        val body = GeometryEngine.assetBodyAabb(asset, preset)
        minX = min(minX, body.left)
        minY = min(minY, body.top)
        maxX = max(maxX, body.right)
        maxY = max(maxY, body.bottom)
        ropes.forEach { rope ->
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

    fun px(v: Vec2) = Offset((v.x - minX) * ppm, (v.y - minY) * ppm)

    val widthPx = ((maxX - minX) * ppm).toInt().coerceIn(800, MAX_EXPORT_PX)
    val heightPx = ((maxY - minY) * ppm).toInt().coerceIn(600, MAX_EXPORT_PX)
    val spacingPx = (project.gridMeters * ppm).coerceAtLeast(8f)

    fun gridLines(
        offsetMeters: Float,
        limitPx: Int,
    ): List<Float> {
        val lines = mutableListOf<Float>()
        var p = ((-offsetMeters * ppm) % spacingPx + spacingPx) % spacingPx
        while (p <= limitPx) {
            lines += p
            p += spacingPx
        }
        return lines
    }

    val parcelTopLeft = px(Vec2(0f, 0f))
    return PngExportScene(
        widthPx = widthPx,
        heightPx = heightPx,
        pixelsPerMeter = ppm,
        parcel =
            Rect(
                offset = parcelTopLeft,
                size = Size(project.parcel.widthMeters * ppm, project.parcel.heightMeters * ppm),
            ),
        gridXs = gridLines(minX, widthPx),
        gridYs = gridLines(minY, heightPx),
        zones = project.blockZones.filter { it.points.size >= 3 }.map { zone -> zone.points.map(::px) },
        assets =
            placed.map { (asset, preset, ropes) ->
                PngExportAsset(
                    label = asset.label.ifBlank { preset.name },
                    outline = GeometryEngine.assetWorldOutline(asset, preset).map(::px),
                    ropes = if (asset.showRopes) ropes.map { px(it.start) to px(it.end) } else emptyList(),
                    center = px(asset.position),
                )
            },
    )
}
