package de.voculupo.historicalcamplayout.features.planner.state

import de.voculupo.historicalcamplayout.AutoSetupRequest
import de.voculupo.historicalcamplayout.FreeArea
import de.voculupo.historicalcamplayout.FreeAreaKind
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.IssueKind
import de.voculupo.historicalcamplayout.Parcel
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.RingShape
import de.voculupo.historicalcamplayout.SpacingRules
import de.voculupo.historicalcamplayout.VisitorSide
import de.voculupo.historicalcamplayout.defaultPresets
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Hundreds of generated camps (parcel size, tent mix, sails, shape, visitor side, open areas, walkway)
 * checked against the rules every draft must keep. Failures are collected so one run shows them all.
 */
class AutoLayoutScenarioTest {
    private val presets = defaultPresets()
    private val presetsById = presets.associateBy { it.id }
    private val tentIds = presets.filterNot(GeometryEngine::isSunSailPreset).map { it.id }
    private val sailIds = presets.filter(GeometryEngine::isSunSailPreset).map { it.id }

    private fun isSail(a: PlacedAsset) = GeometryEngine.isSunSailPreset(presetsById.getValue(a.presetId))

    private fun scenario(rnd: Random): AutoSetupRequest {
        val w = listOf(12f, 18f, 25f, 30f, 40f, 60f)[rnd.nextInt(6)]
        val h = listOf(10f, 15f, 20f, 30f, 50f)[rnd.nextInt(5)]
        val tents = List(1 + rnd.nextInt(14)) { tentIds[rnd.nextInt(tentIds.size)] }
        val sails = List(listOf(0, 1, 1, 1, 2, 3)[rnd.nextInt(6)]) { sailIds[rnd.nextInt(sailIds.size)] }
        val areas =
            buildList {
                if (rnd.nextInt(3) == 0) add(FreeArea(FreeAreaKind.FIRE, "Feuer", listOf(1.5f, 2f, 3f)[rnd.nextInt(3)]))
                if (rnd.nextInt(5) == 0) add(FreeArea(FreeAreaKind.ARENA, "Kampfplatz", 6f + rnd.nextInt(8), 5f + rnd.nextInt(6)))
                if (rnd.nextInt(6) == 0) add(FreeArea(FreeAreaKind.LANE, "Gasse", 2f + rnd.nextInt(3)))
            }
        return AutoSetupRequest(
            parcel = Parcel(w, h),
            tentPresetIds = tents,
            sailPresetIds = sails,
            visitorSide = VisitorSide.entries[rnd.nextInt(4)],
            shape = RingShape.entries[rnd.nextInt(RingShape.entries.size)],
            joinSails = sails.size > 1 && rnd.nextBoolean(),
            freeAreas = areas,
            spacing = SpacingRules(tentToTent = listOf(1.5f, 1.5f, 1.5f, 1.0f, 2.0f, 0f)[rnd.nextInt(6)]),
            optimize = rnd.nextInt(4) != 0,
        )
    }

    /** Every rule a draft must keep; empty when the draft is valid. */
    private fun violations(
        req: AutoSetupRequest,
        result: AutoLayoutResult,
    ): List<String> {
        val out = mutableListOf<String>()
        if (result.assets.any { !it.position.x.isFinite() || !it.position.y.isFinite() || !it.rotationDeg.isFinite() }) {
            out +=
                "non-finite pose"
        }
        val report =
            GeometryEngine.evaluate(PlannerProject(parcel = req.parcel, assets = result.assets, blockZones = result.zones), presets)
        if (report.hardCollisions.isNotEmpty()) out += "hard: " + report.issues.filter { it.kind != IssueKind.NARROW_WALKWAY }
        out +=
            report.issues
                .filter {
                    it.kind == IssueKind.OUTSIDE_PARCEL || it.kind == IssueKind.ENTRY_BLOCKED ||
                        it.kind == IssueKind.IN_NO_GO_ZONE
                }.map { "$it" }
        // The walkway the result reports must really be kept (the app warns below 1.5 m).
        if (result.walkwayMeters >= 1.5f) out += report.issues.filter { it.kind == IssueKind.NARROW_WALKWAY }.map { "$it" }

        // Everything asked for is either placed or reported as left out.
        val names = { ids: List<String> -> ids.map { presetsById.getValue(it).name }.toSet() }
        val tentNames = names(req.tentPresetIds)
        val sailNames = names(req.sailPresetIds)
        val placedTents = result.assets.count { !isSail(it) }
        val skippedTents = result.skipped.count { it in tentNames }
        if (placedTents + skippedTents !=
            req.tentPresetIds.size
        ) {
            out += "lost tents: $placedTents placed + $skippedTents skipped of ${req.tentPresetIds.size}"
        }
        val sails = result.assets.count(::isSail) + result.skipped.count { it in sailNames }
        if (sails != req.sailPresetIds.size) out += "sails: $sails of ${req.sailPresetIds.size}"
        // Rings give the first tent the best spot; edge / spread fill the parcel, where it may simply not fit.
        val ring = req.shape != RingShape.EDGE && req.shape != RingShape.SPREAD
        // Cloth to cloth may have to drop it when its ropes can't be staked clear of a sail.
        if (ring && result.walkwayMeters > 0f && result.skipped.isNotEmpty() && placedTents > 0 &&
            result.assets.none { it.presetId == req.tentPresetIds.first() }
        ) {
            out += "first-priority tent dropped while others placed"
        }

        // No door opens onto a fire or arena (lanes are fine).
        val noEntry = result.zones.filter { z -> req.freeAreas.any { it.label == z.label && it.kind != FreeAreaKind.LANE } }
        result.assets.filterNot(::isSail).forEach { t ->
            val door = GeometryEngine.entryZone(t, presetsById.getValue(t.presetId)) ?: return@forEach
            noEntry.filter { GeometryEngine.polygonsIntersect(door, it.points) }.forEach { out += "${t.presetId} door into ${it.label}" }
        }
        return out
    }

    @Test
    fun generatedCampsKeepEveryRule() {
        val rnd = Random(2026)
        val failures = mutableListOf<String>()
        var skipped = 0
        var compressed = 0
        var tents = 0
        repeat(CASES) { n ->
            val req = scenario(rnd)
            var id = 0
            val result = AutoLayout.generate(req, presetsById) { "$it${id++}" }
            tents += req.tentPresetIds.size
            skipped += result.skipped.size
            if (result.walkwayMeters < req.spacing.tentToTent) compressed++
            violations(req, result).takeIf { it.isNotEmpty() }?.let {
                failures += "#$n $req\n  -> $it"
                println("FAIL\t#$n\t${req.shape}\topt=${req.optimize}\twalk=${req.spacing.tentToTent}->${result.walkwayMeters}\t$it")
            }
        }
        println("scenarios=$CASES tents=$tents skipped=$skipped compressed=$compressed failures=${failures.size}")
        assertTrue(failures.isEmpty(), "${failures.size} of $CASES camps broke a rule:\n" + failures.take(15).joinToString("\n"))
    }

    @Test
    fun sameRequestGivesSameDraft() {
        val rnd = Random(11)
        repeat(20) {
            val req = scenario(rnd)
            var a = 0
            var b = 0
            val first = AutoLayout.generate(req, presetsById) { "$it${a++}" }
            val second = AutoLayout.generate(req, presetsById) { "$it${b++}" }
            assertTrue(first == second, "draft changed between runs: $req")
        }
    }

    /** A roomy parcel (several times what the tents cover) must take every tent at the full walkway. */
    @Test
    fun roomyParcelsDropNothing() {
        val rnd = Random(5)
        val failures = mutableListOf<String>()
        repeat(ROOMY_CASES) { n ->
            // Up to 8 tents: 11 large ones in a half circle really need more than 60 x 50 m.
            val tents = List(2 + rnd.nextInt(7)) { tentIds[rnd.nextInt(tentIds.size)] }
            val sails = List(rnd.nextInt(3)) { sailIds[rnd.nextInt(sailIds.size)] }
            val req =
                AutoSetupRequest(
                    parcel = Parcel(60f, 50f),
                    tentPresetIds = tents,
                    sailPresetIds = sails,
                    visitorSide = VisitorSide.entries[rnd.nextInt(4)],
                    shape = RingShape.entries[rnd.nextInt(RingShape.entries.size)],
                    freeAreas = if (rnd.nextBoolean()) listOf(FreeArea(FreeAreaKind.FIRE, "Feuer", 2f)) else emptyList(),
                )
            var id = 0
            val result = AutoLayout.generate(req, presetsById) { "$it${id++}" }
            val problems = violations(req, result).toMutableList()
            if (result.skipped.isNotEmpty()) problems += "skipped ${result.skipped}"
            if (result.walkwayMeters < req.spacing.tentToTent) problems += "walkway shrank to ${result.walkwayMeters}"
            if (problems.isNotEmpty()) failures += "#$n ${req.shape} ${req.visitorSide} $tents $sails -> $problems"
        }
        assertTrue(failures.isEmpty(), "${failures.size} of $ROOMY_CASES roomy camps:\n" + failures.take(15).joinToString("\n"))
    }

    /** The walkway setting really spaces the tents: every pair keeps it between their safety margins. */
    @Test
    fun walkwaySettingSpacesTheTents() {
        val tents =
            listOf("saxon_3x5", "saxon_2x4", "saxon_2x4", "round_4", "a_tent_3x4_frame", "a_tent_2x3_basic", "saxon_4x6", "round_3x3")
        val failures = mutableListOf<String>()
        RingShape.entries.filter { it != RingShape.SPREAD }.forEach { shape ->
            val spacing =
                listOf(0.5f, 1f, 1.5f, 2f, 3f).map { w ->
                    val req =
                        AutoSetupRequest(
                            Parcel(80f, 60f),
                            tents,
                            listOf("sun_sail_6x6_flat"),
                            VisitorSide.TOP,
                            shape = shape,
                            spacing = SpacingRules(tentToTent = w),
                        )
                    var id = 0
                    val r = AutoLayout.generate(req, presetsById) { "$it${id++}" }
                    val t = r.assets.filterNot(::isSail)
                    val min = t.indices.minOf { i -> (t.indices - i).minOf { j -> walkway(t[i], t[j]) } }
                    if (r.walkwayMeters != w ||
                        r.skipped.isNotEmpty()
                    ) {
                        failures += "$shape $w: got ${r.walkwayMeters}, skipped ${r.skipped}"
                    }
                    if (min < w - 0.05f) failures += "$shape $w: closest pair only ${"%.2f".format(min)} m apart"
                    min
                }
            if (spacing.zipWithNext().any { (a, b) -> b < a - 0.05f }) failures += "$shape: spacing shrinks as the walkway grows $spacing"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** Free ground between two tents' safety margins (outline to outline). */
    private fun walkway(
        a: PlacedAsset,
        b: PlacedAsset,
    ): Float {
        val pa = GeometryEngine.assetWorldOutline(a, presetsById.getValue(a.presetId))
        val pb = GeometryEngine.assetWorldOutline(b, presetsById.getValue(b.presetId))
        if (GeometryEngine.polygonsIntersect(pa, pb)) return -1f

        fun toEdges(
            p: de.voculupo.historicalcamplayout.Vec2,
            poly: List<de.voculupo.historicalcamplayout.Vec2>,
        ) = poly.indices.minOf { k ->
            val s = poly[k]
            val e = poly[(k + 1) % poly.size]
            val dx = e.x - s.x
            val dy = e.y - s.y
            val t = (((p.x - s.x) * dx + (p.y - s.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
            kotlin.math.hypot(p.x - (s.x + t * dx), p.y - (s.y + t * dy))
        }
        return minOf(pa.minOf { toEdges(it, pb) }, pb.minOf { toEdges(it, pa) }) - a.safetyMarginMeters - b.safetyMarginMeters
    }

    private companion object {
        const val CASES = 300
        const val ROOMY_CASES = 80
    }
}
