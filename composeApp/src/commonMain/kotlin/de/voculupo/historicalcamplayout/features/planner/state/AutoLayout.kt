package de.voculupo.historicalcamplayout.features.planner.state

import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.Parcel
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class VisitorSide { TOP, RIGHT, BOTTOM, LEFT }

enum class RingShape(
    val arcDeg: Float,
) {
    HORSESHOE(240f),
    FULL_CIRCLE(360f),
    HALF_CIRCLE(180f),
}

data class AutoSetupRequest(
    val parcel: Parcel,
    val tentPresetIds: List<String>,
    val sailPresetIds: List<String>,
    val visitorSide: VisitorSide,
    val shape: RingShape = RingShape.HORSESHOE,
    val joinSails: Boolean = false,
    val replaceExisting: Boolean = true,
)

/**
 * Builds a camp draft: sun sails near the visitor edge, tents in a ring around each sail with
 * their entry (local -y, like the Saxon porch) facing the sail, the open side toward the visitors.
 * Spacing uses each asset's outline plus rope ends, so ropes never reach into another body.
 * If the camp is bigger than the parcel it overflows; the normal parcel warnings then show that.
 */
internal object AutoLayout {
    /** Walkway between footprints; matches the 1.5 m warning in [GeometryEngine.evaluate]. */
    const val CLEAR = 1.5f

    private class Cluster(
        val assets: List<Pair<PlacedAsset, TentPreset>>,
    )

    fun generate(
        req: AutoSetupRequest,
        presetsById: Map<String, TentPreset>,
        newId: (prefix: String) -> String,
    ): List<PlacedAsset> {
        val tents =
            req.tentPresetIds
                .mapNotNull { presetsById[it] }
                .filterNot(GeometryEngine::isSunSailPreset)
                .sortedByDescending { it.widthMeters * it.heightMeters }
        val sails = req.sailPresetIds.mapNotNull { presetsById[it] }.filter(GeometryEngine::isSunSailPreset)
        if (tents.isEmpty() && sails.isEmpty()) return emptyList()

        val sailGroups =
            when {
                sails.isEmpty() -> listOf(emptyList())
                req.joinSails -> listOf(sails)
                else -> sails.map { listOf(it) }
            }
        // Round-robin keeps the big tents spread across the rings.
        val clusters =
            sailGroups.mapIndexed { k, group ->
                buildCluster(group, tents.filterIndexed { i, _ -> i % sailGroups.size == k }, req.shape)
            }

        // Canonical frame: visitors on the top edge. Rotated into the real frame at the end.
        val sideways = req.visitorSide == VisitorSide.LEFT || req.visitorSide == VisitorSide.RIGHT
        val canonicalWidth = if (sideways) req.parcel.heightMeters else req.parcel.widthMeters
        // Padded boxes too, so the walkway check between neighbouring rings stays quiet.
        val bounds =
            clusters.map { cluster ->
                bounds(
                    cluster.assets.flatMap { (a, p) ->
                        val box = GeometryEngine.assetAabb(a, p)
                        footprint(a, p) + Vec2(box.left, box.top) + Vec2(box.right, box.bottom)
                    },
                )
            }
        val totalWidth = bounds.sumOf { (it.right - it.left).toDouble() }.toFloat() + CLEAR * (clusters.size - 1)
        var cursor = (canonicalWidth - totalWidth) / 2f

        val result = mutableListOf<PlacedAsset>()
        clusters.forEachIndexed { k, cluster ->
            val b = bounds[k]
            val dx = cursor - b.left
            val dy = CLEAR - b.top
            cursor += (b.right - b.left) + CLEAR
            val groupId = if (cluster.assets.size > 1) newId("group-") else null
            cluster.assets.forEach { (asset, _) ->
                val moved = Vec2(asset.position.x + dx, asset.position.y + dy)
                result +=
                    asset.copy(
                        id = newId(""),
                        groupId = groupId,
                        position = toRealFrame(moved, req.visitorSide, req.parcel),
                        rotationDeg = wrapDegrees(asset.rotationDeg + sideAngle(req.visitorSide)),
                    )
            }
        }
        return result
    }

    private fun buildCluster(
        sails: List<TentPreset>,
        tents: List<TentPreset>,
        shape: RingShape,
    ): Cluster {
        // Sails edge to edge in a row so joined sails lace together (shared edges hide their ropes).
        val sailWidths = sails.map { bounds(GeometryEngine.assetWorldOutline(place(it, Vec2(0f, 0f)), it)) }
        var x = -sailWidths.sumOf { (it.right - it.left).toDouble() }.toFloat() / 2f
        val placedSails =
            sails.mapIndexed { i, preset ->
                val b = sailWidths[i]
                val asset = place(preset, Vec2(x - b.left, -(b.top + b.bottom) / 2f))
                x += b.right - b.left
                asset to preset
            }
        val sailRadius =
            placedSails
                .flatMap { (a, p) -> footprint(a, p) }
                .maxOfOrNull { hypot(it.x, it.y) } ?: CLEAR

        // Footprint of each tent at rotation 0: x runs along the ring, -y points at the sail.
        val shapes = tents.map { footprint(place(it, Vec2(0f, 0f)), it) }
        val inward = shapes.map { points -> (-points.minOf { it.y }).coerceAtLeast(0f) }
        val n = tents.size
        if (n == 0) return Cluster(placedSails)

        val arc = shape.arcDeg / 180f * PI.toFloat()
        val fullCircle = shape == RingShape.FULL_CIRCLE
        val gapCount = if (fullCircle) n else n - 1

        // r is the inner circle every tent's closest point sits on, so small tents stay close to the sail.
        // A tent's wedge is the widest angle any of its points (outline or rope end) reaches from the centre.
        fun wedges(r: Float) = shapes.mapIndexed { i, points -> 2f * points.maxOf { atan2(abs(it.x), r + inward[i] + it.y) } }

        // Points on either side of this angle, both at least r out, are at least CLEAR apart.
        fun gap(r: Float) = asin((CLEAR / r).coerceAtMost(1f))

        fun needed(r: Float) = wedges(r).sum() + gapCount * gap(r)

        // Biggest tent at the back centre, the rest alternating left and right of it.
        val order = ArrayDeque<Int>()
        tents.indices.forEach { if (it % 2 == 0) order.addLast(it) else order.addFirst(it) }

        fun ring(radius: Float): List<Pair<PlacedAsset, TentPreset>> {
            val wedges = wedges(radius)
            // Spread leftover arc evenly so a horseshoe really opens to its full width.
            val step = gap(radius) + if (gapCount > 0) (arc - needed(radius)) / gapCount else 0f
            val span = wedges.sum() + if (fullCircle) 0f else (n - 1) * step
            var theta = (PI / 2).toFloat() - span / 2f
            return order.map { i ->
                val center = theta + wedges[i] / 2f
                theta += wedges[i] + step
                val preset = tents[i]
                val r = radius + inward[i]
                // rotationDeg = theta - 90 turns local -y toward the ring centre.
                place(preset, Vec2(r * cos(center), r * sin(center)), center * 180f / PI.toFloat() - 90f) to preset
            }
        }

        var radius = sailRadius + CLEAR
        while (needed(radius) > arc) radius += 0.25f
        // The geometric spacing above is exact; the app's walkway warning measures axis-aligned boxes,
        // which grow when tents are rotated. Widen until the real checker is quiet (bounded).
        var assets = placedSails + ring(radius)
        repeat(MAX_WIDEN_STEPS) {
            if (isClean(assets)) return Cluster(assets)
            radius += 0.25f
            assets = placedSails + ring(radius)
        }
        return Cluster(assets)
    }

    private const val MAX_WIDEN_STEPS = 80

    /** No hard collision and no warning between the cluster's own assets (parcel border ignored). */
    private fun isClean(assets: List<Pair<PlacedAsset, TentPreset>>): Boolean {
        val offset = 1000f
        val project =
            PlannerProject(
                parcel = Parcel(2 * offset, 2 * offset),
                assets =
                    assets.mapIndexed { i, (a, _) ->
                        a.copy(id = "$i", position = Vec2(a.position.x + offset, a.position.y + offset))
                    },
            )
        val report = GeometryEngine.evaluate(project, assets.map { it.second }.distinctBy { it.id })
        return report.hardCollisions.isEmpty() && report.warningCollisions.isEmpty()
    }

    private fun place(
        preset: TentPreset,
        position: Vec2,
        rotationDeg: Float = 0f,
    ) = PlacedAsset(
        id = "",
        presetId = preset.id,
        label = preset.name,
        position = position,
        rotationDeg = rotationDeg,
        safetyMarginMeters = preset.defaultSafetyMarginMeters,
        heightRange = preset.defaultHeightRange,
        ropes = PlannerDomainOps.defaultRopes(preset),
    )

    private fun footprint(
        asset: PlacedAsset,
        preset: TentPreset,
    ): List<Vec2> = GeometryEngine.assetWorldOutline(asset, preset) + GeometryEngine.ropeSegments(asset).map { it.end }

    private data class Box(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    private fun bounds(points: List<Vec2>) =
        Box(
            left = points.minOf { it.x },
            top = points.minOf { it.y },
            right = points.maxOf { it.x },
            bottom = points.maxOf { it.y },
        )

    private fun sideAngle(side: VisitorSide) =
        when (side) {
            VisitorSide.TOP -> 0f
            VisitorSide.RIGHT -> 90f
            VisitorSide.BOTTOM -> 180f
            VisitorSide.LEFT -> 270f
        }

    /** Rotates a canonical (visitors on top) point so the visitor edge becomes [side]. */
    private fun toRealFrame(
        p: Vec2,
        side: VisitorSide,
        parcel: Parcel,
    ) = when (side) {
        VisitorSide.TOP -> p
        VisitorSide.RIGHT -> Vec2(parcel.widthMeters - p.y, p.x)
        VisitorSide.BOTTOM -> Vec2(parcel.widthMeters - p.x, parcel.heightMeters - p.y)
        VisitorSide.LEFT -> Vec2(p.y, parcel.heightMeters - p.x)
    }

    private fun wrapDegrees(deg: Float) = ((deg % 360f) + 360f) % 360f
}
