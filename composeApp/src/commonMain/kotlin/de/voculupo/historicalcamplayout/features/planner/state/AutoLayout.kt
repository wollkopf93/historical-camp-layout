package de.voculupo.historicalcamplayout.features.planner.state

import androidx.compose.ui.geometry.Rect
import de.voculupo.historicalcamplayout.AutoSetupRequest
import de.voculupo.historicalcamplayout.BlockZone
import de.voculupo.historicalcamplayout.FreeArea
import de.voculupo.historicalcamplayout.FreeAreaKind
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.Parcel
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.RingShape
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.Vec2
import de.voculupo.historicalcamplayout.VisitorSide
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** A draggable part of the draft: a sail ring (index into clusterPins) or a free area (index into freeAreas). */
data class BlockKey(
    val isRing: Boolean,
    val index: Int,
)

data class LayoutBlock(
    val key: BlockKey,
    val anchor: Vec2,
    /** What you grab in the preview: the sails/free area, or the tents of a ring without a centre. */
    val bounds: Rect,
    val assetIds: Set<String>,
    val zoneIds: Set<String>,
)

data class AutoLayoutResult(
    val assets: List<PlacedAsset>,
    val zones: List<BlockZone>,
    /** Names of tents that did not fit, lowest priority last in the request. */
    val skipped: List<String>,
    val walkwayMeters: Float,
    /** Suggested parcel size when the draft had to compress or drop tents. */
    val neededParcel: Parcel?,
    val blocks: List<LayoutBlock>,
    /** Tents that had to leave their spot in the arrangement to fit (search fallback). */
    val displaced: Int = 0,
)

/**
 * Builds a camp draft. Rings: sun sails (or a fire) in the middle, tents around them with the entry
 * (local -y, like the Saxon porch) facing the centre, the open side toward the visitors. Free areas
 * become block zones. Rings, lanes and areas sit in a row along the visitor edge unless pinned.
 * Tents are then fitted into the parcel in priority order; when space runs out the walkway shrinks
 * step by step (down to cloth to cloth) and the lowest-priority tents are left out.
 */
internal object AutoLayout {
    /** Walkway between footprints; matches the 1.5 m warning in [GeometryEngine.evaluate]. */
    const val CLEAR = 1.5f

    /**
     * Walkways tried when tents don't fit: guy ropes plus 1 m to pass, then cloth to cloth (common when
     * space is short). Ropes still never run into another tent's body; that stays a hard collision.
     */
    private val COMPRESS_STEPS = listOf(1.0f, 0f)

    /** Id prefix of zones the auto setup owns; regenerating replaces them, hand-drawn zones stay. */
    const val AUTO_PREFIX = "auto-"

    private const val MAX_WIDEN_STEPS = 80
    private const val SWING_STEP_DEG = 15f
    private const val SEARCH_STEP = 0.5f
    private const val FIRE_SIDES = 12
    private const val HUGE = 1000f

    fun generate(
        req: AutoSetupRequest,
        presetsById: Map<String, TentPreset>,
        obstacleZones: List<BlockZone> = emptyList(),
        obstacleAssets: List<PlacedAsset> = emptyList(),
        newId: (prefix: String) -> String,
    ): AutoLayoutResult {
        var result = layout(req, presetsById, obstacleZones, obstacleAssets, req.walkwayMeters, newId)
        for (clear in COMPRESS_STEPS.filter { it < req.walkwayMeters }) {
            if (result.skipped.isEmpty() && result.displaced == 0) break
            val tight = layout(req, presetsById, obstacleZones, obstacleAssets, clear, newId)
            // Fewer dropped tents first, then a tidier arrangement (fewer tents scattered to fit).
            if (compareValuesBy(tight, result, { it.skipped.size }, { it.displaced }) < 0) result = tight
        }
        val cramped = result.skipped.isNotEmpty() || result.displaced > 0 || result.walkwayMeters < req.walkwayMeters
        return if (cramped) result.copy(neededParcel = neededParcel(req, presetsById)) else result
    }

    /** Rings the request produces: one per sail (or one for joined sails), one per fire; at least one. */
    fun ringCount(
        req: AutoSetupRequest,
        presetsById: Map<String, TentPreset>,
    ): Int {
        val sails = req.sailPresetIds.count { id -> presetsById[id]?.let(GeometryEngine::isSunSailPreset) == true }
        val sailRings = if (req.joinSails) minOf(sails, 1) else sails
        return max(1, sailRings + req.freeAreas.count { it.kind == FreeAreaKind.FIRE })
    }

    /** Size of the unpinned draft on an unlimited parcel, plus walkway margins. */
    private fun neededParcel(
        req: AutoSetupRequest,
        presetsById: Map<String, TentPreset>,
    ): Parcel {
        val free =
            layout(
                req.copy(
                    parcel = Parcel(HUGE, HUGE),
                    visitorSide = VisitorSide.TOP,
                    clusterPins = emptyList(),
                    freeAreas = req.freeAreas.map { it.copy(position = null) },
                ),
                presetsById,
                emptyList(),
                emptyList(),
                req.walkwayMeters,
            ) { it }
        val laneZones =
            free.blocks
                .filter { !it.key.isRing && req.freeAreas[it.key.index].kind == FreeAreaKind.LANE }
                .flatMap { it.zoneIds }
                .toSet()
        val presets = free.assets.map { presetsById.getValue(it.presetId) }
        val points =
            free.assets.zip(presets).flatMap { (a, p) -> footprint(a, p) } +
                free.zones.filterNot { it.id in laneZones }.flatMap { it.points }
        val xs = points.map { it.x } + free.zones.filter { it.id in laneZones }.flatMap { z -> z.points.map { it.x } }
        if (points.isEmpty()) return req.parcel
        val width = ceil(xs.max() - xs.min() + 2 * req.walkwayMeters)
        val depth = ceil(points.maxOf { it.y } + req.walkwayMeters)
        val sideways = req.visitorSide == VisitorSide.LEFT || req.visitorSide == VisitorSide.RIGHT
        return if (sideways) Parcel(depth, width) else Parcel(width, depth)
    }

    /** One draggable unit, built in the canonical frame (visitors at -y) around its anchor. */
    private class Block(
        val key: BlockKey,
        val pin: Vec2?,
        val fixed: List<Pair<PlacedAsset, TentPreset>>,
        /** Priority index (position in the request) to tent at its ideal ring spot. */
        val tents: List<Pair<Int, Pair<PlacedAsset, TentPreset>>>,
        val zones: List<Pair<List<Vec2>, String>>,
        val isLane: Boolean = false,
    )

    /**
     * Something a tent must keep clear of. [body] is the solid part (walkway is measured to it), [reach]
     * adds guy ropes; [asset] is set for tents and sails so rope overlaps can be checked exactly.
     * [ringSpaced]: the ring construction already spaced it from its ring's ideally placed tents.
     */
    private class Obstacle(
        val body: Rect,
        val reach: Rect,
        val asset: Pair<PlacedAsset, TentPreset>?,
        val owner: Block?,
        val ringSpaced: Boolean = false,
    )

    private fun zoneObstacle(
        points: List<Vec2>,
        owner: Block?,
    ) = bounds(points).let { Obstacle(it, it, null, owner, ringSpaced = owner != null) }

    private fun assetObstacle(
        asset: PlacedAsset,
        preset: TentPreset,
        owner: Block?,
        ringSpaced: Boolean,
        clear: Float,
    ) = Obstacle(
        spacingBox(asset, preset, clear),
        box(asset, preset, withRopes = !swingsRopes(preset, clear)),
        asset to preset,
        owner,
        ringSpaced,
    )

    /** Cloth to cloth: tent ropes are staked behind the neighbour instead of keeping tents apart. Sails keep theirs. */
    private fun swingsRopes(
        preset: TentPreset,
        clear: Float,
    ) = clear <= 0f && !GeometryEngine.isSunSailPreset(preset)

    private fun layout(
        req: AutoSetupRequest,
        presetsById: Map<String, TentPreset>,
        obstacleZones: List<BlockZone>,
        obstacleAssets: List<PlacedAsset>,
        clear: Float,
        newId: (prefix: String) -> String,
    ): AutoLayoutResult {
        val parcel = req.parcel
        val side = req.visitorSide
        val sideways = side == VisitorSide.LEFT || side == VisitorSide.RIGHT
        val canonicalWidth = if (sideways) parcel.heightMeters else parcel.widthMeters
        val canonicalDepth = if (sideways) parcel.widthMeters else parcel.heightMeters
        val angle = sideAngle(side)

        val tents =
            req.tentPresetIds.mapIndexedNotNull { i, id ->
                presetsById[id]?.takeUnless(GeometryEngine::isSunSailPreset)?.let { i to it }
            }
        val sails = req.sailPresetIds.mapNotNull { presetsById[it] }.filter(GeometryEngine::isSunSailPreset)
        val sailGroups =
            when {
                sails.isEmpty() -> emptyList()
                req.joinSails -> listOf(sails)
                else -> sails.map { listOf(it) }
            }
        val fires = req.freeAreas.withIndex().filter { it.value.kind == FreeAreaKind.FIRE }
        // Ring centres: sail groups, then fires; with neither, one ring around an empty centre.
        val ringCount = max(1, sailGroups.size + fires.size)
        val members = assignRings(tents, req.tentRings, ringCount)

        val rings =
            (0 until ringCount).map { r ->
                val fire = if (r >= sailGroups.size) fires.getOrNull(r - sailGroups.size) else null
                buildRing(
                    key = if (fire != null) BlockKey(false, fire.index) else BlockKey(true, r),
                    pin = fire?.value?.position ?: req.clusterPins.getOrNull(r),
                    sails = sailGroups.getOrNull(r).orEmpty(),
                    fire = fire?.value,
                    tents = members[r],
                    shape = req.shape,
                    clear = clear,
                )
            }
        val areas =
            req.freeAreas.withIndex().filter { it.value.kind != FreeAreaKind.FIRE }.map { (i, area) ->
                val lane = area.kind == FreeAreaKind.LANE
                val depth = if (lane) canonicalDepth else area.depthMeters
                val zone = rect(area.widthMeters, depth) to area.label
                Block(BlockKey(false, i), area.position, emptyList(), emptyList(), listOf(zone), lane)
            }
        val lanes = areas.filter { it.isLane }
        // Lanes run between the rings; arenas and other areas go beside the camp.
        val row =
            (0 until max(rings.size, lanes.size)).flatMap { listOfNotNull(rings.getOrNull(it), lanes.getOrNull(it)) } +
                areas.filterNot { it.isLane }

        val anchors = mutableMapOf<Block, Vec2>()
        val unpinned = row.filter { it.pin == null }
        val localBounds = unpinned.associateWith { localBounds(it) }
        val rowWidth = unpinned.sumOf { localBounds.getValue(it).width.toDouble() }.toFloat() + clear * (unpinned.size - 1)
        var cursor = (canonicalWidth - rowWidth) / 2f
        unpinned.forEach { block ->
            val b = localBounds.getValue(block)
            val top = if (block.isLane) 0f else clear
            anchors[block] = toRealFrame(Vec2(cursor - b.left, top - b.top), side, parcel)
            cursor += b.width + clear
        }
        row.filter { it.pin != null }.forEach { anchors[it] = it.pin!! }

        // Sails and zones are solid: push each block so they sit inside the parcel.
        row.forEach { block ->
            val anchor = anchors.getValue(block)
            val solid =
                block.fixed.flatMap { (a, p) -> GeometryEngine.assetWorldOutline(transform(a, anchor, angle), p) } +
                    block.zones.flatMap { (poly, _) -> poly.map { transform(it, anchor, angle) } }
            if (solid.isNotEmpty()) {
                val b = bounds(solid)
                anchors[block] =
                    Vec2(
                        anchor.x + shiftInto(b.left, b.right, parcel.widthMeters),
                        anchor.y + shiftInto(b.top, b.bottom, parcel.heightMeters),
                    )
            }
        }

        val obstacles = mutableListOf<Obstacle>()
        obstacleZones.filter { it.points.size >= 3 }.forEach { obstacles += zoneObstacle(it.points, null) }
        obstacleAssets.forEach { a -> presetsById[a.presetId]?.let { obstacles += assetObstacle(a, it, null, false, clear) } }

        val zonesOut = mutableListOf<BlockZone>()
        val zoneOwner = mutableMapOf<String, Block>()
        val fixedOut = mutableListOf<Pair<PlacedAsset, Block>>()
        row.forEach { block ->
            val anchor = anchors.getValue(block)
            block.fixed.forEach { (a, p) ->
                val placed = transform(a, anchor, angle).copy(id = newId(""))
                fixedOut += placed to block
                obstacles += assetObstacle(placed, p, block, true, clear)
            }
            block.zones.forEach { (poly, label) ->
                val zone = BlockZone(AUTO_PREFIX + newId(""), poly.map { transform(it, anchor, angle) }, label)
                zonesOut += zone
                zoneOwner[zone.id] = block
                obstacles += zoneObstacle(zone.points, block)
            }
        }

        val tentsOut = mutableListOf<Pair<PlacedAsset, Block>>()
        val skipped = mutableListOf<String>()
        var displaced = 0
        val searchRadius = hypot(parcel.widthMeters, parcel.heightMeters)
        row
            .flatMap { block -> block.tents.map { Triple(it.first, block, it.second) } }
            .sortedBy { it.first }
            .forEach { (_, block, tent) ->
                val (local, preset) = tent
                val anchor = anchors.getValue(block)
                val ideal = transform(local, anchor, angle)
                val placed =
                    fits(ideal, preset, block, true, obstacles, parcel, clear)
                        ?: search(ideal, preset, block, anchor, obstacles, parcel, clear, searchRadius)
                if (placed == null) {
                    skipped += preset.name
                } else {
                    if (placed.position != ideal.position) displaced++
                    val withId = placed.copy(id = newId(""))
                    tentsOut += withId to block
                    obstacles += assetObstacle(withId, preset, block, placed.position == ideal.position, clear)
                }
            }

        if (clear <= 0f) {
            val outlines =
                (fixedOut + tentsOut).map { (a, _) ->
                    a.id to
                        GeometryEngine.assetWorldOutline(a, presetsById.getValue(a.presetId))
                }
            tentsOut.replaceAll { (a, block) -> swingRopes(a, outlines.filter { it.first != a.id }.map { it.second }) to block }
        }
        val all = fixedOut + tentsOut
        val groups = row.associateWith { block -> if (all.count { it.second === block } > 1) newId("group-") else null }
        val assets = all.map { (a, block) -> a.copy(groupId = groups[block]) }
        val blocks =
            row.map { block ->
                val ids = all.filter { it.second === block }.map { it.first.id }.toSet()
                val zoneIds = zonesOut.filter { zoneOwner[it.id] === block }.map { it.id }.toSet()
                val grab =
                    fixedOut.filter { it.second === block }.flatMap { (a, _) ->
                        GeometryEngine.assetWorldOutline(a, presetsById.getValue(a.presetId))
                    } +
                        zonesOut.filter { it.id in zoneIds }.flatMap { it.points }
                val fallback =
                    tentsOut.filter { it.second === block }.flatMap { (a, _) ->
                        GeometryEngine.assetWorldOutline(a, presetsById.getValue(a.presetId))
                    }
                val b = (grab.ifEmpty { fallback }).ifEmpty { listOf(anchors.getValue(block)) }.let(::bounds)
                LayoutBlock(block.key, anchors.getValue(block), b, ids, zoneIds)
            }
        return AutoLayoutResult(assets, zonesOut, skipped, clear, null, blocks, displaced)
    }

    /** Tents with a fixed ring go there; the rest join the ring with the fewest tents, in priority order. */
    private fun assignRings(
        tents: List<Pair<Int, TentPreset>>,
        tentRings: List<Int?>,
        ringCount: Int,
    ): List<List<Pair<Int, TentPreset>>> {
        val rings = List(ringCount) { mutableListOf<Pair<Int, TentPreset>>() }
        val (fixed, auto) = tents.partition { (i, _) -> tentRings.getOrNull(i)?.let { it in 0 until ringCount } == true }
        fixed.forEach { rings[tentRings[it.first]!!] += it }
        auto.forEach { tent -> rings.minBy { it.size } += tent }
        return rings.map { ring -> ring.sortedBy { it.first } }
    }

    private fun buildRing(
        key: BlockKey,
        pin: Vec2?,
        sails: List<TentPreset>,
        fire: FreeArea?,
        tents: List<Pair<Int, TentPreset>>,
        shape: RingShape,
        clear: Float,
    ): Block {
        // Sails edge to edge in a row so joined sails lace together (shared edges hide their ropes).
        val sailBoxes = sails.map { bounds(GeometryEngine.assetWorldOutline(place(it, Vec2(0f, 0f)), it)) }
        var x = -sailBoxes.sumOf { it.width.toDouble() }.toFloat() / 2f
        val placedSails =
            sails.mapIndexed { i, preset ->
                val b = sailBoxes[i]
                val asset = place(preset, Vec2(x - b.left, -(b.top + b.bottom) / 2f))
                x += b.width
                asset to preset
            }
        val fireRadius = fire?.widthMeters?.div(2f) ?: 0f
        val fireZone =
            fire?.let {
                List(FIRE_SIDES) { k ->
                    val a = 2f * PI.toFloat() * k / FIRE_SIDES
                    Vec2(fireRadius * cos(a), fireRadius * sin(a))
                } to it.label
            }
        val centreRadius =
            max(fireRadius, placedSails.flatMap { (a, p) -> footprint(a, p) }.maxOfOrNull { hypot(it.x, it.y) } ?: 0f)

        // Footprint of each tent at rotation 0: x runs along the ring, -y points at the centre.
        val shapes = tents.map { (_, p) -> footprint(place(p, Vec2(0f, 0f)), p, withRopes = !swingsRopes(p, clear)) }
        val inward = shapes.map { points -> (-points.minOf { it.y }).coerceAtLeast(0f) }
        val n = tents.size
        if (n == 0) return Block(key, pin, placedSails, emptyList(), listOfNotNull(fireZone))
        if (shape == RingShape.U_SHAPE) {
            return Block(key, pin, placedSails, buildU(placedSails, fireRadius, tents, clear), listOfNotNull(fireZone))
        }

        val arc = shape.arcDeg / 180f * PI.toFloat()
        val fullCircle = shape == RingShape.FULL_CIRCLE
        val gapCount = if (fullCircle) n else n - 1

        // r is the inner circle every tent's closest point sits on, so small tents stay close to the centre.
        // A tent's wedge is the widest angle any of its points (outline or rope end) reaches from the centre.
        fun wedges(r: Float) = shapes.mapIndexed { i, points -> 2f * points.maxOf { atan2(abs(it.x), r + inward[i] + it.y) } }

        // Points on either side of this angle, both at least r out, are at least `clear` apart.
        fun gap(r: Float) = asin((clear / r).coerceAtMost(1f))

        fun needed(r: Float) = wedges(r).sum() + gapCount * gap(r)

        fun ring(radius: Float): List<Pair<Int, Pair<PlacedAsset, TentPreset>>> {
            val wedges = wedges(radius)
            // Spread leftover arc evenly so a horseshoe really opens to its full width.
            val step = gap(radius) + if (gapCount > 0) (arc - needed(radius)) / gapCount else 0f
            // First tent exactly at the back centre (pi/2 = away from the visitors), the next ones
            // packed outward alternating left and right of it.
            val back = (PI / 2).toFloat()
            var left = back - wedges[0] / 2f
            var right = back + wedges[0] / 2f
            // Each next tent goes to the shorter side, so the opening stays centred on the visitors.
            val centers =
                wedges.mapIndexed { i, w ->
                    when {
                        i == 0 -> back
                        back - left <= right - back -> (left - step - w / 2f).also { left = it - w / 2f }
                        else -> (right + step + w / 2f).also { right = it + w / 2f }
                    }
                }
            val placed =
                tents.indices.map { i ->
                    val center = centers[i]
                    val (priority, preset) = tents[i]
                    val r = radius + inward[i]
                    // theta - 90 turns local -y toward the ring centre; squared up to the parcel edges.
                    priority to
                        (place(preset, Vec2(r * cos(center), r * sin(center)), squareUp(center * 180f / PI.toFloat() - 90f)) to preset)
                }
            return swingWithin(placedSails, placed, clear)
        }

        // At least 1 m: an empty centre with cloth-to-cloth spacing would otherwise start at r = 0 (0/0 in gap()).
        var radius = max(centreRadius + clear, 1f)
        while (needed(radius) > arc) radius += 0.25f
        // The spacing above is exact; the app's walkway warning measures axis-aligned boxes, which grow
        // when tents are rotated. Widen until the real checker is quiet (bounded). Compressed drafts
        // accept those warnings and only avoid hard collisions.
        val strict = clear >= CLEAR
        var placed = ring(radius)
        repeat(MAX_WIDEN_STEPS) {
            if (isClean(placedSails + placed.map { it.second }, strict)) {
                return Block(key, pin, placedSails, placed, listOfNotNull(fireZone))
            }
            radius += 0.25f
            placed = ring(radius)
        }
        return Block(key, pin, placedSails, placed, listOfNotNull(fireZone))
    }

    /** Cloth to cloth: each tent's ropes swing behind its neighbours in the same ring. */
    private fun swingWithin(
        placedSails: List<Pair<PlacedAsset, TentPreset>>,
        placed: List<Pair<Int, Pair<PlacedAsset, TentPreset>>>,
        clear: Float,
    ): List<Pair<Int, Pair<PlacedAsset, TentPreset>>> {
        if (clear > 0f) return placed
        val outlines = (placedSails + placed.map { it.second }).map { (a, p) -> GeometryEngine.assetWorldOutline(a, p) }
        return placed.mapIndexed { i, (priority, tent) ->
            val others = outlines.filterIndexed { j, _ -> j != placedSails.size + i }
            priority to (swingRopes(tent.first, others) to tent.second)
        }
    }

    /**
     * U-shape around the centre (canonical frame, visitors at -y): the first tent in the middle of a
     * straight back row, the back row filled outward, the rest in two side rows running toward the
     * visitors. Entries face inward, everything is square to the parcel. Spacing uses the tents' full
     * boxes (outline, ropes, safety margin), so rows never touch each other or the centre.
     */
    private fun buildU(
        placedSails: List<Pair<PlacedAsset, TentPreset>>,
        fireRadius: Float,
        tents: List<Pair<Int, TentPreset>>,
        clear: Float,
    ): List<Pair<Int, Pair<PlacedAsset, TentPreset>>> {
        val centre =
            (
                placedSails.flatMap { (a, p) -> footprint(a, p) } +
                    listOf(Vec2(-fireRadius, -fireRadius), Vec2(fireRadius, fireRadius))
            ).let(::bounds)

        fun boxAt(
            preset: TentPreset,
            rotationDeg: Float,
        ) = box(place(preset, Vec2(0f, 0f), rotationDeg), preset, withRopes = !swingsRopes(preset, clear))

        // Back row takes about half, small camps are just a row.
        val backCount = if (tents.size <= 3) tents.size else (tents.size + 1) / 2
        val placed = mutableListOf<Pair<Int, Pair<PlacedAsset, TentPreset>>>()
        val backY = centre.bottom + clear
        var left = 0f
        var right = 0f
        tents.take(backCount).forEachIndexed { i, (priority, preset) ->
            val b = boxAt(preset, 0f)
            // First tent centred behind the sail, each next one on the shorter end of the row.
            val x =
                when {
                    i == 0 -> {
                        (-(b.left + b.right) / 2f).also {
                            left = it + b.left
                            right = it + b.right
                        }
                    }

                    -left <= right -> {
                        (left - clear - b.right).also { left = it + b.left }
                    }

                    else -> {
                        (right + clear - b.left).also { right = it + b.right }
                    }
                }
            placed += priority to (place(preset, Vec2(x, backY - b.top), 0f) to preset)
        }

        // Side rows start just in front of the back row and run toward the visitors.
        val innerHalf = max(-centre.left, centre.right) + clear
        var leftTop = backY - clear
        var rightTop = backY - clear
        tents.drop(backCount).forEach { (priority, preset) ->
            val toLeft = leftTop >= rightTop
            // 90 turns the entry toward +x (the centre) for the left row, 270 toward -x for the right row.
            val rotation = if (toLeft) 90f else 270f
            val b = boxAt(preset, rotation)
            val x = if (toLeft) -innerHalf - b.right else innerHalf - b.left
            val y = (if (toLeft) leftTop else rightTop) - b.bottom
            if (toLeft) leftTop = y + b.top - clear else rightTop = y + b.top - clear
            placed += priority to (place(preset, Vec2(x, y), rotation) to preset)
        }
        return swingWithin(placedSails, placed, clear)
    }

    /** No hard collision (and with [strict], no warning) between the ring's own assets; parcel border ignored. */
    private fun isClean(
        assets: List<Pair<PlacedAsset, TentPreset>>,
        strict: Boolean,
    ): Boolean {
        val project =
            PlannerProject(
                parcel = Parcel(2 * HUGE, 2 * HUGE),
                assets = assets.mapIndexed { i, (a, _) -> a.copy(id = "$i", position = Vec2(a.position.x + HUGE, a.position.y + HUGE)) },
            )
        val report = GeometryEngine.evaluate(project, assets.map { it.second }.distinctBy { it.id })
        return report.hardCollisions.isEmpty() && (!strict || report.warningCollisions.isEmpty())
    }

    /**
     * Inside the parcel, at least [clear] (body to body) from everything placed so far, and no rope
     * reaching into a body (checked exactly with [GeometryEngine.evaluate] when the rope boxes touch).
     * Tents at their ideal ring spot skip their own ring: the ring construction already spaced those.
     */
    private fun fits(
        asset: PlacedAsset,
        preset: TentPreset,
        block: Block,
        ideal: Boolean,
        obstacles: List<Obstacle>,
        parcel: Parcel,
        clear: Float,
    ): PlacedAsset? {
        val outline = GeometryEngine.assetWorldOutline(asset, preset)
        if (outline.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        val body = bounds(outline)
        val eps = 0.001f
        if (body.left < -eps || body.top < -eps || body.right > parcel.widthMeters + eps ||
            body.bottom > parcel.heightMeters + eps
        ) {
            return null
        }
        val swing = swingsRopes(preset, clear)
        val reach = box(asset, preset, withRopes = true)
        val spacing = spacingBox(asset, preset, clear)
        val close = mutableListOf<Pair<PlacedAsset, TentPreset>>()
        for (o in obstacles) {
            if (ideal && o.ringSpaced && o.owner === block) continue
            if (gap(spacing, o.body) < clear) {
                // Cloth to cloth: boxes of rotated tents overlap long before the walls touch; check the real outlines.
                val other = o.asset ?: return null
                if (!swing ||
                    GeometryEngine.polygonsIntersect(outline, GeometryEngine.assetWorldOutline(other.first, other.second))
                ) {
                    return null
                }
            }
            if (o.asset != null && gap(reach, o.reach) < 0f) close += o.asset
        }
        if (close.isEmpty()) return asset
        if (!swing) {
            val candidate = asset.copy(id = "candidate")
            val project = PlannerProject(parcel = parcel, assets = close.mapIndexed { i, (a, _) -> a.copy(id = "o$i") } + candidate)
            val hard = GeometryEngine.evaluate(project, (close.map { it.second } + preset).distinctBy { it.id }).hardCollisions
            return asset.takeIf { candidate.id !in hard }
        }
        // Own ropes go behind the neighbours; neighbour tents swing theirs in the final pass, sails can't.
        val bodies = close.map { (a, p) -> GeometryEngine.assetWorldOutline(a, p) }
        val swung = swingRopes(asset, bodies)
        if (ropesHit(swung, bodies)) return null
        val sailRopeIntoUs =
            close.any { (a, p) ->
                GeometryEngine.isSunSailPreset(p) &&
                    GeometryEngine.ropeSegments(a).any { GeometryEngine.lineIntersectsPolygon(it, outline) }
            }
        return swung.takeUnless { sailRopeIntoUs }
    }

    private fun ropesHit(
        asset: PlacedAsset,
        bodies: List<List<Vec2>>,
    ) = GeometryEngine.ropeSegments(asset).any { seg -> bodies.any { GeometryEngine.lineIntersectsPolygon(seg, it) } }

    /**
     * Turns each guy rope that runs into one of [bodies] toward the tent's back (local +y, away from the
     * ring centre) in small steps, like staking it behind the neighbour. Ropes that can't be freed stay.
     */
    private fun swingRopes(
        asset: PlacedAsset,
        bodies: List<List<Vec2>>,
    ): PlacedAsset {
        var current = asset
        asset.ropes.indices.forEach { k ->
            fun hits(a: PlacedAsset) =
                GeometryEngine.ropeSegments(a).getOrNull(k)?.let { seg -> bodies.any { GeometryEngine.lineIntersectsPolygon(seg, it) } } ==
                    true
            if (!hits(current)) return@forEach
            val start = current.ropes[k].outwardAngleDeg
            val toBack = ((90f - start) % 360f + 540f) % 360f - 180f
            var turned = SWING_STEP_DEG
            while (turned <= abs(toBack) + 0.01f) {
                val angle = start + if (toBack < 0f) -turned else turned
                val candidate = current.copy(ropes = current.ropes.toMutableList().also { it[k] = it[k].copy(outwardAngleDeg = angle) })
                if (!hits(candidate)) {
                    current = candidate
                    break
                }
                turned += SWING_STEP_DEG
            }
        }
        return current
    }

    /** Spiral search around the ideal spot, entry still facing the ring centre. */
    private fun search(
        ideal: PlacedAsset,
        preset: TentPreset,
        block: Block,
        anchor: Vec2,
        obstacles: List<Obstacle>,
        parcel: Parcel,
        clear: Float,
        maxRadius: Float,
    ): PlacedAsset? {
        var s = SEARCH_STEP
        while (s <= maxRadius) {
            val count = max(8, ceil(2 * PI * s / SEARCH_STEP).toInt())
            for (k in 0 until count) {
                val a = 2 * PI * k / count
                val p = Vec2(ideal.position.x + s * cos(a).toFloat(), ideal.position.y + s * sin(a).toFloat())
                val facing = atan2(p.y - anchor.y, p.x - anchor.x) * 180f / PI.toFloat() - 90f
                val candidate = ideal.copy(position = p, rotationDeg = squareUp(facing))
                fits(candidate, preset, block, false, obstacles, parcel, clear)?.let { return it }
            }
            s += SEARCH_STEP
        }
        return null
    }

    /**
     * What the walkway is measured from: the padded safety box, like the app's walkway warning, so
     * "1.0 m" here reads as 1.0 m there. Cloth to cloth measures from the wall itself.
     */
    private fun spacingBox(
        asset: PlacedAsset,
        preset: TentPreset,
        clear: Float,
    ): Rect = if (clear > 0f) GeometryEngine.assetAabb(asset, preset) else bounds(GeometryEngine.assetWorldOutline(asset, preset))

    /** Signed separation of two boxes along the axis where they are furthest apart (negative = overlap). */
    private fun gap(
        a: Rect,
        b: Rect,
    ) = maxOf(b.left - a.right, a.left - b.right, b.top - a.bottom, a.top - b.bottom)

    /** Outline, rope ends and padded safety box: everything that must stay clear of other things. */
    private fun box(
        asset: PlacedAsset,
        preset: TentPreset,
        withRopes: Boolean,
    ): Rect {
        val padded = GeometryEngine.assetAabb(asset, preset)
        return bounds(footprint(asset, preset, withRopes)).let {
            Rect(minOf(it.left, padded.left), minOf(it.top, padded.top), maxOf(it.right, padded.right), maxOf(it.bottom, padded.bottom))
        }
    }

    private fun localBounds(block: Block): Rect =
        bounds(
            (block.fixed + block.tents.map { it.second }).flatMap { (a, p) ->
                val padded = GeometryEngine.assetAabb(a, p)
                footprint(a, p) + Vec2(padded.left, padded.top) + Vec2(padded.right, padded.bottom)
            } + block.zones.flatMap { it.first },
        )

    private fun rect(
        width: Float,
        depth: Float,
    ) = listOf(Vec2(-width / 2, -depth / 2), Vec2(width / 2, -depth / 2), Vec2(width / 2, depth / 2), Vec2(-width / 2, depth / 2))

    /** Shift that moves [lo, hi] into [0, limit]; too-wide spans align to 0. */
    private fun shiftInto(
        lo: Float,
        hi: Float,
        limit: Float,
    ) = when {
        hi - lo > limit || lo < 0f -> -lo
        hi > limit -> limit - hi
        else -> 0f
    }

    private fun transform(
        local: Vec2,
        anchor: Vec2,
        angle: Float,
    ): Vec2 = GeometryEngine.rotatePoint(local, angle).let { Vec2(anchor.x + it.x, anchor.y + it.y) }

    private fun transform(
        local: PlacedAsset,
        anchor: Vec2,
        angle: Float,
    ) = local.copy(position = transform(local.position, anchor, angle), rotationDeg = wrapDegrees(local.rotationDeg + angle))

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
        withRopes: Boolean = true,
    ): List<Vec2> =
        GeometryEngine.assetWorldOutline(asset, preset) + if (withRopes) GeometryEngine.ropeSegments(asset).map { it.end } else emptyList()

    private fun bounds(points: List<Vec2>) =
        Rect(
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

    /** Maps a canonical (visitors on top) point so the visitor edge becomes [side]. */
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

    /**
     * Tents are pitched parallel to the parcel edges; only spots near a diagonal (the corners of a
     * ring) get 45 degrees. Turns the exact "entry faces the centre" angle into that.
     */
    internal fun squareUp(deg: Float): Float {
        val d = wrapDegrees(deg)
        val step = if (d % 90f in 30f..60f) 45f else 90f
        return wrapDegrees((d / step).roundToInt() * step)
    }
}
