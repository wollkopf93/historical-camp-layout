package de.voculupo.historicalcamplayout.features.planner.state

import androidx.compose.ui.geometry.Rect
import de.voculupo.historicalcamplayout.AutoSetupRequest
import de.voculupo.historicalcamplayout.BlockZone
import de.voculupo.historicalcamplayout.FreeArea
import de.voculupo.historicalcamplayout.FreeAreaKind
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.IssueKind
import de.voculupo.historicalcamplayout.MIN_ZONE_CLEAR
import de.voculupo.historicalcamplayout.Parcel
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.RingShape
import de.voculupo.historicalcamplayout.SpacingRules
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.Vec2
import de.voculupo.historicalcamplayout.VisitorSide
import de.voculupo.historicalcamplayout.between
import de.voculupo.historicalcamplayout.capped
import de.voculupo.historicalcamplayout.toPath
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
    val blocks: List<LayoutBlock>,
    /** Tents that had to leave their spot in the arrangement to fit (search fallback). */
    val displaced: Int = 0,
    /** Tents the optimizer may move: searched spots and edge / spread tents. Ring spots keep their shape. */
    val movableIds: Set<String> = emptySet(),
)

/**
 * Builds a camp draft. Rings: sun sails in the middle, tents around them with the entry
 * (local -y, like the Saxon porch) facing the centre, the open side toward the visitors. Free areas
 * become block zones. Rings, lanes and areas sit in a row along the visitor edge unless pinned.
 * Tents are then fitted into the parcel in priority order; when space runs out the walkway shrinks
 * step by step (down to cloth to cloth) and the lowest-priority tents are left out.
 */
internal object AutoLayout {
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
        // strict: the user's own gaps, so the draft must not cause walkway warnings; squeezed drafts may.
        fun attempt(
            rules: SpacingRules,
            strict: Boolean,
        ) = repair(
            layout(req, presetsById, obstacleZones, obstacleAssets, rules, strict, newId),
            req.parcel,
            presetsById,
            obstacleZones,
            obstacleAssets,
        )
        var result = attempt(req.spacing, strict = true)
        // The chosen walkway is the user's call: only narrow it when that fits more tents.
        for (clear in COMPRESS_STEPS.filter { it < req.spacing.tentToTent }) {
            if (result.skipped.isEmpty()) break
            val tight = attempt(req.spacing.capped(clear), strict = false)
            if (tight.skipped.size < result.skipped.size) result = tight
        }
        if (!req.optimize) return result
        val tuned =
            repair(
                LayoutRefiner.refine(result, req, presetsById, obstacleZones, obstacleAssets),
                req.parcel,
                presetsById,
                obstacleZones,
                obstacleAssets,
            )

        fun walkwayWarnings(r: AutoLayoutResult) =
            GeometryEngine
                .evaluate(
                    PlannerProject(parcel = req.parcel, assets = r.assets + obstacleAssets, spacing = req.spacing),
                    presetsById.values.toList(),
                ).issues
                .count { it.kind == IssueKind.NARROW_WALKWAY }
        val keepTuned = tuned.skipped.size <= result.skipped.size && walkwayWarnings(tuned) <= walkwayWarnings(result)
        return if (keepTuned) tuned else result
    }

    /**
     * Last safety net, checked with the app's own rules: while a tent still has a hard collision (body,
     * rope, closed zone), stands outside the parcel or blocks a door, the lowest-priority tent involved
     * is left out. Sails and existing assets are never removed.
     */
    private fun repair(
        result: AutoLayoutResult,
        parcel: Parcel,
        presetsById: Map<String, TentPreset>,
        obstacleZones: List<BlockZone>,
        obstacleAssets: List<PlacedAsset>,
    ): AutoLayoutResult {
        val known = obstacleAssets.filter { it.presetId in presetsById }
        val presets = presetsById.values.toList()
        var assets = result.assets
        val dropped = mutableListOf<PlacedAsset>()
        while (true) {
            val all = assets + known
            val report =
                GeometryEngine.evaluate(
                    PlannerProject(parcel = parcel, assets = all, blockZones = result.zones + obstacleZones),
                    presets,
                    autoZonesHard = true,
                )
            val bad = report.hardCollisions.toMutableSet()
            all.forEach { a ->
                val p = presetsById.getValue(a.presetId)
                val b = GeometryEngine.assetBodyAabb(a, p)
                if (b.left < 0f || b.top < 0f || b.right > parcel.widthMeters || b.bottom > parcel.heightMeters) bad += a.id
                all.forEach { o ->
                    if (o !== a && GeometryEngine.blocksEntry(a, p, o, presetsById.getValue(o.presetId))) bad += listOf(a.id, o.id)
                }
            }
            val victim = assets.lastOrNull { it.id in bad && !GeometryEngine.isSunSailPreset(presetsById.getValue(it.presetId)) } ?: break
            assets = assets - victim
            dropped += victim
        }
        if (dropped.isEmpty()) return result
        val gone = dropped.map { it.id }.toSet()
        return result.copy(
            assets = assets,
            skipped = result.skipped + dropped.map { presetsById.getValue(it.presetId).name },
            blocks = result.blocks.map { it.copy(assetIds = it.assetIds - gone) },
            movableIds = result.movableIds - gone,
        )
    }

    /** Parcel sizes the area search tries: half metres, like pitches are measured out. */
    private const val AREA_STEP = 0.5f
    private const val MAX_AREA_GROWTH = 16f
    private const val MAX_ROUNDING_TRIES = 8

    /**
     * Smallest parcel (in [AREA_STEP]s) on which everything in [req] fits with the chosen gaps: no tent
     * left out, no walkway squeezed. Keeps the shape of the current parcel while growing, then trims width
     * and depth one after the other. Pins are dropped (they are absolute positions). Null when nothing is
     * chosen or even a [MAX_AREA_GROWTH] times bigger parcel is not enough.
     */
    fun requiredParcel(
        req: AutoSetupRequest,
        presetsById: Map<String, TentPreset>,
        obstacleZones: List<BlockZone> = emptyList(),
        obstacleAssets: List<PlacedAsset> = emptyList(),
    ): Parcel? {
        if (req.tentPresetIds.isEmpty() && req.sailPresetIds.isEmpty() && req.freeAreas.isEmpty()) return null
        // The optimizer only polishes; whether things fit is decided by the draft.
        val base = req.copy(optimize = false, clusterPins = emptyList(), freeAreas = req.freeAreas.map { it.copy(position = null) })

        fun fits(p: Parcel): Boolean {
            var n = 0
            val r = generate(base.copy(parcel = p), presetsById, obstacleZones, obstacleAssets) { "$it${n++}" }
            return r.skipped.isEmpty() && r.walkwayMeters >= req.spacing.tentToTent
        }

        fun up(v: Float) = ceil(v / AREA_STEP) * AREA_STEP
        val w0 = max(req.parcel.widthMeters, AREA_STEP)
        val h0 = max(req.parcel.heightMeters, AREA_STEP)
        var hi = 1f
        while (!fits(Parcel(w0 * hi, h0 * hi))) {
            hi *= 2f
            if (hi > MAX_AREA_GROWTH) return null
        }
        var lo = 0f
        while ((hi - lo) * max(w0, h0) > AREA_STEP) {
            val mid = (lo + hi) / 2f
            if (fits(Parcel(w0 * mid, h0 * mid))) hi = mid else lo = mid
        }
        // Rounding up can move rings around; only sizes that were checked are kept.
        var w = up(w0 * hi)
        var h = up(h0 * hi)
        var tries = 0
        while (!fits(Parcel(w, h))) {
            if (++tries > MAX_ROUNDING_TRIES) return Parcel(w0 * hi, h0 * hi)
            w += AREA_STEP
            h += AREA_STEP
        }

        fun trim(
            v: Float,
            ok: (Float) -> Boolean,
        ): Float {
            var bad = 0f
            var good = v
            while (good - bad > AREA_STEP) {
                val mid = up((bad + good) / 2f)
                if (mid >= good) break
                if (ok(mid)) good = mid else bad = mid
            }
            return good
        }
        w = trim(w) { fits(Parcel(it, h)) }
        h = trim(h) { fits(Parcel(w, it)) }
        return Parcel(w, h)
    }

    /** Rings the request produces: one per sail (or one for joined sails); at least one. */
    fun ringCount(
        req: AutoSetupRequest,
        presetsById: Map<String, TentPreset>,
    ): Int {
        val sails = req.sailPresetIds.count { id -> presetsById[id]?.let(GeometryEngine::isSunSailPreset) == true }
        val sailRings = if (req.joinSails) minOf(sails, 1) else sails
        return max(1, sailRings)
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
        /** Free ground in front of a placed tent's entry: no other tent may stand on it. */
        val entryZone: List<Vec2>? = null,
        /** An open area (fire, arena, ...) no entry may face; lanes are fine to step out onto. */
        val noEntryZone: List<Vec2>? = null,
        /** A lane: tents keep the path gap from it. */
        val isPath: Boolean = false,
    )

    private fun zoneObstacle(
        points: List<Vec2>,
        owner: Block?,
        entryAllowed: Boolean = false,
    ) = bounds(points).let {
        Obstacle(it, it, null, owner, ringSpaced = owner != null, noEntryZone = points.takeUnless { entryAllowed }, isPath = entryAllowed)
    }

    /** Boxes closer than this may still touch for the app's polygon tests, which count shared edges. */
    private const val TOUCH = 0.01f

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
        rules: SpacingRules,
        strict: Boolean,
        newId: (prefix: String) -> String,
    ): AutoLayoutResult {
        val clear = rules.tentToTent
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
        // Ring centres: the sail groups; without sails, one ring around an empty centre.
        val ringCount = max(1, sailGroups.size)
        // Edge / spread place tents against the whole parcel; the rings then only carry the sails.
        val parcelWide = req.shape == RingShape.EDGE || req.shape == RingShape.SPREAD
        val members = if (parcelWide) List(ringCount) { emptyList() } else assignRings(tents, req.tentRings, ringCount)

        val rings =
            (0 until ringCount).map { r ->
                buildRing(
                    key = BlockKey(true, r),
                    pin = req.clusterPins.getOrNull(r),
                    sails = sailGroups.getOrNull(r).orEmpty(),
                    tents = members[r],
                    shape = req.shape,
                    rules = rules,
                    strict = strict,
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

        fun rowGap(
            a: Block,
            b: Block,
        ) = (if (a.isLane || b.isLane) rules.tentToPath else clear) + TOUCH
        val rowWidth =
            unpinned.sumOf { localBounds.getValue(it).width.toDouble() }.toFloat() +
                unpinned.zipWithNext { a, b -> rowGap(a, b) }.sum()
        var cursor = (canonicalWidth - rowWidth) / 2f
        unpinned.forEachIndexed { i, block ->
            val b = localBounds.getValue(block)
            val top = if (block.isLane) 0f else clear
            anchors[block] = toRealFrame(Vec2(cursor - b.left, top - b.top), side, parcel)
            cursor += b.width + (unpinned.getOrNull(i + 1)?.let { rowGap(block, it) } ?: 0f)
        }
        row.filter { it.pin != null }.forEach { anchors[it] = it.pin!! }

        // Sails and zones are solid: push each block so they sit inside the parcel.
        row.forEach { block ->
            val anchor = anchors.getValue(block)
            val solid = solidPoints(block, anchor, angle)
            if (solid.isNotEmpty()) {
                val b = bounds(solid)
                anchors[block] =
                    Vec2(
                        anchor.x + shiftInto(b.left, b.right, parcel.widthMeters),
                        anchor.y + shiftInto(b.top, b.bottom, parcel.heightMeters),
                    )
            }
        }

        // A row wider than the parcel would stack sails and areas on top of each other: move each block
        // to the nearest spot where its whole ring (tents included) stays clear of the blocks before it,
        // else where at least its sails/area do, else leave its sails/area out.
        val zoneBoxes = obstacleZones.filter { it.points.size >= 3 }.map { bounds(it.points) }
        val takenSolid = zoneBoxes.toMutableList()
        val takenWhole = zoneBoxes.toMutableList()
        val dropped = mutableSetOf<Block>()
        row.forEach { block ->
            val anchor = anchors.getValue(block)
            val solid = solidPoints(block, anchor, angle).ifEmpty { return@forEach }
            // Sails keep their safety margin like the app's walkway check; areas have none.
            val margin = block.fixed.maxOfOrNull { it.first.safetyMarginMeters } ?: 0f
            val b = bounds(solid)
            val local = localBounds(block)
            val whole =
                bounds(
                    listOf(
                        Vec2(local.left, local.top),
                        Vec2(local.right, local.top),
                        Vec2(local.right, local.bottom),
                        Vec2(local.left, local.bottom),
                    ).map { transform(it, anchor, angle) } + solid,
                )
            val pathGap = if (block.fixed.isNotEmpty() || block.isLane) rules.sailToPath else 0f
            val apart = maxOf(clear, MIN_ZONE_CLEAR, pathGap) + TOUCH

            fun Rect.by(d: Vec2) = Rect(left + d.x, top + d.y, right + d.x, bottom + d.y)

            fun freeAt(
                d: Vec2,
                ringClear: Boolean,
            ): Boolean {
                val moved = b.by(d)
                // Strict: the app flags a sail 1 µm over the edge. Lanes span the full depth by design.
                val depthOk = block.isLane || (moved.top >= 0f && moved.bottom <= parcel.heightMeters)
                return moved.left >= 0f && moved.right <= parcel.widthMeters && depthOk &&
                    takenSolid.all { gap(moved.inflate(margin), it) >= apart } &&
                    (!ringClear || takenWhole.all { gap(whole.by(d), it) >= apart })
            }
            // Lanes run the full depth, so they only slide sideways. Spots stay 1 mm inside against float rounding.
            val mm = 0.001f
            val shift =
                if (block.pin != null || freeAt(Vec2(0f, 0f), ringClear = true)) {
                    Vec2(0f, 0f)
                } else {
                    val xs =
                        generateSequence(
                            mm - b.left,
                        ) { it + SEARCH_STEP }.takeWhile { it <= parcel.widthMeters - b.right - mm }.toList()
                    val ys =
                        if (block.isLane) {
                            listOf(0f)
                        } else {
                            generateSequence(
                                mm - b.top,
                            ) { it + SEARCH_STEP }.takeWhile { it <= parcel.heightMeters - b.bottom - mm }.toList()
                        }
                    val spots = xs.flatMap { x -> ys.map { y -> Vec2(x, y) } }.sortedBy { hypot(it.x, it.y) }
                    spots.firstOrNull { freeAt(it, ringClear = true) } ?: spots.firstOrNull { freeAt(it, ringClear = false) }
                }
            if (shift == null) {
                dropped += block
            } else {
                anchors[block] = Vec2(anchor.x + shift.x, anchor.y + shift.y)
                takenSolid += b.by(shift).inflate(margin)
                takenWhole += whole.by(shift)
            }
        }

        val obstacles = mutableListOf<Obstacle>()
        obstacleZones.filter { it.points.size >= 3 }.forEach { obstacles += zoneObstacle(it.points, null) }
        obstacleAssets.forEach { a -> presetsById[a.presetId]?.let { obstacles += assetObstacle(a, it, null, false, clear) } }

        val zonesOut = mutableListOf<BlockZone>()
        val zoneOwner = mutableMapOf<String, Block>()
        val fixedOut = mutableListOf<Pair<PlacedAsset, Block>>()
        row.forEach { block ->
            if (block in dropped) return@forEach
            val anchor = anchors.getValue(block)
            block.fixed.forEach { (a, p) ->
                val placed = transform(a, anchor, angle).copy(id = newId(""))
                fixedOut += placed to block
                obstacles += assetObstacle(placed, p, block, true, clear)
            }
            block.zones.forEach { (poly, label) ->
                val kind = if (block.isLane) FreeAreaKind.LANE else null
                val zone = BlockZone(AUTO_PREFIX + newId(""), poly.map { transform(it, anchor, angle) }, label, kind)
                zonesOut += zone
                zoneOwner[zone.id] = block
                obstacles += zoneObstacle(zone.points, block, entryAllowed = block.isLane)
            }
        }

        val tentsOut = mutableListOf<Pair<PlacedAsset, Block>>()
        val skipped = dropped.flatMap { block -> block.fixed.map { it.second.name } + block.zones.map { it.second } }.toMutableList()
        var displaced = 0
        val movable = mutableSetOf<String>()
        val searchRadius = hypot(parcel.widthMeters, parcel.heightMeters)
        row
            .flatMap { block -> block.tents.map { Triple(it.first, block, it.second) } }
            .sortedBy { it.first }
            .forEach { (_, block, tent) ->
                val (local, preset) = tent
                val anchor = anchors.getValue(block)
                val ideal = transform(local, anchor, angle)
                val placed =
                    fits(ideal, preset, block, true, obstacles, parcel, rules)
                        ?: search(ideal, preset, block, anchor, obstacles, parcel, rules, searchRadius)
                if (placed == null) {
                    skipped += preset.name
                } else {
                    val withId = placed.copy(id = newId(""))
                    if (placed.position != ideal.position) {
                        displaced++
                        movable += withId.id
                    }
                    tentsOut += withId to block
                    obstacles += assetObstacle(withId, preset, block, placed.position == ideal.position, clear)
                    GeometryEngine.entryZone(withId, preset)?.let { zone ->
                        obstacles += Obstacle(bounds(zone), bounds(zone), null, block, entryZone = zone)
                    }
                }
            }

        if (parcelWide) {
            // Not part of any ring: entries face the nearest sail, else the parcel centre.
            val loose = Block(BlockKey(true, -1), null, emptyList(), emptyList(), emptyList())
            val centres =
                rings
                    .filter { (it.fixed.isNotEmpty() || it.zones.isNotEmpty()) && it !in dropped }
                    .map { anchors.getValue(it) }
                    .ifEmpty { listOf(Vec2(parcel.widthMeters / 2f, parcel.heightMeters / 2f)) }
            tents.forEach { (_, preset) ->
                val placed =
                    if (req.shape == RingShape.EDGE) {
                        edgeSpot(preset, loose, side, obstacles, parcel, rules)
                    } else {
                        spreadSpot(preset, loose, centres, obstacles, parcel, rules)
                    }
                if (placed == null) {
                    skipped += preset.name
                } else {
                    val withId = placed.copy(id = newId(""))
                    movable += withId.id
                    tentsOut += withId to loose
                    obstacles += assetObstacle(withId, preset, loose, false, clear)
                    GeometryEngine.entryZone(withId, preset)?.let { zone ->
                        obstacles += Obstacle(bounds(zone), bounds(zone), null, loose, entryZone = zone)
                    }
                }
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
        // Fire pits last: they go into the open ground the tents left, close to the camp's centre.
        val fireBlocks = mutableListOf<Block>()
        val campCentre =
            rings.firstOrNull { it.fixed.isNotEmpty() && it !in dropped }?.let { anchors.getValue(it) }
                ?: Vec2(parcel.widthMeters / 2f, parcel.heightMeters / 2f)
        req.freeAreas.withIndex().filter { it.value.kind == FreeAreaKind.FIRE }.forEach { (i, area) ->
            val r = area.widthMeters / 2f
            val centre = area.position ?: fireSpot(r, campCentre, obstacles, parcel)
            if (centre == null) {
                skipped += area.label
            } else {
                val block = Block(BlockKey(false, i), area.position, emptyList(), emptyList(), emptyList())
                anchors[block] = centre
                val zone =
                    BlockZone(
                        AUTO_PREFIX + newId(""),
                        circle(r).map { Vec2(centre.x + it.x, centre.y + it.y) },
                        area.label,
                        FreeAreaKind.FIRE,
                    )
                zonesOut += zone
                zoneOwner[zone.id] = block
                obstacles += zoneObstacle(zone.points, block)
                fireBlocks += block
            }
        }

        val all = fixedOut + tentsOut
        // Loose tents (edge / spread) are not grouped: they don't belong together.
        val groups = row.associateWith { block -> if (all.count { it.second === block } > 1) newId("group-") else null }
        val assets = all.map { (a, block) -> a.copy(groupId = groups[block]) }
        val blocks =
            (row + fireBlocks).map { block ->
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
        return AutoLayoutResult(assets, zonesOut, skipped, clear, blocks, displaced, movable)
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
        tents: List<Pair<Int, TentPreset>>,
        shape: RingShape,
        rules: SpacingRules,
        strict: Boolean,
    ): Block {
        val clear = rules.tentToTent
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
        val centreRadius = placedSails.flatMap { (a, p) -> footprint(a, p) }.maxOfOrNull { hypot(it.x, it.y) } ?: 0f

        // Footprint of each tent at rotation 0: x runs along the ring, -y points at the centre.
        val shapes = tents.map { (_, p) -> footprint(place(p, Vec2(0f, 0f)), p, withRopes = !swingsRopes(p, clear)) }
        val inward = shapes.map { points -> (-points.minOf { it.y }).coerceAtLeast(0f) }
        val n = tents.size
        if (n == 0) return Block(key, pin, placedSails, emptyList(), emptyList())
        if (shape == RingShape.U_SHAPE) {
            return Block(key, pin, placedSails, buildU(placedSails, tents, rules), emptyList())
        }

        val arc = shape.arcDeg / 180f * PI.toFloat()
        val fullCircle = shape == RingShape.FULL_CIRCLE
        val gapCount = if (fullCircle) n else n - 1

        // r is the inner circle every tent's closest point sits on, so small tents stay close to the centre.
        // A tent's wedge is the widest angle any of its points (outline or rope end) reaches from the centre.
        fun wedges(r: Float) = shapes.mapIndexed { i, points -> 2f * points.maxOf { atan2(abs(it.x), r + inward[i] + it.y) } }

        // Tent types may want more room than the camp's walkway: the ring uses the widest pair.
        val ringClear = tents.flatMap { (_, a) -> tents.map { (_, b) -> rules.between(a, b) } }.maxOrNull() ?: clear

        // Points on either side of this angle, both at least r out, are at least `ringClear` apart.
        fun gap(r: Float) = asin((ringClear / r).coerceAtMost(1f))

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
        val toSail = placedSails.flatMap { (_, s) -> tents.map { (_, t) -> rules.between(s, t) } }.maxOrNull()
        var radius = max(centreRadius + (toSail ?: ringClear), 1f)
        while (needed(radius) > arc) radius += 0.25f
        // The spacing above is exact; the app's walkway warning measures axis-aligned boxes, which grow
        // when tents are rotated. Widen until the real checker is quiet (bounded). Compressed drafts
        // accept those warnings and only avoid hard collisions.
        var placed = ring(radius)
        repeat(MAX_WIDEN_STEPS) {
            if (isClean(placedSails + placed.map { it.second }, strict, rules)) {
                return Block(key, pin, placedSails, placed, emptyList())
            }
            radius += 0.25f
            placed = ring(radius)
        }
        return Block(key, pin, placedSails, placed, emptyList())
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
        tents: List<Pair<Int, TentPreset>>,
        rules: SpacingRules,
    ): List<Pair<Int, Pair<PlacedAsset, TentPreset>>> {
        val clear = rules.tentToTent
        // Sail outline, ropes and safety margin: a lean-to's margin reaches past its short ropes.
        val centre =
            placedSails
                .flatMap { (a, p) -> box(a, p, withRopes = true).let { listOf(Vec2(it.left, it.top), Vec2(it.right, it.bottom)) } }
                .ifEmpty { listOf(Vec2(0f, 0f)) }
                .let(::bounds)

        fun boxAt(
            preset: TentPreset,
            rotationDeg: Float,
        ) = box(place(preset, Vec2(0f, 0f), rotationDeg), preset, withRopes = !swingsRopes(preset, clear))

        // +1 cm: the app's walkway warning counts exactly 1.50 m as "under 1.5 m".
        fun padded(gap: Float) = if (gap > 0f) gap + TOUCH else 0f

        fun space(
            a: TentPreset,
            b: TentPreset,
        ) = padded(rules.between(a, b))
        val rowSpace = tents.flatMap { (_, a) -> tents.map { (_, b) -> space(a, b) } }.maxOrNull() ?: padded(clear)
        val toCentre = placedSails.flatMap { (_, s) -> tents.map { (_, t) -> rules.between(s, t) } }.maxOrNull() ?: clear
        val centreSpace = padded(toCentre)

        // Back row takes about half, small camps are just a row.
        val backCount = if (tents.size <= 3) tents.size else (tents.size + 1) / 2
        val placed = mutableListOf<Pair<Int, Pair<PlacedAsset, TentPreset>>>()
        val backY = centre.bottom + centreSpace
        var left = 0f
        var right = 0f
        var leftEnd: TentPreset? = null
        var rightEnd: TentPreset? = null
        tents.take(backCount).forEachIndexed { i, (priority, preset) ->
            val b = boxAt(preset, 0f)
            // First tent centred behind the sail, each next one on the shorter end of the row.
            val x =
                when {
                    i == 0 -> {
                        (-(b.left + b.right) / 2f).also {
                            left = it + b.left
                            right = it + b.right
                            leftEnd = preset
                            rightEnd = preset
                        }
                    }

                    -left <= right -> {
                        (left - space(leftEnd!!, preset) - b.right).also {
                            left = it + b.left
                            leftEnd = preset
                        }
                    }

                    else -> {
                        (right + space(rightEnd!!, preset) - b.left).also {
                            right = it + b.right
                            rightEnd = preset
                        }
                    }
                }
            placed += priority to (place(preset, Vec2(x, backY - b.top), 0f) to preset)
        }

        // Side rows start just in front of the back row and run toward the visitors.
        val innerHalf = max(-centre.left, centre.right) + centreSpace
        var leftTop = backY
        var rightTop = backY
        var leftLast: TentPreset? = null
        var rightLast: TentPreset? = null
        tents.drop(backCount).forEach { (priority, preset) ->
            val toLeft = leftTop >= rightTop
            // 90 turns the entry toward +x (the centre) for the left row, 270 toward -x for the right row.
            val rotation = if (toLeft) 90f else 270f
            val b = boxAt(preset, rotation)
            val x = if (toLeft) -innerHalf - b.right else innerHalf - b.left
            // The first side tent faces the back row's corner: keep the widest gap of the camp to it.
            val gapY = (if (toLeft) leftLast else rightLast)?.let { space(it, preset) } ?: rowSpace
            val y = (if (toLeft) leftTop else rightTop) - gapY - b.bottom
            if (toLeft) {
                leftTop = y + b.top
                leftLast = preset
            } else {
                rightTop = y + b.top
                rightLast = preset
            }
            placed += priority to (place(preset, Vec2(x, y), rotation) to preset)
        }
        return swingWithin(placedSails, placed, clear)
    }

    /**
     * No hard collision between the ring's own assets (parcel border ignored); with [strict] also no
     * app warning. Every pair keeps the chosen walkway [clear] between their safety margins, so a wider
     * walkway really widens the ring.
     */
    private fun isClean(
        assets: List<Pair<PlacedAsset, TentPreset>>,
        strict: Boolean,
        rules: SpacingRules,
    ): Boolean {
        val clear = rules.tentToTent
        val project =
            PlannerProject(
                parcel = Parcel(2 * HUGE, 2 * HUGE),
                assets = assets.mapIndexed { i, (a, _) -> a.copy(id = "$i", position = Vec2(a.position.x + HUGE, a.position.y + HUGE)) },
                spacing = rules,
            )
        val report = GeometryEngine.evaluate(project, assets.map { it.second }.distinctBy { it.id })
        if (report.hardCollisions.isNotEmpty() || (strict && clear > 0f && report.warningCollisions.isNotEmpty())) return false
        if (clear <= 0f) return true
        val outlines = assets.map { (a, p) -> GeometryEngine.assetWorldOutline(a, p) }
        return assets.indices.all { i ->
            (i + 1 until assets.size).all { j ->
                // Joined sails lace together edge to edge.
                (GeometryEngine.isSunSailPreset(assets[i].second) && GeometryEngine.isSunSailPreset(assets[j].second)) ||
                    GeometryEngine.outlineDistance(outlines[i], outlines[j]) -
                    assets[i].first.safetyMarginMeters -
                    assets[j].first.safetyMarginMeters >= rules.between(assets[i].second, assets[j].second)
            }
        }
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
        rules: SpacingRules,
    ): PlacedAsset? {
        val clear = rules.tentToTent
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
        val ownEntry = GeometryEngine.entryZone(asset, preset)
        // Entries are checked for every tent, also within a ring: nobody stands in front of a door.
        if (obstacles.any { o -> blocksAnEntry(outline, body, ownEntry, o) }) return null
        val close = mutableListOf<Pair<PlacedAsset, TentPreset>>()
        for (o in obstacles) {
            if (o.entryZone != null || (ideal && o.ringSpaced && o.owner === block)) continue
            // +1 cm for freely searched spots: the app's walkway warning counts exactly 1.50 m as "under 1.5 m".
            val needed =
                when {
                    o.isPath -> rules.toPath(preset)
                    o.asset == null || o.noEntryZone != null -> max(clear, MIN_ZONE_CLEAR)
                    else -> rules.between(preset, o.asset.second)
                }
            if (gap(spacing, o.body) < needed + if (needed > 0f && !ideal) 0.01f else 0f) {
                // Cloth to cloth: boxes of rotated tents overlap long before the walls touch; check the real outlines.
                val other = o.asset ?: return null
                if (!swing ||
                    GeometryEngine.polygonsIntersect(outline, GeometryEngine.assetWorldOutline(other.first, other.second))
                ) {
                    return null
                }
            }
            if (o.asset != null && gap(reach, o.reach) < TOUCH) close += o.asset
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
        if (sailRopeIntoUs) return null
        // Each neighbour tent must still be able to swing its ropes clear of us and of its other neighbours,
        // or the final pass leaves a rope in a wall (and the tent gets dropped later anyway).
        val stuck =
            close.any { (n, p) ->
                if (GeometryEngine.isSunSailPreset(p) ||
                    GeometryEngine.ropeSegments(n).none { GeometryEngine.lineIntersectsPolygon(it, outline) }
                ) {
                    return@any false
                }
                val nReach = box(n, p, withRopes = true)
                val around =
                    obstacles
                        .mapNotNull { o -> o.asset?.takeIf { it.first !== n && gap(o.reach, nReach) < TOUCH } }
                        .map { (a, ap) -> GeometryEngine.assetWorldOutline(a, ap) } + listOf(outline)
                ropesHit(swingRopes(n, around), around)
            }
        return swung.takeUnless { stuck }
    }

    /** This tent stands in [o]'s entry zone, or [o] (a tent) stands in this tent's entry zone. */
    private fun blocksAnEntry(
        outline: List<Vec2>,
        body: Rect,
        ownEntry: List<Vec2>?,
        o: Obstacle,
    ): Boolean {
        if (o.entryZone != null) return gap(body, o.body) < TOUCH && GeometryEngine.polygonsIntersect(outline, o.entryZone)
        if (o.noEntryZone != null) {
            return ownEntry != null && gap(bounds(ownEntry), o.body) < TOUCH && GeometryEngine.polygonsIntersect(ownEntry, o.noEntryZone)
        }
        val other = o.asset ?: return false
        if (ownEntry == null || GeometryEngine.isSunSailPreset(other.second) || gap(bounds(ownEntry), o.body) >= TOUCH) return false
        return GeometryEngine.polygonsIntersect(ownEntry, GeometryEngine.assetWorldOutline(other.first, other.second))
    }

    private fun ropesHit(
        asset: PlacedAsset,
        bodies: List<List<Vec2>>,
    ) = GeometryEngine.ropeSegments(asset).any { seg -> bodies.any { GeometryEngine.lineIntersectsPolygon(seg, it) } }

    /**
     * Turns each guy rope that runs into one of [bodies] toward the tent's back (local +y, away from the
     * ring centre) in small steps, like staking it behind the neighbour. Ropes that can't be freed stay.
     */
    internal fun swingRopes(
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

    /**
     * Along the back edge (opposite the visitors) from the middle outward, then down both side edges
     * from the back toward the visitors. The tent's back touches the edge, its entry faces inward.
     */
    private fun edgeSpot(
        preset: TentPreset,
        loose: Block,
        side: VisitorSide,
        obstacles: List<Obstacle>,
        parcel: Parcel,
        rules: SpacingRules,
    ): PlacedAsset? {
        val w = parcel.widthMeters
        val h = parcel.heightMeters

        // Each edge: rotation that turns the entry inward, and where along it to try first.
        data class Edge(
            val rotation: Float,
            val point: (Float, Rect) -> Vec2,
            val length: Float,
            val fromMiddle: Boolean,
            val fromStart: Boolean,
        )
        val top = Edge(180f, { t, b -> Vec2(t, -b.top) }, w, true, true)
        val bottom = Edge(0f, { t, b -> Vec2(t, h - b.bottom) }, w, true, true)
        val left = Edge(90f, { t, b -> Vec2(-b.left, t) }, h, false, true)
        val right = Edge(270f, { t, b -> Vec2(w - b.right, t) }, h, false, true)
        val (back, sides) =
            when (side) {
                VisitorSide.TOP -> {
                    bottom to listOf(left.copy(fromStart = false), right.copy(fromStart = false))
                }

                VisitorSide.BOTTOM -> {
                    top to listOf(left, right)
                }

                VisitorSide.LEFT -> {
                    right.copy(fromMiddle = true) to
                        listOf(top.copy(fromMiddle = false, fromStart = false), bottom.copy(fromMiddle = false, fromStart = false))
                }

                VisitorSide.RIGHT -> {
                    left.copy(fromMiddle = true) to listOf(top.copy(fromMiddle = false), bottom.copy(fromMiddle = false))
                }
            }

        fun stops(edge: Edge): List<Float> {
            val all = generateSequence(0f) { it + SEARCH_STEP }.takeWhile { it <= edge.length }.toList()
            return when {
                edge.fromMiddle -> all.sortedBy { abs(it - edge.length / 2f) }
                edge.fromStart -> all
                else -> all.reversed()
            }
        }

        fun tryEdge(
            edge: Edge,
            t: Float,
        ): PlacedAsset? {
            val probe = place(preset, Vec2(0f, 0f), edge.rotation)
            val b = bounds(GeometryEngine.assetWorldOutline(probe, preset))
            return fits(probe.copy(position = edge.point(t, b)), preset, loose, false, obstacles, parcel, rules)
        }
        stops(back).forEach { t -> tryEdge(back, t)?.let { return it } }
        // Side edges alternately, so both sides grow evenly from the back.
        val sideStops = sides.map { edge -> stops(edge).map { edge to it } }
        val interleaved = (0 until sideStops.maxOf { it.size }).flatMap { i -> sideStops.mapNotNull { it.getOrNull(i) } }
        interleaved.forEach { (edge, t) -> tryEdge(edge, t)?.let { return it } }
        return null
    }

    /**
     * The free spot farthest from everything already there (tents, entries, sails, open areas and the
     * parcel edges), entry turned toward the nearest centre.
     */
    private fun spreadSpot(
        preset: TentPreset,
        loose: Block,
        centres: List<Vec2>,
        obstacles: List<Obstacle>,
        parcel: Parcel,
        rules: SpacingRules,
    ): PlacedAsset? {
        val step = 1f
        val candidates =
            generateSequence(step / 2f) { it + step }
                .takeWhile { it < parcel.widthMeters }
                .flatMap { x ->
                    generateSequence(step / 2f) { it + step }.takeWhile { it < parcel.heightMeters }.map { y -> Vec2(x, y) }
                }.map { p ->
                    val centre = centres.minBy { hypot(it.x - p.x, it.y - p.y) }
                    val facing = atan2(p.y - centre.y, p.x - centre.x) * 180f / PI.toFloat() - 90f
                    val asset = place(preset, p, squareUp(facing))
                    val body = bounds(GeometryEngine.assetWorldOutline(asset, preset))
                    val edgeGap = minOf(body.left, body.top, parcel.widthMeters - body.right, parcel.heightMeters - body.bottom)
                    val nearest = obstacles.minOfOrNull { gap(body, it.body) } ?: Float.MAX_VALUE
                    asset to minOf(edgeGap, nearest)
                }.filter { (_, score) -> score >= 0f }
                .sortedByDescending { it.second }
        return candidates.firstNotNullOfOrNull { (asset, _) -> fits(asset, preset, loose, false, obstacles, parcel, rules) }
    }

    /** Distance a fire pit keeps from tents and sails (fire safety); relaxed to [MIN_ZONE_CLEAR] when space is short. */
    private const val FIRE_CLEAR = 2.0f

    private fun circle(r: Float) =
        List(FIRE_SIDES) { k ->
            val a = 2f * PI.toFloat() * k / FIRE_SIDES
            Vec2(r * cos(a), r * sin(a))
        }

    /**
     * Free spot for a fire pit of radius [r] closest to [near]: [FIRE_CLEAR] from every tent and sail,
     * and clear of doors and other open areas. Null when the camp leaves no room for it.
     */
    private fun fireSpot(
        r: Float,
        near: Vec2,
        obstacles: List<Obstacle>,
        parcel: Parcel,
    ): Vec2? {
        val candidates =
            generateSequence(r) { it + SEARCH_STEP }
                .takeWhile { it <= parcel.widthMeters - r }
                .flatMap { x ->
                    generateSequence(r) { it + SEARCH_STEP }.takeWhile { it <= parcel.heightMeters - r }.map { y -> Vec2(x, y) }
                }.sortedBy { hypot(it.x - near.x, it.y - near.y) }
                .toList()
        return listOf(FIRE_CLEAR, MIN_ZONE_CLEAR).firstNotNullOfOrNull { assetGap ->
            candidates.firstOrNull { p ->
                val box = Rect(p.x - r, p.y - r, p.x + r, p.y + r)
                obstacles.all { o -> gap(box, o.body) >= if (o.asset != null) assetGap else MIN_ZONE_CLEAR }
            }
        }
    }

    /** Spiral search around the ideal spot, entry still facing the ring centre. */
    private fun search(
        ideal: PlacedAsset,
        preset: TentPreset,
        block: Block,
        anchor: Vec2,
        obstacles: List<Obstacle>,
        parcel: Parcel,
        rules: SpacingRules,
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
                fits(candidate, preset, block, false, obstacles, parcel, rules)?.let { return it }
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

    /** The solid part of a block (sail outlines, area polygons) with its anchor at [anchor]. */
    private fun solidPoints(
        block: Block,
        anchor: Vec2,
        angle: Float,
    ) = block.fixed.flatMap { (a, p) -> GeometryEngine.assetWorldOutline(transform(a, anchor, angle), p) } +
        block.zones.flatMap { (poly, _) -> poly.map { transform(it, anchor, angle) } }

    // An empty block (nothing chosen yet in the sheet) is a point, not a crash.
    private fun localBounds(block: Block): Rect =
        bounds(
            (
                (block.fixed + block.tents.map { it.second }).flatMap { (a, p) ->
                    val padded = GeometryEngine.assetAabb(a, p)
                    footprint(a, p) + Vec2(padded.left, padded.top) + Vec2(padded.right, padded.bottom)
                } + block.zones.flatMap { it.first }
            ).ifEmpty { listOf(Vec2(0f, 0f)) },
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
