package de.voculupo.historicalcamplayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

data class RopeSegment(
    val start: Vec2,
    val end: Vec2,
)

data class CollisionReport(
    val hardCollisions: Set<String> = emptySet(),
    val warningCollisions: Set<String> = emptySet(),
    val messages: List<String> = emptyList(),
    /** Rope indices per asset id that are hidden because two sun sails are joined along that edge. */
    val hiddenRopeIndices: Map<String, Set<Int>> = emptyMap(),
)

object GeometryEngine {
    private const val JOIN_MAX_EDGE_DISTANCE = 0.28f
    private const val JOIN_MIN_OVERLAP_FRACTION = 0.28f

    fun degToRad(deg: Float): Double = deg.toDouble() * PI / 180.0

    fun radToDeg(rad: Double): Float = (rad * 180.0 / PI).toFloat()

    private data class WorldEdge(
        val index: Int,
        val start: Vec2,
        val end: Vec2,
    )

    private data class AssetGeometry(
        val asset: PlacedAsset,
        val preset: TentPreset,
        val bodyAabb: Rect,
        val paddedAabb: Rect,
        val bodyPath: Path,
    )

    fun anchorSlotsForPreset(preset: TentPreset): List<Vec2> {
        if (preset.ropeAnchors.isNotEmpty()) {
            // Preserve vendor/model-specific real anchor logic when available.
            return preset.ropeAnchors.map { Vec2(it.localX, it.localY) }
        }
        return when (preset.shape) {
            TentShapeType.CIRCLE -> {
                val r = preset.widthMeters / 2f
                (0 until 8).map { i ->
                    val a = i * (2f * PI.toFloat() / 8f)
                    Vec2(cos(a) * r, sin(a) * r)
                }
            }

            TentShapeType.RECTANGLE, TentShapeType.A_FRAME -> {
                val hw = preset.widthMeters / 2f
                val hh = preset.heightMeters / 2f
                listOf(
                    Vec2(-hw, -hh),
                    Vec2(0f, -hh),
                    Vec2(hw, -hh),
                    Vec2(hw, 0f),
                    Vec2(hw, hh),
                    Vec2(0f, hh),
                    Vec2(-hw, hh),
                    Vec2(-hw, 0f),
                )
            }

            TentShapeType.CAPSULE, TentShapeType.SAXON -> {
                val hw = preset.widthMeters / 2f
                val hh = preset.heightMeters / 2f
                listOf(
                    Vec2(-hw, -hh * 0.5f),
                    Vec2(-hw, hh * 0.5f),
                    Vec2(-hw * 0.35f, -hh),
                    Vec2(-hw * 0.35f, hh),
                    Vec2(hw * 0.35f, -hh),
                    Vec2(hw * 0.35f, hh),
                    Vec2(hw, -hh * 0.5f),
                    Vec2(hw, hh * 0.5f),
                )
            }

            TentShapeType.POLYGON -> {
                val pts =
                    if (preset.polygonPoints.isNotEmpty()) {
                        preset.polygonPoints
                    } else {
                        val hw = preset.widthMeters / 2f
                        val hh = preset.heightMeters / 2f
                        listOf(Vec2(-hw, -hh), Vec2(hw, -hh), Vec2(hw, hh), Vec2(-hw, hh))
                    }
                val mids =
                    pts.indices.map { i ->
                        val a = pts[i]
                        val b = pts[(i + 1) % pts.size]
                        Vec2((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                    }
                (pts + mids).take(10)
            }
        }
    }

    fun snapLocalAnchorToPreset(
        local: Vec2,
        preset: TentPreset,
    ): Vec2 {
        val slots = anchorSlotsForPreset(preset)
        return slots.minByOrNull { slot -> distance(slot, local) } ?: local
    }

    private fun buildGeometries(
        project: PlannerProject,
        presets: List<TentPreset>,
    ): List<AssetGeometry> {
        val byId = presets.associateBy { it.id }
        return project.assets.mapNotNull { asset ->
            val preset = byId[asset.presetId] ?: return@mapNotNull null
            AssetGeometry(
                asset = asset,
                preset = preset,
                bodyAabb = assetBodyAabb(asset, preset),
                paddedAabb = assetAabb(asset, preset),
                bodyPath = assetWorldPath(asset, preset),
            )
        }
    }

    /**
     * Ropes that disappear because two sun sails share an edge (laced together instead of staked).
     * Derived from current geometry on every call, so moving a sail away restores its ropes automatically.
     */
    fun joinHiddenRopeIndices(
        project: PlannerProject,
        presets: List<TentPreset>,
    ): Map<String, Set<Int>> = joinHiddenRopeIndices(buildGeometries(project, presets))

    private fun joinHiddenRopeIndices(geometries: List<AssetGeometry>): Map<String, Set<Int>> {
        val sails = geometries.filter { isSunSailPreset(it.preset) }
        val hidden = mutableMapOf<String, MutableSet<Int>>()
        for (i in sails.indices) {
            for (j in i + 1 until sails.size) {
                val a = sails[i]
                val b = sails[j]
                joinedEdgePairs(a, b).forEach { (edgeA, edgeB) ->
                    hidden.getOrPut(a.asset.id) { mutableSetOf() } += ropeIndicesNearWorldEdge(a.asset, a.preset, edgeA)
                    hidden.getOrPut(b.asset.id) { mutableSetOf() } += ropeIndicesNearWorldEdge(b.asset, b.preset, edgeB)
                }
            }
        }
        return hidden.filterValues { it.isNotEmpty() }
    }

    /** Rope segments of [asset] without the ones listed in [hidden], keeping each rope's original index. */
    fun visibleRopeSegments(
        asset: PlacedAsset,
        hidden: Set<Int>,
    ): List<IndexedValue<RopeSegment>> = ropeSegments(asset).withIndex().filterNot { it.index in hidden }

    fun evaluate(
        project: PlannerProject,
        presets: List<TentPreset>,
    ): CollisionReport {
        val hard = mutableSetOf<String>()
        val warn = mutableSetOf<String>()
        val messages = mutableListOf<String>()
        val geometries = buildGeometries(project, presets)

        geometries.forEach { geo ->
            // Parcel border warnings should refer to the physical tent footprint,
            // not the optional safety margin buffer, to avoid false positives.
            val bounds = geo.bodyAabb
            if (bounds.left < 0f ||
                bounds.top < 0f ||
                bounds.right > project.parcel.widthMeters ||
                bounds.bottom > project.parcel.heightMeters
            ) {
                warn += geo.asset.id
                messages += "Asset ${geo.preset.name} ragt aus der Parzelle."
            }
        }

        val hiddenRopes = joinHiddenRopeIndices(geometries)

        for (i in geometries.indices) {
            for (j in i + 1 until geometries.size) {
                val a = geometries[i]
                val b = geometries[j]
                val bothSunSails = isSunSailPreset(a.preset) && isSunSailPreset(b.preset)
                if (bothSunSails && joinedEdgePairs(a, b).isNotEmpty()) {
                    continue
                }
                val distance = aabbDistanceMeters(a.paddedAabb, b.paddedAabb)
                if (bothSunSails && aabbOverlap(a.bodyAabb, b.bodyAabb) && CollisionEngine.pathsIntersect(a.bodyPath, b.bodyPath)) {
                    warn += a.asset.id
                    warn += b.asset.id
                    messages += "Sonnensegel überlappen: ${a.preset.name} und ${b.preset.name}."
                } else if (isHardCollision(a, b)) {
                    hard += a.asset.id
                    hard += b.asset.id
                    messages += "Harte Kollision zwischen ${a.preset.name} und ${b.preset.name}."
                } else if (isRopeIntrusionCollision(a, b, hiddenRopes)) {
                    hard += a.asset.id
                    hard += b.asset.id
                    messages += "Seil-Kollision: Seil ragt in ${a.preset.name} oder ${b.preset.name}."
                } else if (distance in 0.001f..1.5f) {
                    warn += a.asset.id
                    warn += b.asset.id
                    messages += "Laufweg zwischen ${a.preset.name} und ${b.preset.name} unter 1.5m (${formatFloat(distance, 2)}m)."
                }
            }
        }

        project.blockZones.forEach { zone ->
            if (zone.points.size < 3) return@forEach
            val zonePolygon = zone.points
            geometries.forEach { geo ->
                val assetPolygon = assetWorldOutline(geo.asset, geo.preset)
                if (polygonsIntersect(assetPolygon, zonePolygon)) {
                    hard += geo.asset.id
                    messages += "Sperrzone verletzt: ${geo.preset.name} liegt in einer Sperrzone."
                }
            }
        }

        return CollisionReport(hard, warn, messages.distinct(), hiddenRopes)
    }

    fun metersToOffset(
        v: Vec2,
        pixelsPerMeter: Float,
    ): Offset = Offset(v.x * pixelsPerMeter, v.y * pixelsPerMeter)

    fun offsetToMeters(
        offset: Offset,
        pixelsPerMeter: Float,
    ): Vec2 = Vec2(offset.x / pixelsPerMeter, offset.y / pixelsPerMeter)

    fun rotatePoint(
        local: Vec2,
        rotationDeg: Float,
    ): Vec2 {
        val rad = degToRad(rotationDeg)
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()
        return Vec2(
            local.x * cos - local.y * sin,
            local.x * sin + local.y * cos,
        )
    }

    fun worldPoint(
        asset: PlacedAsset,
        local: Vec2,
    ): Vec2 {
        val r = rotatePoint(local, asset.rotationDeg)
        return Vec2(asset.position.x + r.x, asset.position.y + r.y)
    }

    /** Inverse of [worldPoint]: world coordinates into the asset's local (unrotated) frame. */
    fun localPoint(
        asset: PlacedAsset,
        world: Vec2,
    ): Vec2 = rotatePoint(Vec2(world.x - asset.position.x, world.y - asset.position.y), -asset.rotationDeg)

    /**
     * Precise hit test for rotated assets.
     * Transforms world point into asset local-space, then runs point-in-polygon.
     */
    fun isPointInAsset(
        worldPoint: Vec2,
        asset: PlacedAsset,
        preset: TentPreset,
    ): Boolean {
        val localPoint = localPoint(asset, worldPoint)
        val outline = localOutlinePoints(preset)
        if (outline.isEmpty()) return false

        var inside = false
        var j = outline.lastIndex
        for (i in outline.indices) {
            val pi = outline[i]
            val pj = outline[j]
            val denom = pj.y - pi.y
            val intersects =
                (pi.y > localPoint.y) != (pj.y > localPoint.y) &&
                    localPoint.x < (pj.x - pi.x) * (localPoint.y - pi.y) / denom + pi.x
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    fun ropeSegments(asset: PlacedAsset): List<RopeSegment> {
        // No fallback to preset ropes: an empty list means the user removed them all.
        if (!asset.showRopes) return emptyList()
        return asset.ropes.map { rope ->
            val localStart = Vec2(rope.localX, rope.localY)
            val start = worldPoint(asset, localStart)
            val normal = anchorDirection(asset, localStart, rope.outwardAngleDeg)
            val end =
                Vec2(
                    start.x + normal.x * rope.lengthMeters,
                    start.y + normal.y * rope.lengthMeters,
                )
            RopeSegment(start, end)
        }
    }

    fun isSunSailPreset(preset: TentPreset): Boolean = preset.isSunSail || preset.id.startsWith("sun_sail_")

    /** Position that lines [movingAsset] up edge-to-edge with [targetAsset], or null if no edge pair is close enough. */
    fun snapSunSailToNeighbor(
        movingAsset: PlacedAsset,
        movingPreset: TentPreset,
        targetAsset: PlacedAsset,
        targetPreset: TentPreset,
        snapDistanceMeters: Float = 0.45f,
    ): Vec2? {
        if (!isSunSailPreset(movingPreset) || !isSunSailPreset(targetPreset)) return null
        val movingLocal = localOutlinePoints(movingPreset)
        val targetLocal = localOutlinePoints(targetPreset)
        if (movingLocal.size < 3 || targetLocal.size < 3) return null
        val movingEdges = worldEdges(movingAsset, movingLocal)
        val targetEdges = worldEdges(targetAsset, targetLocal)

        var best: Vec2? = null
        var bestScore = Float.POSITIVE_INFINITY
        movingEdges.forEach { movingEdge ->
            targetEdges.forEach { targetEdge ->
                val candidate =
                    edgeSnapCandidate(
                        movingAsset = movingAsset,
                        movingEdge = movingEdge,
                        targetEdge = targetEdge,
                        snapDistanceMeters = snapDistanceMeters,
                    ) ?: return@forEach
                if (candidate.first < bestScore) {
                    bestScore = candidate.first
                    best = candidate.second
                }
            }
        }
        return best
    }

    fun assetBodyAabb(
        asset: PlacedAsset,
        preset: TentPreset,
    ): Rect {
        // Built from the rotated world outline: outlines can be off-center (Saxon tents) and assets rotated.
        val points = assetWorldOutline(asset, preset)
        return Rect(
            left = points.minOf { it.x },
            top = points.minOf { it.y },
            right = points.maxOf { it.x },
            bottom = points.maxOf { it.y },
        )
    }

    fun assetAabb(
        asset: PlacedAsset,
        preset: TentPreset,
    ): Rect = assetBodyAabb(asset, preset).inflate(asset.safetyMarginMeters)

    fun snapToGrid(
        point: Vec2,
        grid: Float,
    ): Vec2 {
        if (grid <= 0f) return point
        return Vec2(
            round(point.x / grid) * grid,
            round(point.y / grid) * grid,
        )
    }

    fun distance(
        a: Vec2,
        b: Vec2,
    ): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun isHardCollision(
        a: AssetGeometry,
        b: AssetGeometry,
    ): Boolean {
        // Sun sails are intentionally combinable; body-body overlap between two sun sails
        // must not be treated as hard collision.
        if (isSunSailPreset(a.preset) && isSunSailPreset(b.preset)) return false
        if (!heightOverlaps(a.asset.heightRange, b.asset.heightRange)) return false
        if (!aabbOverlap(a.bodyAabb, b.bodyAabb)) return false
        val colliding = CollisionEngine.isColliding(a.asset, b.asset, a.bodyPath, b.bodyPath)
        if (!colliding) return false
        return true
    }

    /**
     * Rope segments vs another asset's footprint (2D), with vertical overlap rules:
     * - Default: rope owner uses its [PlacedAsset.heightRange] vs the other body's height (canopy / volume).
     * - Sonnensegel: ropes peg to the ground; their intrusion into tents must not be suppressed by the
     *   sail's high canopy band. We therefore use a near-ground band for the *rope owner* only when
     *   testing that owner's ropes against the other body. The inverse (e.g. tent ropes vs sail) still
     *   uses the tent's height range vs the sail's range, so a high sail does not falsely collide with
     *   ground ropes when heights do not overlap.
     */
    private fun isRopeIntrusionCollision(
        geoA: AssetGeometry,
        geoB: AssetGeometry,
        hiddenRopes: Map<String, Set<Int>>,
    ): Boolean {
        val a = geoA.asset
        val presetA = geoA.preset
        val b = geoB.asset
        val presetB = geoB.preset
        // Sun sails can be combined in different configurations. Their ropes should not
        // generate hard rope-collision warnings against other sun sail bodies.
        if (isSunSailPreset(presetA) && isSunSailPreset(presetB)) {
            return false
        }
        val ropesA = visibleRopeSegments(a, hiddenRopes[a.id].orEmpty()).map { it.value }
        val ropesB = visibleRopeSegments(b, hiddenRopes[b.id].orEmpty()).map { it.value }

        val bodyPolyA = assetWorldOutline(a, presetA)
        val bodyPolyB = assetWorldOutline(b, presetB)

        val ropeAIntoBodyB =
            ropesA.any { lineIntersectsPolygon(it, bodyPolyB) } &&
                heightOverlaps(heightRangeForRopeOwnerVsOtherBody(a, presetA), b.heightRange)
        val ropeBIntoBodyA =
            ropesB.any { lineIntersectsPolygon(it, bodyPolyA) } &&
                heightOverlaps(heightRangeForRopeOwnerVsOtherBody(b, presetB), a.heightRange)
        return ropeAIntoBodyB || ropeBIntoBodyA
    }

    /**
     * Vertical span used when this asset's *ropes* are tested against another asset's body.
     * Sonnensegel stakes sit near the ground while [PlacedAsset.heightRange] describes the canopy.
     */
    private fun heightRangeForRopeOwnerVsOtherBody(
        ropeOwner: PlacedAsset,
        ropeOwnerPreset: TentPreset,
    ): HeightRange {
        if (!isSunSailPreset(ropeOwnerPreset)) return ropeOwner.heightRange
        val upper = ropeOwner.heightRange.maxMeters.coerceIn(2.2f, 3.6f)
        return HeightRange(minMeters = 0f, maxMeters = upper)
    }

    internal fun lineIntersectsPolygon(
        seg: RopeSegment,
        polygon: List<Vec2>,
    ): Boolean {
        if (polygon.size < 3) return false
        if (pointInPolygon(seg.start, polygon) || pointInPolygon(seg.end, polygon)) return true
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            if (linesIntersect(seg.start, seg.end, a, b)) return true
        }
        return false
    }

    private fun linesIntersect(
        a1: Vec2,
        a2: Vec2,
        b1: Vec2,
        b2: Vec2,
    ): Boolean {
        val eps = 0.0001f

        fun orient(
            p: Vec2,
            q: Vec2,
            r: Vec2,
        ): Float = (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)

        fun isZero(v: Float): Boolean = kotlin.math.abs(v) <= eps

        fun onSegment(
            p: Vec2,
            q: Vec2,
            r: Vec2,
        ): Boolean =
            q.x <= max(p.x, r.x) + eps &&
                q.x + eps >= min(p.x, r.x) &&
                q.y <= max(p.y, r.y) + eps &&
                q.y + eps >= min(p.y, r.y)

        val o1 = orient(a1, a2, b1)
        val o2 = orient(a1, a2, b2)
        val o3 = orient(b1, b2, a1)
        val o4 = orient(b1, b2, a2)

        if (((o1 > eps && o2 < -eps) || (o1 < -eps && o2 > eps)) &&
            ((o3 > eps && o4 < -eps) || (o3 < -eps && o4 > eps))
        ) {
            return true
        }

        if (isZero(o1) && onSegment(a1, b1, a2)) return true
        if (isZero(o2) && onSegment(a1, b2, a2)) return true
        if (isZero(o3) && onSegment(b1, a1, b2)) return true
        if (isZero(o4) && onSegment(b1, a2, b2)) return true
        return false
    }

    private fun normalize(v: Vec2): Vec2 {
        val len = sqrt(v.x * v.x + v.y * v.y)
        if (len < 0.0001f) return Vec2(0f, -1f)
        return Vec2(v.x / len, v.y / len)
    }

    fun areSunSailsJoinedByEdge(
        a: PlacedAsset,
        presetA: TentPreset,
        b: PlacedAsset,
        presetB: TentPreset,
    ): Boolean = joinedEdgePairs(a, presetA, b, presetB).isNotEmpty()

    private fun joinedEdgePairs(
        a: AssetGeometry,
        b: AssetGeometry,
    ): List<Pair<Int, Int>> {
        // Cheap reject first: joined edges lie within the join tolerance of each other.
        if (aabbDistanceMeters(a.bodyAabb, b.bodyAabb) > JOIN_MAX_EDGE_DISTANCE) return emptyList()
        return joinedEdgePairs(a.asset, a.preset, b.asset, b.preset)
    }

    /** Index pairs (edge of a, edge of b) where the two sun sails touch along a shared edge. */
    private fun joinedEdgePairs(
        a: PlacedAsset,
        presetA: TentPreset,
        b: PlacedAsset,
        presetB: TentPreset,
    ): List<Pair<Int, Int>> {
        val aLocal = localOutlinePoints(presetA)
        val bLocal = localOutlinePoints(presetB)
        if (aLocal.size < 3 || bLocal.size < 3) return emptyList()
        val aEdges = worldEdges(a, aLocal)
        val bEdges = worldEdges(b, bLocal)
        return aEdges.flatMap { edgeA ->
            bEdges
                .filter { edgeB -> areEdgesJoined(edgeA, edgeB, JOIN_MAX_EDGE_DISTANCE, JOIN_MIN_OVERLAP_FRACTION) }
                .map { edgeB -> edgeA.index to edgeB.index }
        }
    }

    private fun worldEdges(
        asset: PlacedAsset,
        localPoints: List<Vec2>,
    ): List<WorldEdge> =
        localPoints.indices.map { idx ->
            WorldEdge(
                index = idx,
                start = worldPoint(asset, localPoints[idx]),
                end = worldPoint(asset, localPoints[(idx + 1) % localPoints.size]),
            )
        }

    private fun edgeSnapCandidate(
        movingAsset: PlacedAsset,
        movingEdge: WorldEdge,
        targetEdge: WorldEdge,
        snapDistanceMeters: Float,
    ): Pair<Float, Vec2>? {
        if (!areEdgesDirectionCompatible(movingEdge, targetEdge)) return null
        // Only edges that are already close may snap; otherwise any sail would jump onto any other sail's edge.
        if (lineDistanceScore(movingEdge, targetEdge) > snapDistanceMeters) return null
        val translateA = Vec2(targetEdge.end.x - movingEdge.start.x, targetEdge.end.y - movingEdge.start.y)
        val translateB = Vec2(targetEdge.start.x - movingEdge.end.x, targetEdge.start.y - movingEdge.end.y)
        val translation = Vec2((translateA.x + translateB.x) / 2f, (translateA.y + translateB.y) / 2f)
        val movedEdge =
            WorldEdge(
                index = movingEdge.index,
                start = Vec2(movingEdge.start.x + translation.x, movingEdge.start.y + translation.y),
                end = Vec2(movingEdge.end.x + translation.x, movingEdge.end.y + translation.y),
            )
        if (!areEdgesJoined(movedEdge, targetEdge, snapDistanceMeters, 0.55f)) return null
        // Prefer the snap that moves the sail least.
        val score = sqrt(translation.x * translation.x + translation.y * translation.y)
        return score to Vec2(movingAsset.position.x + translation.x, movingAsset.position.y + translation.y)
    }

    private fun areEdgesDirectionCompatible(
        a: WorldEdge,
        b: WorldEdge,
    ): Boolean {
        val aDir = normalize(Vec2(a.end.x - a.start.x, a.end.y - a.start.y))
        val bDir = normalize(Vec2(b.end.x - b.start.x, b.end.y - b.start.y))
        val dot = aDir.x * bDir.x + aDir.y * bDir.y
        return abs(dot) > 0.94f
    }

    private fun areEdgesJoined(
        a: WorldEdge,
        b: WorldEdge,
        maxEdgeDistance: Float,
        minOverlapFraction: Float,
    ): Boolean {
        val distance = lineDistanceScore(a, b)
        if (distance > maxEdgeDistance) return false
        val midpointDistance =
            pointToSegmentDistance(
                point = Vec2((a.start.x + a.end.x) / 2f, (a.start.y + a.end.y) / 2f),
                segStart = b.start,
                segEnd = b.end,
            )
        if (midpointDistance > maxEdgeDistance * 1.6f) return false
        val overlap = edgeOverlapFraction(a, b)
        return overlap >= minOverlapFraction
    }

    private fun lineDistanceScore(
        a: WorldEdge,
        b: WorldEdge,
    ): Float {
        val d1 = distance(a.start, b.end)
        val d2 = distance(a.end, b.start)
        return (d1 + d2) / 2f
    }

    private fun edgeOverlapFraction(
        a: WorldEdge,
        b: WorldEdge,
    ): Float {
        val axis = normalize(Vec2(b.end.x - b.start.x, b.end.y - b.start.y))
        val aProj = projectRangeOntoAxis(a.start, a.end, axis)
        val bProj = projectRangeOntoAxis(b.start, b.end, axis)
        val overlapStart = max(aProj.first, bProj.first)
        val overlapEnd = min(aProj.second, bProj.second)
        val overlap = (overlapEnd - overlapStart).coerceAtLeast(0f)
        val minLength = min(aProj.second - aProj.first, bProj.second - bProj.first).coerceAtLeast(0.001f)
        return (overlap / minLength).coerceIn(0f, 1f)
    }

    private fun projectRangeOntoAxis(
        a: Vec2,
        b: Vec2,
        axis: Vec2,
    ): Pair<Float, Float> {
        val pa = a.x * axis.x + a.y * axis.y
        val pb = b.x * axis.x + b.y * axis.y
        return min(pa, pb) to max(pa, pb)
    }

    private fun pointToSegmentDistance(
        point: Vec2,
        segStart: Vec2,
        segEnd: Vec2,
    ): Float {
        val vx = segEnd.x - segStart.x
        val vy = segEnd.y - segStart.y
        val wx = point.x - segStart.x
        val wy = point.y - segStart.y
        val vv = (vx * vx + vy * vy).coerceAtLeast(0.0001f)
        val t = ((wx * vx + wy * vy) / vv).coerceIn(0f, 1f)
        val projX = segStart.x + t * vx
        val projY = segStart.y + t * vy
        val dx = point.x - projX
        val dy = point.y - projY
        return sqrt(dx * dx + dy * dy)
    }

    private fun ropeIndicesNearWorldEdge(
        asset: PlacedAsset,
        preset: TentPreset,
        edgeIndex: Int,
    ): Set<Int> {
        if (asset.ropes.isEmpty()) return emptySet()
        val localPoints = localOutlinePoints(preset)
        if (localPoints.size < 2) return emptySet()
        val start = worldPoint(asset, localPoints[edgeIndex])
        val end = worldPoint(asset, localPoints[(edgeIndex + 1) % localPoints.size])
        val edgeVec = Vec2(end.x - start.x, end.y - start.y)
        val edgeLength = sqrt(edgeVec.x * edgeVec.x + edgeVec.y * edgeVec.y).coerceAtLeast(0.001f)
        return asset.ropes.indices.filterTo(mutableSetOf()) { idx ->
            val rope = asset.ropes[idx]
            val anchor = worldPoint(asset, Vec2(rope.localX, rope.localY))
            val distanceToLine = abs((anchor.x - start.x) * edgeVec.y - (anchor.y - start.y) * edgeVec.x) / edgeLength
            if (distanceToLine > 0.18f) return@filterTo false
            val projection = ((anchor.x - start.x) * edgeVec.x + (anchor.y - start.y) * edgeVec.y) / (edgeLength * edgeLength)
            projection in -0.1f..1.1f
        }
    }

    private fun anchorDirection(
        asset: PlacedAsset,
        localStart: Vec2,
        outwardAngleDeg: Float,
    ): Vec2 {
        val absolute = degToRad(asset.rotationDeg + outwardAngleDeg)
        val configured = Vec2(cos(absolute).toFloat(), sin(absolute).toFloat())
        if (configured.x == 0f && configured.y == 0f) {
            return normalize(rotatePoint(localStart, asset.rotationDeg))
        }
        return configured
    }

    private fun localOutlinePoints(preset: TentPreset): List<Vec2> {
        if (preset.polygonPoints.isNotEmpty()) return preset.polygonPoints
        val generated =
            GeometryHelper.generatePoints(
                shape = preset.shape,
                widthMeters = preset.widthMeters,
                depthMeters = preset.heightMeters,
            )
        if (generated.isNotEmpty()) return generated
        val hw = preset.widthMeters / 2f
        val hh = preset.heightMeters / 2f
        return listOf(
            Vec2(-hw, -hh),
            Vec2(hw, -hh),
            Vec2(hw, hh),
            Vec2(-hw, hh),
        )
    }

    private fun assetWorldPath(
        asset: PlacedAsset,
        preset: TentPreset,
    ): Path {
        val points = localOutlinePoints(preset)
        return Path().apply {
            if (points.isEmpty()) return@apply
            val first = worldPoint(asset, points.first())
            moveTo(first.x, first.y)
            for (i in 1 until points.size) {
                val world = worldPoint(asset, points[i])
                lineTo(world.x, world.y)
            }
            close()
        }
    }

    fun assetWorldOutline(
        asset: PlacedAsset,
        preset: TentPreset,
    ): List<Vec2> {
        val points = localOutlinePoints(preset)
        if (points.isEmpty()) return emptyList()
        return points.map { worldPoint(asset, it) }
    }

    private fun pointInPolygon(
        point: Vec2,
        polygon: List<Vec2>,
    ): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            val intersects =
                (pi.y > point.y) != (pj.y > point.y) &&
                    point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    internal fun polygonsIntersect(
        a: List<Vec2>,
        b: List<Vec2>,
    ): Boolean {
        if (a.size < 3 || b.size < 3) return false

        for (i in a.indices) {
            val a1 = a[i]
            val a2 = a[(i + 1) % a.size]
            for (j in b.indices) {
                val b1 = b[j]
                val b2 = b[(j + 1) % b.size]
                if (linesIntersect(a1, a2, b1, b2)) return true
            }
        }

        if (pointInPolygon(a.first(), b)) return true
        if (pointInPolygon(b.first(), a)) return true
        return false
    }

    private fun aabbOverlap(
        a: Rect,
        b: Rect,
    ): Boolean = a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

    private fun aabbDistanceMeters(
        a: Rect,
        b: Rect,
    ): Float {
        val dx = max(0f, max(a.left - b.right, b.left - a.right))
        val dy = max(0f, max(a.top - b.bottom, b.top - a.bottom))
        return sqrt(dx * dx + dy * dy)
    }

    private fun heightOverlaps(
        a: HeightRange,
        b: HeightRange,
    ): Boolean {
        val lower = max(a.minMeters, b.minMeters)
        val upper = min(a.maxMeters, b.maxMeters)
        return upper - lower > 0.02f
    }

    private fun formatFloat(
        value: Float,
        decimals: Int,
    ): String = "%.${decimals}f".format(value)
}
