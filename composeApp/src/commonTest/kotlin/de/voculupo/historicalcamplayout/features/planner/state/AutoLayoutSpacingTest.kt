package de.voculupo.historicalcamplayout.features.planner.state

import de.voculupo.historicalcamplayout.AutoSetupRequest
import de.voculupo.historicalcamplayout.FreeArea
import de.voculupo.historicalcamplayout.FreeAreaKind
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.Parcel
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.RingShape
import de.voculupo.historicalcamplayout.SpacingOverride
import de.voculupo.historicalcamplayout.SpacingRules
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.VisitorSide
import de.voculupo.historicalcamplayout.defaultPresets
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The auto setup with the spacing rules, per-type overrides and the area search. */
class AutoLayoutSpacingTest {
    private val presets = defaultPresets()
    private val presetsById = presets.associateBy { it.id }
    private val tents = listOf("saxon_3x5", "saxon_2x4", "round_4", "a_tent_3x4_frame", "a_tent_2x3_basic", "round_3x3")
    private val sail = "sun_sail_6x6_flat"
    private val big = Parcel(70f, 60f)

    private fun generate(
        req: AutoSetupRequest,
        byId: Map<String, TentPreset> = presetsById,
    ): AutoLayoutResult {
        var n = 0
        return AutoLayout.generate(req, byId) { "$it${n++}" }
    }

    private fun messages(
        req: AutoSetupRequest,
        r: AutoLayoutResult,
        byId: Map<String, TentPreset> = presetsById,
    ) = GeometryEngine
        .evaluate(PlannerProject(parcel = req.parcel, assets = r.assets, blockZones = r.zones, spacing = req.spacing), byId.values.toList())
        .messages

    private fun isSail(a: PlacedAsset) = GeometryEngine.isSunSailPreset(presetsById.getValue(a.presetId))

    /** Shortest padded-box gap from any tent to any sail. */
    private fun closestToSail(r: AutoLayoutResult): Float {
        val sails = r.assets.filter(::isSail).map { GeometryEngine.assetAabb(it, presetsById.getValue(it.presetId)) }
        return r.assets.filterNot(::isSail).minOf { t ->
            val b = GeometryEngine.assetAabb(t, presetsById.getValue(t.presetId))
            sails.minOf { s -> maxOf(s.left - b.right, b.left - s.right, s.top - b.bottom, b.top - s.bottom) }
        }
    }

    @Test
    fun customWalkway_draftHasNoWarningsUnderTheSameRules() {
        RingShape.entries.forEach { shape ->
            listOf(0.5f, 2.5f).forEach { walk ->
                val req =
                    AutoSetupRequest(big, tents, listOf(sail), VisitorSide.BOTTOM, shape = shape, spacing = SpacingRules(tentToTent = walk))
                val r = generate(req)
                assertTrue(r.skipped.isEmpty(), "$shape $walk skipped ${r.skipped}")
                assertEquals(walk, r.walkwayMeters, "$shape $walk was squeezed")
                val bad = messages(req, r).filter { "Laufweg" in it || "Kollision" in it || "Eingang" in it }
                assertTrue(bad.isEmpty(), "$shape $walk: $bad")
            }
        }
    }

    @Test
    fun tentToSail_movesTentsCloserOrFarther() {
        listOf(RingShape.U_SHAPE, RingShape.HORSESHOE, RingShape.FULL_CIRCLE).forEach { shape ->
            fun run(gap: Float): Float {
                val req =
                    AutoSetupRequest(
                        big,
                        tents,
                        listOf(sail),
                        VisitorSide.BOTTOM,
                        shape = shape,
                        spacing = SpacingRules(tentToSail = gap),
                        optimize = false,
                    )
                val r = generate(req)
                assertTrue(r.skipped.isEmpty(), "$shape $gap skipped ${r.skipped}")
                assertTrue(messages(req, r).none { "Laufweg" in it }, "$shape $gap: ${messages(req, r)}")
                return closestToSail(r)
            }
            val near = run(0.5f)
            val far = run(4f)
            assertTrue(far >= 4f - 0.05f, "$shape: closest tent only $far m from the sail, wanted 4")
            assertTrue(near < far, "$shape: 0.5 m gives $near, 4 m gives $far")
        }
    }

    @Test
    fun lanes_areTaggedAndKeepThePathGap() {
        val lane = FreeArea(FreeAreaKind.LANE, "Gasse", 3f)
        listOf(RingShape.U_SHAPE, RingShape.HORSESHOE, RingShape.EDGE, RingShape.SPREAD).forEach { shape ->
            val req =
                AutoSetupRequest(
                    big,
                    tents,
                    listOf(sail, sail),
                    VisitorSide.BOTTOM,
                    shape = shape,
                    freeAreas = listOf(lane),
                    spacing = SpacingRules(tentToPath = 3f, sailToPath = 2f),
                )
            val r = generate(req)
            assertEquals(FreeAreaKind.LANE, r.zones.single { it.label == "Gasse" }.kind)
            assertTrue(r.skipped.isEmpty(), "$shape skipped ${r.skipped}")
            assertTrue(messages(req, r).none { "Abstand zum Weg" in it }, "$shape: ${messages(req, r)}")
        }
    }

    @Test
    fun otherFreeAreas_stayUntagged() {
        val req =
            AutoSetupRequest(
                big,
                tents,
                listOf(sail),
                VisitorSide.BOTTOM,
                freeAreas = listOf(FreeArea(FreeAreaKind.ARENA, "Kampfplatz", 8f, 6f), FreeArea(FreeAreaKind.FIRE, "Feuer", 2f)),
            )
        val zones = generate(req).zones.associateBy { it.label }
        assertEquals(null, zones.getValue("Kampfplatz").kind)
        assertEquals(FreeAreaKind.FIRE, zones.getValue("Feuer").kind)
    }

    @Test
    fun optimizer_doesNotAddTooFarFromSailWarnings() {
        RingShape.entries.forEach { shape ->
            val base =
                AutoSetupRequest(big, tents, listOf(sail), VisitorSide.BOTTOM, shape = shape, spacing = SpacingRules(tentToSailMax = 6f))

            fun far(r: AutoLayoutResult) = messages(base, r).count { "Zu weit" in it }
            val tuned = generate(base)
            val draft = generate(base.copy(optimize = false))
            assertTrue(far(tuned) <= far(draft), "$shape: optimizer ${far(tuned)} vs draft ${far(draft)} too far from the sail")
        }
    }

    @Test
    fun squeezedDraft_reportsTheSqueezeAndStaysCollisionFree() {
        val req =
            AutoSetupRequest(
                Parcel(16f, 11f),
                List(6) { "a_tent_3x4_frame" },
                emptyList(),
                VisitorSide.TOP,
                spacing = SpacingRules(tentToTent = 3f),
            )
        val r = generate(req)
        assertTrue(r.walkwayMeters < 3f, "6 frame tents on 16 x 11 m can't keep 3 m")
        val report = GeometryEngine.evaluate(PlannerProject(parcel = req.parcel, assets = r.assets), presets)
        assertTrue(report.hardCollisions.isEmpty(), report.messages.toString())
    }

    @Test
    fun overridesSmallerThanTheCamp_letTentsCloser() {
        val tight = presetsById.getValue("a_tent_3x4_frame").copy(spacing = SpacingOverride(toTents = 0.3f))
        val byId = presetsById + (tight.id to tight)
        val req = AutoSetupRequest(Parcel(18f, 12f), List(6) { tight.id }, emptyList(), VisitorSide.TOP, shape = RingShape.U_SHAPE)
        val r = generate(req, byId)
        assertTrue(r.skipped.isEmpty(), "tents that want only 0.3 m fit: skipped ${r.skipped}")
        assertTrue(messages(req, r, byId).none { "Laufweg" in it }, messages(req, r, byId).toString())
    }

    @Test
    fun requiredParcel_everyShape_fitsWithoutSqueezing() {
        RingShape.entries.forEach { shape ->
            val req = AutoSetupRequest(Parcel(10f, 10f), tents.take(4), listOf(sail), VisitorSide.BOTTOM, shape = shape)
            val p = assertNotNull(AutoLayout.requiredParcel(req, presetsById), "$shape found nothing")
            val r = generate(req.copy(parcel = p))
            assertTrue(r.skipped.isEmpty() && r.walkwayMeters >= 1.5f, "$shape on $p: skipped ${r.skipped}, walkway ${r.walkwayMeters}")
            assertEquals(0f, (p.widthMeters * 2) % 1f, "$shape width $p not in half metres")
            assertEquals(0f, (p.heightMeters * 2) % 1f, "$shape depth $p not in half metres")
        }
    }

    @Test
    fun requiredParcel_growsWithWiderGaps_andCountsFreeAreas() {
        val base = AutoSetupRequest(Parcel(20f, 15f), tents.take(4), listOf(sail), VisitorSide.BOTTOM, shape = RingShape.U_SHAPE)

        fun area(r: AutoSetupRequest) = assertNotNull(AutoLayout.requiredParcel(r, presetsById)).let { it.widthMeters * it.heightMeters }
        val plain = area(base)
        assertTrue(area(base.copy(spacing = SpacingRules(tentToTent = 3f, tentToSail = 3f))) > plain, "wider gaps need more ground")
        val arena = base.copy(freeAreas = listOf(FreeArea(FreeAreaKind.ARENA, "Kampfplatz", 10f, 8f)))
        val withArena = assertNotNull(AutoLayout.requiredParcel(arena, presetsById))
        assertTrue(withArena.widthMeters * withArena.heightMeters > plain, "an arena needs room too")
        assertTrue(generate(arena.copy(parcel = withArena)).skipped.isEmpty())
    }

    @Test
    fun requiredParcel_ignoresPinsAndStartSize() {
        val req = AutoSetupRequest(Parcel(10f, 10f), tents.take(3), listOf(sail), VisitorSide.BOTTOM, shape = RingShape.HORSESHOE)
        val pinned = req.copy(clusterPins = listOf(de.voculupo.historicalcamplayout.Vec2(1f, 1f)))
        assertEquals(AutoLayout.requiredParcel(req, presetsById), AutoLayout.requiredParcel(pinned, presetsById))
    }

    @Test
    fun requiredParcel_givesUpOnImpossibleRequests() {
        // A rope-free frame tent needing 500 m to everything cannot fit 16x a 2 x 2 m start.
        val lonely = presetsById.getValue("a_tent_3x4_frame").copy(spacing = SpacingOverride(toTents = 500f))
        val req = AutoSetupRequest(Parcel(2f, 2f), List(2) { lonely.id }, emptyList(), VisitorSide.TOP)
        assertEquals(null, AutoLayout.requiredParcel(req, presetsById + (lonely.id to lonely)))
    }

    /** Random rules, overrides and camps: uncompressed drafts must keep every rule under their own spacing. */
    @Test
    fun randomRulesAndOverrides_keepEveryRule() {
        val rnd = Random(77)
        val tentIds = presets.filterNot(GeometryEngine::isSunSailPreset).map { it.id }
        val sailIds = presets.filter(GeometryEngine::isSunSailPreset).map { it.id }
        val gaps = listOf(0.5f, 1f, 1.5f, 2f, 3f)
        val failures = mutableListOf<String>()
        repeat(RANDOM_CASES) { n ->
            val own =
                tentIds.shuffled(rnd).take(3).associateWith {
                    SpacingOverride(
                        toTents = gaps.random(rnd).takeIf { rnd.nextBoolean() },
                        toSails = gaps.random(rnd).takeIf { rnd.nextBoolean() },
                    )
                }
            val byId = presetsById.mapValues { (id, p) -> own[id]?.let { p.copy(spacing = it) } ?: p }
            val rules = SpacingRules(gaps.random(rnd), gaps.random(rnd), null, gaps.random(rnd), gaps.random(rnd))
            val req =
                AutoSetupRequest(
                    parcel = Parcel(listOf(25f, 40f, 60f)[rnd.nextInt(3)], listOf(20f, 30f, 50f)[rnd.nextInt(3)]),
                    tentPresetIds = List(1 + rnd.nextInt(10)) { tentIds.random(rnd) },
                    sailPresetIds = List(rnd.nextInt(3)) { sailIds.random(rnd) },
                    visitorSide = VisitorSide.entries.random(rnd),
                    shape = RingShape.entries.random(rnd),
                    freeAreas = if (rnd.nextInt(3) == 0) listOf(FreeArea(FreeAreaKind.LANE, "Gasse", 3f)) else emptyList(),
                    spacing = rules,
                    optimize = rnd.nextBoolean(),
                )
            val r = generate(req, byId)
            val msgs = messages(req, r, byId)
            val bad = msgs.filter { "Kollision" in it || "Parzelle" in it || "Eingang" in it || "Sperrzone" in it }.toMutableList()
            if (r.walkwayMeters >= rules.tentToTent) bad += msgs.filter { "Laufweg" in it || "Abstand zum Weg" in it }
            val placed = r.assets.count { !GeometryEngine.isSunSailPreset(byId.getValue(it.presetId)) }
            val skippedTents = r.skipped.count { name -> req.tentPresetIds.any { byId.getValue(it).name == name } }
            if (placed + skippedTents != req.tentPresetIds.size) bad += "lost tents: $placed + $skippedTents of ${req.tentPresetIds.size}"
            if (bad.isNotEmpty()) failures += "#$n ${req.shape} $rules own=$own -> $bad"
        }
        assertTrue(failures.isEmpty(), "${failures.size} of $RANDOM_CASES camps:\n" + failures.take(10).joinToString("\n"))
    }

    private companion object {
        const val RANDOM_CASES = 150
    }
}
