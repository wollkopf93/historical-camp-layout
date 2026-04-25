package de.voculupo.historicalcamplayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

data class RopeSegment(val start: Vec2, val end: Vec2)

data class CollisionReport(
    val hardCollisions: Set<String> = emptySet(),
    val warningCollisions: Set<String> = emptySet(),
    val messages: List<String> = emptyList(),
)

object GeometryEngine {
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
                    val a = i * (2f * Math.PI.toFloat() / 8f)
                    Vec2(cos(a) * r, sin(a) * r)
                }
            }
            TentShapeType.RECTANGLE, TentShapeType.A_FRAME -> {
                val hw = preset.widthMeters / 2f
                val hh = preset.heightMeters / 2f
                listOf(
                    Vec2(-hw, -hh), Vec2(0f, -hh), Vec2(hw, -hh),
                    Vec2(hw, 0f),
                    Vec2(hw, hh), Vec2(0f, hh), Vec2(-hw, hh),
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
                val pts = if (preset.polygonPoints.isNotEmpty()) preset.polygonPoints else {
                    val hw = preset.widthMeters / 2f
                    val hh = preset.heightMeters / 2f
                    listOf(Vec2(-hw, -hh), Vec2(hw, -hh), Vec2(hw, hh), Vec2(-hw, hh))
                }
                val mids = pts.indices.map { i ->
                    val a = pts[i]
                    val b = pts[(i + 1) % pts.size]
                    Vec2((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                }
                (pts + mids).take(10)
            }
        }
    }

    fun snapLocalAnchorToPreset(local: Vec2, preset: TentPreset): Vec2 {
        val slots = anchorSlotsForPreset(preset)
        return slots.minByOrNull { slot -> distance(slot, local) } ?: local
    }

    fun evaluate(project: PlannerProject, presets: List<TentPreset>): CollisionReport {
        val byId = presets.associateBy { it.id }
        val hard = mutableSetOf<String>()
        val warn = mutableSetOf<String>()
        val messages = mutableListOf<String>()
        val geometries = project.assets.mapNotNull { asset ->
            val preset = byId[asset.presetId] ?: return@mapNotNull null
            AssetGeometry(
                asset = asset,
                preset = preset,
                bodyAabb = assetBodyAabb(asset, preset),
                paddedAabb = assetAabb(asset, preset),
                bodyPath = assetWorldPath(asset, preset),
            )
        }

        geometries.forEach { geo ->
            // Parcel border warnings should refer to the physical tent footprint,
            // not the optional safety margin buffer, to avoid false positives.
            val bounds = geo.bodyAabb
            if (bounds.left < 0f || bounds.top < 0f || bounds.right > project.parcel.widthMeters || bounds.bottom > project.parcel.heightMeters) {
                warn += geo.asset.id
                messages += "Asset ${geo.preset.name} ragt aus der Parzelle."
            }
        }

        for (i in geometries.indices) {
            for (j in i + 1 until geometries.size) {
                val a = geometries[i]
                val b = geometries[j]
                val distance = aabbDistanceMeters(a.paddedAabb, b.paddedAabb)
                if (isHardCollision(a, b)) {
                    hard += a.asset.id
                    hard += b.asset.id
                    messages += "Harte Kollision zwischen ${a.preset.name} und ${b.preset.name}."
                } else if (isRopeIntrusionCollision(a.asset, a.preset, b.asset, b.preset)) {
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
                val assetPolygon = assetWorldOutlinePoints(geo.asset, geo.preset)
                if (polygonsIntersect(assetPolygon, zonePolygon)) {
                    hard += geo.asset.id
                    messages += "Sperrzone verletzt: ${geo.preset.name} liegt in einer Sperrzone."
                }
            }
        }

        return CollisionReport(hard, warn, messages.distinct())
    }

    fun metersToOffset(v: Vec2, pixelsPerMeter: Float): Offset = Offset(v.x * pixelsPerMeter, v.y * pixelsPerMeter)

    fun offsetToMeters(offset: Offset, pixelsPerMeter: Float): Vec2 = Vec2(offset.x / pixelsPerMeter, offset.y / pixelsPerMeter)

    fun rotatePoint(local: Vec2, rotationDeg: Float): Vec2 {
        val rad = Math.toRadians(rotationDeg.toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()
        return Vec2(
            local.x * cos - local.y * sin,
            local.x * sin + local.y * cos,
        )
    }

    fun worldPoint(asset: PlacedAsset, local: Vec2): Vec2 {
        val r = rotatePoint(local, asset.rotationDeg)
        return Vec2(asset.position.x + r.x, asset.position.y + r.y)
    }

    /**
     * Precise hit test for rotated assets.
     * Transforms world point into asset local-space, then runs point-in-polygon.
     */
    fun isPointInAsset(worldPoint: Vec2, asset: PlacedAsset, preset: TentPreset): Boolean {
        val translatedX = worldPoint.x - asset.position.x
        val translatedY = worldPoint.y - asset.position.y

        val rad = Math.toRadians((-asset.rotationDeg).toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val localPoint = Vec2(
            x = translatedX * c - translatedY * s,
            y = translatedX * s + translatedY * c,
        )

        val outline = localOutlinePoints(preset)
        if (outline.isEmpty()) return false

        var inside = false
        var j = outline.lastIndex
        for (i in outline.indices) {
            val pi = outline[i]
            val pj = outline[j]
            val denom = pj.y - pi.y
            val intersects = (pi.y > localPoint.y) != (pj.y > localPoint.y) &&
                localPoint.x < (pj.x - pi.x) * (localPoint.y - pi.y) / denom + pi.x
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    fun ropeSegments(asset: PlacedAsset, preset: TentPreset): List<RopeSegment> {
        if (!asset.showRopes) return emptyList()
        val ropes = if (asset.ropes.isEmpty()) {
            preset.ropeAnchors.map {
                AssetRope(
                    localX = it.localX,
                    localY = it.localY,
                    lengthMeters = it.lengthMeters,
                    outwardAngleDeg = it.outwardAngleDeg ?: 0f,
                )
            }
        } else asset.ropes
        return ropes.map { rope ->
            val localStart = Vec2(rope.localX, rope.localY)
            val start = worldPoint(asset, localStart)
            val normal = anchorDirection(asset, localStart, rope.outwardAngleDeg)
            val end = Vec2(
                start.x + normal.x * rope.lengthMeters,
                start.y + normal.y * rope.lengthMeters,
            )
            RopeSegment(start, end)
        }
    }

    fun assetBodyAabb(asset: PlacedAsset, preset: TentPreset): Rect {
        val (halfW, halfH) = bodyHalfExtents(preset)
        return Rect(
            left = asset.position.x - halfW,
            top = asset.position.y - halfH,
            right = asset.position.x + halfW,
            bottom = asset.position.y + halfH,
        )
    }

    fun assetAabb(asset: PlacedAsset, preset: TentPreset): Rect {
        val (baseHalfW, baseHalfH) = bodyHalfExtents(preset)
        val halfW = baseHalfW + asset.safetyMarginMeters
        val halfH = baseHalfH + asset.safetyMarginMeters
        return Rect(
            left = asset.position.x - halfW,
            top = asset.position.y - halfH,
            right = asset.position.x + halfW,
            bottom = asset.position.y + halfH,
        )
    }

    fun snapToGrid(point: Vec2, grid: Float): Vec2 {
        if (grid <= 0f) return point
        return Vec2(
            round(point.x / grid) * grid,
            round(point.y / grid) * grid,
        )
    }

    fun distance(a: Vec2, b: Vec2): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun isHardCollision(a: AssetGeometry, b: AssetGeometry): Boolean {
        if (!heightOverlaps(a.asset.heightRange, b.asset.heightRange)) return false
        if (!aabbOverlap(a.bodyAabb, b.bodyAabb)) return false
        return CollisionEngine.isColliding(a.asset, b.asset, a.bodyPath, b.bodyPath)
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
    private fun isRopeIntrusionCollision(a: PlacedAsset, presetA: TentPreset, b: PlacedAsset, presetB: TentPreset): Boolean {
        val ropesA = ropeSegments(a, presetA)
        val ropesB = ropeSegments(b, presetB)

        val bodyPolyA = assetWorldOutlinePoints(a, presetA)
        val bodyPolyB = assetWorldOutlinePoints(b, presetB)

        val ropeAIntoBodyB = ropesA.any { lineIntersectsPolygon(it, bodyPolyB) } &&
            heightOverlaps(heightRangeForRopeOwnerVsOtherBody(a, presetA), b.heightRange)
        val ropeBIntoBodyA = ropesB.any { lineIntersectsPolygon(it, bodyPolyA) } &&
            heightOverlaps(heightRangeForRopeOwnerVsOtherBody(b, presetB), a.heightRange)
        return ropeAIntoBodyB || ropeBIntoBodyA
    }

    private fun isSunSailPreset(preset: TentPreset): Boolean = preset.id.startsWith("sun_sail_")

    /**
     * Vertical span used when this asset's *ropes* are tested against another asset's body.
     * Sonnensegel stakes sit near the ground while [PlacedAsset.heightRange] describes the canopy.
     */
    private fun heightRangeForRopeOwnerVsOtherBody(ropeOwner: PlacedAsset, ropeOwnerPreset: TentPreset): HeightRange {
        if (!isSunSailPreset(ropeOwnerPreset)) return ropeOwner.heightRange
        val upper = ropeOwner.heightRange.maxMeters.coerceIn(2.2f, 3.6f)
        return HeightRange(minMeters = 0f, maxMeters = upper)
    }

    private fun lineIntersectsPolygon(seg: RopeSegment, polygon: List<Vec2>): Boolean {
        if (polygon.size < 3) return false
        if (pointInPolygon(seg.start, polygon) || pointInPolygon(seg.end, polygon)) return true
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            if (linesIntersect(seg.start, seg.end, a, b)) return true
        }
        return false
    }

    private fun linesIntersect(a1: Vec2, a2: Vec2, b1: Vec2, b2: Vec2): Boolean {
        val eps = 0.0001f
        fun orient(p: Vec2, q: Vec2, r: Vec2): Float =
            (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)
        fun isZero(v: Float): Boolean = kotlin.math.abs(v) <= eps
        fun onSegment(p: Vec2, q: Vec2, r: Vec2): Boolean {
            return q.x <= max(p.x, r.x) + eps && q.x + eps >= min(p.x, r.x) &&
                q.y <= max(p.y, r.y) + eps && q.y + eps >= min(p.y, r.y)
        }

        val o1 = orient(a1, a2, b1)
        val o2 = orient(a1, a2, b2)
        val o3 = orient(b1, b2, a1)
        val o4 = orient(b1, b2, a2)

        if ((o1 > eps && o2 < -eps || o1 < -eps && o2 > eps) &&
            (o3 > eps && o4 < -eps || o3 < -eps && o4 > eps)
        ) return true

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

    private fun anchorDirection(asset: PlacedAsset, localStart: Vec2, outwardAngleDeg: Float): Vec2 {
        val absolute = Math.toRadians((asset.rotationDeg + outwardAngleDeg).toDouble())
        val configured = Vec2(cos(absolute).toFloat(), sin(absolute).toFloat())
        if (configured.x == 0f && configured.y == 0f) {
            return normalize(rotatePoint(localStart, asset.rotationDeg))
        }
        return configured
    }

    private fun bodyHalfExtents(preset: TentPreset): Pair<Float, Float> {
        val points = localOutlinePoints(preset)
        if (points.isEmpty()) return (preset.widthMeters / 2f) to (preset.heightMeters / 2f)
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        return ((maxX - minX) / 2f) to ((maxY - minY) / 2f)
    }

    private fun localOutlinePoints(preset: TentPreset): List<Vec2> {
        if (preset.polygonPoints.isNotEmpty()) return preset.polygonPoints
        val generated = GeometryHelper.generatePoints(
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

    private fun assetWorldPath(asset: PlacedAsset, preset: TentPreset): Path {
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

    private fun assetWorldOutlinePoints(asset: PlacedAsset, preset: TentPreset): List<Vec2> {
        val points = localOutlinePoints(preset)
        if (points.isEmpty()) return emptyList()
        return points.map { worldPoint(asset, it) }
    }

    private fun pointInPolygon(point: Vec2, polygon: List<Vec2>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            val intersects = (pi.y > point.y) != (pj.y > point.y) &&
                point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    private fun polygonsIntersect(a: List<Vec2>, b: List<Vec2>): Boolean {
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

    private fun aabbOverlap(a: Rect, b: Rect): Boolean {
        return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
    }

    private fun aabbDistanceMeters(a: Rect, b: Rect): Float {
        val dx = max(0f, max(a.left - b.right, b.left - a.right))
        val dy = max(0f, max(a.top - b.bottom, b.top - a.bottom))
        return sqrt(dx * dx + dy * dy)
    }

    private fun heightOverlaps(a: HeightRange, b: HeightRange): Boolean {
        val lower = max(a.minMeters, b.minMeters)
        val upper = min(a.maxMeters, b.maxMeters)
        return upper - lower > 0.02f
    }

    private fun formatFloat(value: Float, decimals: Int): String = "%.${decimals}f".format(value)
}

