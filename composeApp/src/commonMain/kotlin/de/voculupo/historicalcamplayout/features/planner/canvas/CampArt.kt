package de.voculupo.historicalcamplayout.features.planner.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.TentShapeType
import de.voculupo.historicalcamplayout.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Undyed linen/cotton canvas, as most historical tents are.
private val CANVAS_LIGHT = Color(0xFFF3ECDA)
private val CANVAS_SHADE = Color(0xFFA8957A)
internal val CANVAS_EDGE = Color(0xFF6B5838)
private val SEAM = Color(0x40503F26)
private val RIDGE = Color(0x80503F26)
private val POLE = Color(0xFF3F2C1B)
private val SHADOW = Color(0x47000000)

/** Direction towards the sun in world space (top left of the screen), so light and shadows stay put when a tent turns. */
private val SUN = Vec2(-0.6f, -0.8f)

/**
 * A tent seen from straight above: roof panels shaded by the sun, seams, poles and a cast shadow.
 * Draws in the tent's local frame: call it inside `rotate(rotationDeg, center)`.
 * [outline] is the preset's local outline in meters, [heightMeters] sets the shadow length (0 = none).
 */
internal fun DrawScope.drawTentArt(
    preset: TentPreset,
    outline: List<Vec2>,
    center: Offset,
    ppm: Float,
    rotationDeg: Float,
    heightMeters: Float,
    tint: Color?,
    edge: Color = CANVAS_EDGE,
) {
    if (outline.size < 3) return

    fun px(p: Vec2) = Offset(center.x + p.x * ppm, center.y + p.y * ppm)

    fun path(
        points: List<Vec2>,
        shift: Offset = Offset.Zero,
    ) = Path().apply {
        moveTo(px(points[0]).x + shift.x, px(points[0]).y + shift.y)
        points.drop(1).forEach { lineTo(px(it).x + shift.x, px(it).y + shift.y) }
        close()
    }
    val sun = rotateVec(SUN, -rotationDeg)
    val shadow = Offset(-sun.x, -sun.y) * (heightMeters * 0.3f * ppm)
    val poleRadius = (0.07f * ppm).coerceIn(1.5f, 5f)

    if (preset.shape == TentShapeType.POLYGON) {
        // Sun sail: a flat sheet up on poles, sagging a little in the middle.
        val sheet = path(outline)
        drawPath(path(outline, shadow), SHADOW)
        drawPath(sheet, CANVAS_LIGHT.copy(alpha = 0.94f))
        val reach = outline.maxOf { sqrt(it.x * it.x + it.y * it.y) } * ppm
        drawPath(sheet, Brush.radialGradient(0f to Color(0x306B5838), 1f to Color.Transparent, center = center, radius = reach))
        tint?.let { drawPath(sheet, it) }
        drawPath(sheet, edge, style = Stroke(1.6f))
        if (outline.size <= 8) outline.forEach { drawCircle(POLE, poleRadius, px(it)) }
        return
    }

    val parts = roofParts(preset, outline)
    parts.forEach { drawPath(path(it.points, shadow), SHADOW) }
    parts.forEach { part ->
        val ridge = part.ridge
        if (ridge == null) {
            drawFlatCanopy(path(part.points), part.points, ::px, poleRadius)
            return@forEach
        }
        val (r0, r1) = ridge
        // Mid tone underneath hides hairline gaps between facets.
        drawPath(path(part.points), lerp(CANVAS_SHADE, CANVAS_LIGHT, 0.55f))
        part.points.indices.forEach { i ->
            val a = part.points[i]
            val b = part.points[(i + 1) % part.points.size]
            val mid = Vec2((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            val foot = project(mid, r0, r1)
            val dx = mid.x - foot.x
            val dy = mid.y - foot.y
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1e-4f) return@forEach
            val light = ((dx * sun.x + dy * sun.y) / len + 1f) / 2f
            drawPath(path(listOf(a, b, project(b, r0, r1), project(a, r0, r1))), lerp(CANVAS_SHADE, CANVAS_LIGHT, 0.2f + 0.8f * light))
        }
        part.points.forEach { drawLine(SEAM, px(it), px(project(it, r0, r1)), 1f) }
        drawLine(RIDGE, px(r0), px(r1), 1.8f, cap = StrokeCap.Round)
    }
    parts.forEach { part ->
        val outlinePath = path(part.points)
        tint?.let { drawPath(outlinePath, it) }
        drawPath(outlinePath, edge, style = Stroke(1.6f))
    }
    val (r0, r1) = parts.first().ridge ?: return
    if (preset.shape == TentShapeType.A_FRAME && r0 != r1) {
        drawWoodFrame(r0, r1, ::px, ppm)
    } else {
        drawCircle(POLE, poleRadius, px(r0))
        if (r0 != r1) drawCircle(POLE, poleRadius, px(r1))
    }
    // Niord: the carved crossed boards stand at the entrance gable, at the front of the porch.
    if (preset.id.contains("niord", ignoreCase = true)) {
        parts.getOrNull(1)?.let { porch ->
            porch.ridge?.let { (p0, p1) -> drawGableBoards(p0, p1, porch.points.maxOf { abs(it.x - p1.x) }, ::px, ppm) }
        }
    }
}

private val WOOD = Color(0xFF7A5230)
private val WOOD_LIGHT = Color(0xFFB0814F)
private val WOOD_DARK = Color(0xFF3E2716)

private fun beamWidth(ppm: Float) = (0.09f * ppm).coerceIn(2f, 8f)

private fun DrawScope.drawPlank(
    from: Offset,
    to: Offset,
    width: Float,
) {
    drawLine(WOOD_DARK, from, to, width, StrokeCap.Round)
    drawLine(WOOD, from, to, width * 0.65f, StrokeCap.Round)
}

/** Maps (along the ridge, across it) offsets from [base] to pixels. */
private fun ridgeFrame(
    r0: Vec2,
    r1: Vec2,
    px: (Vec2) -> Offset,
): (Vec2, Float, Float) -> Offset {
    val dx = r1.x - r0.x
    val dy = r1.y - r0.y
    val len = sqrt(dx * dx + dy * dy)
    val along = Vec2(dx / len, dy / len)
    val across = Vec2(-along.y, along.x)
    return { base, a, c -> px(Vec2(base.x + along.x * a + across.x * c, base.y + along.y * a + across.y * c)) }
}

/** Wedge tent frame: ridge pole on top of the roof and the crossed gable poles whose tips stick out past each end. */
private fun DrawScope.drawWoodFrame(
    r0: Vec2,
    r1: Vec2,
    px: (Vec2) -> Offset,
    ppm: Float,
) {
    val at = ridgeFrame(r0, r1, px)
    val beam = beamWidth(ppm)
    listOf(r0 to -1f, r1 to 1f).forEach { (end, out) ->
        listOf(-1f, 1f).forEach { side ->
            drawPlank(at(end, -out * 0.1f, -side * 0.11f), at(end, out * 0.3f, side * 0.18f), beam * 0.9f)
        }
    }
    val overhang = 0.25f
    drawLine(WOOD_DARK, at(r0, -overhang, 0f), at(r1, overhang, 0f), beam, StrokeCap.Round)
    drawLine(WOOD, at(r0, -overhang, 0f), at(r1, overhang, 0f), beam * 0.7f, StrokeCap.Round)
    // Sunlit top of the round pole.
    drawLine(WOOD_LIGHT, at(r0, -overhang, -0.012f), at(r1, overhang, -0.012f), beam * 0.22f, StrokeCap.Round)
}

/**
 * Two wide boards standing in the gable at [r1] (the end of ridge [r0]-[r1]): their feet at the eaves
 * [halfWidth] to each side, crossing above the ridge with the carved tips leaning out in front.
 */
private fun DrawScope.drawGableBoards(
    r0: Vec2,
    r1: Vec2,
    halfWidth: Float,
    px: (Vec2) -> Offset,
    ppm: Float,
) {
    val at = ridgeFrame(r0, r1, px)
    val board = beamWidth(ppm) * 1.6f
    listOf(-1f, 1f).forEach { side ->
        drawPlank(at(r1, 0f, -side * halfWidth), at(r1, 0.15f, side * 0.3f), board)
    }
}

/**
 * A roof surface: its outline and the ridge (a segment, or one point for a cone) its panels slope down from.
 * No ridge: a flat canopy, like the porch awning of a Saxon tent.
 */
private class RoofPart(
    val points: List<Vec2>,
    val ridge: Pair<Vec2, Vec2>?,
)

/** Flat awning on two front poles: one even sheet, sagging a little between the poles. */
private fun DrawScope.drawFlatCanopy(
    sheet: Path,
    points: List<Vec2>,
    px: (Vec2) -> Offset,
    poleRadius: Float,
) {
    drawPath(sheet, lerp(CANVAS_SHADE, CANVAS_LIGHT, 0.85f))
    val front = points.minOf { it.y }
    val back = points.maxOf { it.y }
    val x = points.sumOf { it.x.toDouble() }.toFloat() / points.size
    drawPath(
        sheet,
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.6f to Color(0x266B5838),
            1f to Color.Transparent,
            startY = minOf(px(Vec2(x, front)).y, px(Vec2(x, back)).y),
            endY = maxOf(px(Vec2(x, front)).y, px(Vec2(x, back)).y),
        ),
    )
    points.filter { it.y < front + 0.01f }.forEach { drawCircle(POLE, poleRadius, px(it)) }
}

private fun roofParts(
    preset: TentPreset,
    outline: List<Vec2>,
): List<RoofPart> {
    val hw = preset.widthMeters / 2f
    val hh = preset.heightMeters / 2f
    return when (preset.shape) {
        TentShapeType.CIRCLE -> {
            listOf(RoofPart(outline, Vec2(0f, 0f) to Vec2(0f, 0f)))
        }

        TentShapeType.CAPSULE -> {
            (hw - hh).coerceAtLeast(0f).let { s -> listOf(RoofPart(outline, Vec2(-s, 0f) to Vec2(s, 0f))) }
        }

        // Wedge tent: ridge from the front gable (entry) to the back one.
        TentShapeType.A_FRAME -> {
            listOf(RoofPart(outline, Vec2(0f, -hh) to Vec2(0f, hh)))
        }

        TentShapeType.RECTANGLE -> {
            listOf(
                if (hw >= hh) {
                    RoofPart(outline, Vec2(-(hw - hh), 0f) to Vec2(hw - hh, 0f))
                } else {
                    RoofPart(outline, Vec2(0f, -(hh - hw)) to Vec2(0f, hh - hw))
                },
            )
        }

        TentShapeType.SAXON -> {
            saxonParts(outline, pitchedPorch = preset.id.contains("niord", ignoreCase = true))
        }

        TentShapeType.POLYGON -> {
            emptyList()
        }
    }
}

/**
 * Saxon tent: a ridge between the two poles, end gores down to the pointed ends, and an optional
 * porch sticking out at the front (-y): a flat awning on two poles, or (Niord) a pitched roof with its own ridge.
 */
private fun saxonParts(
    outline: List<Vec2>,
    pitchedPorch: Boolean,
): List<RoofPart> {
    val eps = 0.01f
    val back = outline.maxOf { it.y }
    val body = outline.filter { it.y >= -back - eps }
    val porch = outline.filter { it.y < -back - eps }
    val ridgeHalf = body.filter { abs(abs(it.y) - back) < eps }.maxOfOrNull { abs(it.x) } ?: 0f
    val main = RoofPart(body, Vec2(-ridgeHalf, 0f) to Vec2(ridgeHalf, 0f))
    if (porch.isEmpty()) return listOf(main)
    val l = porch.minOf { it.x }
    val r = porch.maxOf { it.x }
    val tip = porch.minOf { it.y }
    val porchPart =
        RoofPart(
            listOf(Vec2(l, -back), Vec2(r, -back), Vec2(r, tip), Vec2(l, tip)),
            if (pitchedPorch) Vec2(0f, -back) to Vec2(0f, tip) else null,
        )
    return listOf(main, porchPart)
}

/** Fire pit seen from above: scorched ground, a ring of stones, crossed logs and the glow of the fire. */
internal fun DrawScope.drawCampfire(
    center: Offset,
    radiusPx: Float,
) {
    if (radiusPx < 2f) return
    val ring = radiusPx * 0.55f
    drawCircle(
        Brush.radialGradient(0f to Color(0x55FF9A3C), 1f to Color.Transparent, center = center, radius = radiusPx * 1.3f),
        radius = radiusPx * 1.3f,
        center = center,
    )
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xB02B2118),
            0.7f to Color(0x602B2118),
            1f to Color.Transparent,
            center = center,
            radius = radiusPx,
        ),
        radius = radiusPx,
        center = center,
    )
    drawCircle(
        Brush.radialGradient(0f to Color(0xFF2E2722), 1f to Color(0xFF5E544B), center = center, radius = ring),
        radius = ring,
        center = center,
    )
    val logLength = ring * 0.8f
    repeat(4) { i ->
        val a = (i * 47f + 20f) * PI.toFloat() / 180f
        val dir = Offset(cos(a), sin(a))
        val start = center - dir * logLength
        val end = center + dir * logLength
        drawLine(Color(0xFF5B3A22), start, end, radiusPx * 0.11f, StrokeCap.Round)
        drawLine(
            Color(0xFF241610),
            center - dir * (logLength * 0.35f),
            center + dir * (logLength * 0.35f),
            radiusPx * 0.11f,
            StrokeCap.Round,
        )
    }
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFFFF4B8),
            0.3f to Color(0xFFFFB23C),
            0.65f to Color(0xC0E2531B),
            1f to Color.Transparent,
            center = center,
            radius = ring * 0.85f,
        ),
        radius = ring * 0.85f,
        center = center,
    )
    val stones = 12
    val stoneRadius = radiusPx * 0.13f
    repeat(stones) { i ->
        // Fixed jitter so stones look hand-laid but never move between frames.
        val a = 2f * PI.toFloat() * i / stones + sin(i * 2.3f) * 0.12f
        val dist = ring + stoneRadius * (0.55f + 0.25f * sin(i * 1.7f))
        val c = center + Offset(cos(a), sin(a)) * dist
        val sr = stoneRadius * (0.85f + 0.25f * cos(i * 3.1f))
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFC4C0B6),
                1f to Color(0xFF6A665F),
                center = c + Offset(-sr * 0.35f, -sr * 0.45f),
                radius = sr * 1.4f,
            ),
            radius = sr,
            center = c,
        )
        drawCircle(Color(0xFF45423D), radius = sr, center = c, style = Stroke(1f))
    }
}

/** Size of one grass tile; the canvas scales it so one tile covers about [GRASS_TILE_METERS] of ground. */
internal const val GRASS_TILE_PX = 256
internal const val GRASS_TILE_METERS = 4f

/** Seamless top-down meadow tile, painted once: mottled patches plus thousands of short blades. */
internal fun grassTexture(): ImageBitmap {
    val n = GRASS_TILE_PX
    val image = ImageBitmap(n, n)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(n.toFloat(), n.toFloat())) {
        drawRect(Color(0xFF6A8743))
        val rnd = Random(7)

        // Draws at every position the tile wraps to, so the edges match up.
        fun wrapped(
            x: Float,
            y: Float,
            reach: Float,
            draw: (Offset) -> Unit,
        ) {
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val p = Offset(x + dx * n, y + dy * n)
                    if (p.x > -reach && p.x < n + reach && p.y > -reach && p.y < n + reach) draw(p)
                }
            }
        }
        val patches = listOf(Color(0x30A4C060), Color(0x284A6A2A), Color(0x22B8B060), Color(0x203A5520))
        repeat(70) {
            val r = 14f + rnd.nextFloat() * 40f
            val color = patches[rnd.nextInt(patches.size)]
            wrapped(rnd.nextFloat() * n, rnd.nextFloat() * n, r) { p ->
                drawCircle(Brush.radialGradient(0f to color, 1f to Color.Transparent, center = p, radius = r), radius = r, center = p)
            }
        }
        val blades = listOf(Color(0xFF4F6E2E), Color(0xFF7E9F4F), Color(0xFF93B35E), Color(0xFF5C7A35), Color(0xFFA9B86A))
        repeat(3200) {
            val len = 2f + rnd.nextFloat() * 4f
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val d = Offset(cos(a), sin(a)) * len
            val color = blades[rnd.nextInt(blades.size)].copy(alpha = 0.45f + rnd.nextFloat() * 0.45f)
            wrapped(rnd.nextFloat() * n, rnd.nextFloat() * n, len) { p ->
                drawLine(color, p, p + d, 1f + rnd.nextFloat() * 0.6f, StrokeCap.Round)
            }
        }
    }
    return image
}

private fun project(
    p: Vec2,
    a: Vec2,
    b: Vec2,
): Vec2 {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    if (len2 < 1e-6f) return a
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
    return Vec2(a.x + dx * t, a.y + dy * t)
}

private fun rotateVec(
    v: Vec2,
    deg: Float,
): Vec2 {
    val r = deg * PI.toFloat() / 180f
    return Vec2(v.x * cos(r) - v.y * sin(r), v.x * sin(r) + v.y * cos(r))
}
