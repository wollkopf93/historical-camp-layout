package de.voculupo.historicalcamplayout.features.planner.state

import androidx.compose.ui.geometry.Rect
import de.voculupo.historicalcamplayout.AutoSetupRequest
import de.voculupo.historicalcamplayout.BlockZone
import de.voculupo.historicalcamplayout.FreeAreaKind
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.RingShape
import de.voculupo.historicalcamplayout.RopeSegment
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.Vec2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Fine-tuning after the rule-based draft, after "Make it Home" (Yu et al. 2011): every tent gets a
 * penalty and simulated annealing shifts, turns and swaps tents to lower the total.
 * - Hard (1000 each): bodies overlapping, ropes running into a body, a door blocked by a tent or
 *   facing a fire/arena, body outside the parcel.
 * - Soft: walkway shorter than chosen, too close to an open area, door not facing the nearest sail,
 *   skewed (not 90 degrees), drifting from the draft spot, and for ring shapes distance from the sail.
 * Sails, open areas and existing assets stay where they are. Fixed seed, so the preview is stable.
 */
internal object LayoutRefiner {
    /** Large enough that no sum of soft terms can buy a hard violation. */
    private const val HARD = 1000f
    private const val WALK = 5f

    /** Any shortfall at all counts: the app warns from the first centimetre below the walkway. */
    private const val WALK_STEP = 3f
    private const val FACE = 2f
    private const val SKEW = 0.5f
    private const val DRIFT = 0.2f
    private const val COHESION = 0.4f
    private const val MIN_ZONE_CLEAR = 1.0f
    private const val ITERATIONS_PER_TENT = 800
    private const val START_TEMP = 1f
    private const val END_TEMP = 0.05f
    private const val SEED = 7

    /** Everything the cost function needs about one asset, computed once per pose. */
    private class Shape(
        val outline: List<Vec2>,
        val box: Rect,
        val padded: Rect,
        val reach: Rect,
        val ropes: List<RopeSegment>,
        val door: List<Vec2>?,
        val isSail: Boolean,
    )

    private class Zone(
        val points: List<Vec2>,
        val box: Rect,
        val noEntry: Boolean,
    )

    fun refine(
        result: AutoLayoutResult,
        req: AutoSetupRequest,
        presetsById: Map<String, TentPreset>,
        userZones: List<BlockZone>,
        existing: List<PlacedAsset>,
    ): AutoLayoutResult {
        fun preset(a: PlacedAsset) = presetsById.getValue(a.presetId)
        val tents = result.assets.filterNot { GeometryEngine.isSunSailPreset(preset(it)) }.toMutableList()
        if (tents.isEmpty()) return result
        val sails = result.assets.filter { GeometryEngine.isSunSailPreset(preset(it)) }
        val fixed = (sails + existing.filter { it.presetId in presetsById }).map { shape(it, preset(it)) }
        val laneZones =
            result.blocks
                .filter { !it.key.isRing && req.freeAreas.getOrNull(it.key.index)?.kind == FreeAreaKind.LANE }
                .flatMap { it.zoneIds }
                .toSet()
        val zones =
            result.zones.map { Zone(it.points, bounds(it.points), it.id !in laneZones) } +
                userZones.filter { it.points.size >= 3 }.map { Zone(it.points, bounds(it.points), true) }
        val centres = sails.map { it.position }
        val ringShape = req.shape != RingShape.EDGE && req.shape != RingShape.SPREAD
        // Rows (U, edges) stay strictly square; rings and spread may use 45 degrees at their corners.
        val square = req.shape == RingShape.U_SHAPE || req.shape == RingShape.EDGE

        fun snap(deg: Float) = if (square) AutoLayout.squareUp((deg / 90f).roundToInt() * 90f) else AutoLayout.squareUp(deg)
        val start = tents.map { it.position }
        val walk = result.walkwayMeters
        val cloth = walk <= 0f
        val shapes = tents.map { shape(it, preset(it)) }.toMutableList()

        fun nearestCentre(p: Vec2) = centres.minByOrNull { hypot(it.x - p.x, it.y - p.y) }

        fun pair(
            a: Shape,
            b: Shape,
        ): Float {
            var c = 0f
            if (gap(a.reach, b.reach) < 0f) {
                if (gap(a.box, b.box) < 0f && GeometryEngine.polygonsIntersect(a.outline, b.outline)) c += HARD
                // Cloth to cloth: tent ropes get swung behind the neighbour afterwards; sail ropes can't.
                val ropesCount = !cloth || a.isSail || b.isSail
                if (ropesCount &&
                    (
                        a.ropes.any { GeometryEngine.lineIntersectsPolygon(it, b.outline) } ||
                            b.ropes.any { GeometryEngine.lineIntersectsPolygon(it, a.outline) }
                    )
                ) {
                    c += HARD
                }
            }
            if (!b.isSail && a.door != null && gap(bounds(a.door), b.box) < 0f &&
                GeometryEngine.polygonsIntersect(a.door, b.outline)
            ) {
                c += HARD
            }
            if (!a.isSail && b.door != null && gap(bounds(b.door), a.box) < 0f &&
                GeometryEngine.polygonsIntersect(b.door, a.outline)
            ) {
                c += HARD
            }
            // Cloth to cloth: walls may touch, so measure the walls rather than the safety boxes.
            val g = if (cloth) gap(a.box, b.box) else gap(a.padded, b.padded)
            if (g < walk) c += WALK_STEP + WALK * (walk - g)
            return c
        }

        fun zoneCost(a: Shape): Float =
            zones
                .sumOf { z ->
                    var c = 0f
                    val g = gap(a.box, z.box)
                    val need = max(walk, MIN_ZONE_CLEAR)
                    if (g < need) c += WALK_STEP + WALK * (need - g)
                    if (g < 0f && GeometryEngine.polygonsIntersect(a.outline, z.points)) c += HARD
                    if (z.noEntry && a.door != null && gap(bounds(a.door), z.box) < 0f &&
                        GeometryEngine.polygonsIntersect(a.door, z.points)
                    ) {
                        c += HARD
                    }
                    c.toDouble()
                }.toFloat()

        fun unary(
            i: Int,
            a: PlacedAsset,
            s: Shape,
        ): Float {
            var c = 0f
            val p = req.parcel
            val over =
                max(0f, -s.box.left) + max(0f, -s.box.top) + max(0f, s.box.right - p.widthMeters) + max(0f, s.box.bottom - p.heightMeters)
            if (over > 0f) c += HARD + HARD * over
            val centre = nearestCentre(a.position)
            val entry = GeometryEngine.entryWorld(a, preset(a))
            if (centre != null && entry != null) {
                val (_, out) = entry
                val to = Vec2(centre.x - a.position.x, centre.y - a.position.y)
                val len = hypot(to.x, to.y)
                if (len > 0.01f) c += FACE * (1f - (out.x * to.x + out.y * to.y) / len)
                if (ringShape) c += COHESION * len
            }
            if (a.rotationDeg % 90f != 0f) c += SKEW
            c += DRIFT * hypot(a.position.x - start[i].x, a.position.y - start[i].y)
            return c
        }

        fun tentCost(
            i: Int,
            a: PlacedAsset = tents[i],
            s: Shape = shapes[i],
            skip: Int = -1,
        ): Float {
            var c = unary(i, a, s) + zoneCost(s)
            fixed.forEach { c += pair(s, it) }
            shapes.forEachIndexed { j, other -> if (j != i && j != skip) c += pair(s, other) }
            return c
        }

        fun facing(
            a: PlacedAsset,
            p: Vec2,
        ): Float {
            val centre = nearestCentre(p) ?: return a.rotationDeg
            return snap(kotlin.math.atan2(p.y - centre.y, p.x - centre.x) * 180f / kotlin.math.PI.toFloat() - 90f)
        }

        val rnd = Random(SEED)
        val n = tents.size
        val movable = tents.indices.filter { tents[it].id in result.movableIds }
        if (movable.isEmpty()) return result
        val iterations = ITERATIONS_PER_TENT * movable.size
        val cooling = (END_TEMP / START_TEMP).pow(1f / iterations)
        var temp = START_TEMP
        var total = (0 until n).sumOf { tentCost(it).toDouble() }.toFloat() // pairs counted twice: only deltas matter
        var best = tents.toList()
        var bestTotal = total
        repeat(iterations) {
            val i = movable[rnd.nextInt(movable.size)]
            val a = tents[i]
            val kind = rnd.nextInt(10)
            if (kind < 8 || movable.size < 2) {
                val candidate =
                    if (kind < 6) {
                        val step = 0.25f + 2f * temp / START_TEMP
                        a.copy(
                            position =
                                Vec2(
                                    a.position.x + (rnd.nextFloat() * 2 - 1) * step,
                                    a.position.y + (rnd.nextFloat() * 2 - 1) * step,
                                ),
                        )
                    } else {
                        val turn = listOf(facing(a, a.position), a.rotationDeg + 90f, a.rotationDeg - 90f)[rnd.nextInt(3)]
                        a.copy(rotationDeg = snap(turn))
                    }
                val s = shape(candidate, preset(candidate))
                val delta = 2f * (tentCost(i, candidate, s) - tentCost(i))
                if (delta <= 0f || rnd.nextFloat() < exp(-delta / temp)) {
                    tents[i] = candidate
                    shapes[i] = s
                    total += delta
                }
            } else {
                val j = movable.filter { it != i }.let { it[rnd.nextInt(it.size)] }
                val b = tents[j]
                val na = a.copy(position = b.position, rotationDeg = facing(a, b.position))
                val nb = b.copy(position = a.position, rotationDeg = facing(b, a.position))
                val sa = shape(na, preset(na))
                val sb = shape(nb, preset(nb))
                val before = tentCost(i, skip = j) + tentCost(j, skip = i) + pair(shapes[i], shapes[j])
                val after = tentCost(i, na, sa, skip = j) + tentCost(j, nb, sb, skip = i) + pair(sa, sb)
                val delta = 2f * (after - before)
                if (delta <= 0f || rnd.nextFloat() < exp(-delta / temp)) {
                    tents[i] = na
                    tents[j] = nb
                    shapes[i] = sa
                    shapes[j] = sb
                    total += delta
                }
            }
            if (total < bestTotal - 0.001f) {
                bestTotal = total
                best = tents.toList()
            }
            temp *= cooling
        }

        var refined = best
        if (cloth) {
            val outlines = (sails + refined).map { it.id to GeometryEngine.assetWorldOutline(it, preset(it)) }
            refined = refined.map { t -> AutoLayout.swingRopes(t, outlines.filter { it.first != t.id }.map { it.second }) }
        }
        val byId = refined.associateBy { it.id }
        return result.copy(assets = result.assets.map { byId[it.id] ?: it })
    }

    private fun shape(
        a: PlacedAsset,
        p: TentPreset,
    ): Shape {
        val outline = GeometryEngine.assetWorldOutline(a, p)
        val ropes = GeometryEngine.ropeSegments(a)
        val box = bounds(outline)
        val padded = GeometryEngine.assetAabb(a, p)
        val reach = bounds(outline + ropes.map { it.end } + listOf(Vec2(padded.left, padded.top), Vec2(padded.right, padded.bottom)))
        return Shape(outline, box, padded, reach, ropes, GeometryEngine.entryZone(a, p), GeometryEngine.isSunSailPreset(p))
    }

    private fun bounds(points: List<Vec2>) =
        Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })

    /** Signed separation of two boxes along the axis where they are furthest apart (negative = overlap). */
    private fun gap(
        a: Rect,
        b: Rect,
    ) = maxOf(b.left - a.right, a.left - b.right, b.top - a.bottom, a.top - b.bottom)
}
