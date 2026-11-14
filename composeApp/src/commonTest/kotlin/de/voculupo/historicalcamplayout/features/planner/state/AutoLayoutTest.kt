package de.voculupo.historicalcamplayout.features.planner.state

import de.voculupo.historicalcamplayout.AutoSetupRequest
import de.voculupo.historicalcamplayout.BlockZone
import de.voculupo.historicalcamplayout.FreeArea
import de.voculupo.historicalcamplayout.FreeAreaKind
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.Parcel
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerJson
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.RingShape
import de.voculupo.historicalcamplayout.SpacingRules
import de.voculupo.historicalcamplayout.Vec2
import de.voculupo.historicalcamplayout.VisitorSide
import de.voculupo.historicalcamplayout.data.storage.toJsonString
import de.voculupo.historicalcamplayout.defaultPresets
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutoLayoutTest {
    private val presets = defaultPresets()
    private val presetsById = presets.associateBy { it.id }
    private val mixedTents =
        listOf("saxon_3x5", "saxon_2x4", "saxon_2x4", "round_4", "a_tent_3x4_frame", "a_tent_2x3_basic", "saxon_4x6", "round_3x3")
    private val parcel = Parcel(60f, 50f)

    private fun run(req: AutoSetupRequest): AutoLayoutResult {
        var n = 0
        val result = AutoLayout.generate(req, presetsById) { "$it${n++}" }
        // NaN positions pass every collision check silently, so rule them out up front.
        assertTrue(
            result.assets.all { it.position.x.isFinite() && it.position.y.isFinite() && it.rotationDeg.isFinite() },
            "non-finite pose",
        )
        return result
    }

    private fun generate(req: AutoSetupRequest): List<PlacedAsset> = run(req).assets

    private fun report(
        result: AutoLayoutResult,
        parcel: Parcel,
    ) = GeometryEngine.evaluate(PlannerProject(parcel = parcel, assets = result.assets, blockZones = result.zones), presets)

    private fun isSail(a: PlacedAsset) = GeometryEngine.isSunSailPreset(presetsById.getValue(a.presetId))

    private fun assertCleanAndInside(assets: List<PlacedAsset>) {
        val report = GeometryEngine.evaluate(PlannerProject(parcel = parcel, assets = assets), presets)
        assertTrue(report.hardCollisions.isEmpty(), "hard collisions: ${report.messages}")
        assertTrue(report.messages.none { "Laufweg" in it || "Parzelle" in it || "Eingang" in it }, "warnings: ${report.messages}")
    }

    /** Unit vector the tent entry (local -y) points to in world space. */
    private fun entryDir(a: PlacedAsset) = GeometryEngine.rotatePoint(Vec2(0f, -1f), a.rotationDeg)

    @Test
    fun horseshoeOpensToEachVisitorSideWithEntriesFacingSail() {
        // Direction (world) pointing from the camp toward the visitors.
        val towardVisitors =
            mapOf(
                VisitorSide.TOP to Vec2(0f, -1f),
                VisitorSide.BOTTOM to Vec2(0f, 1f),
                VisitorSide.LEFT to Vec2(-1f, 0f),
                VisitorSide.RIGHT to Vec2(1f, 0f),
            )
        towardVisitors.forEach { (side, up) ->
            val assets = generate(AutoSetupRequest(parcel, mixedTents, listOf("sun_sail_6x6_flat"), side))
            assertEquals(mixedTents.size + 1, assets.size)
            assertCleanAndInside(assets)
            val sail = assets.single(::isSail).position
            assets.filterNot(::isSail).forEach { tent ->
                val to = Vec2(sail.x - tent.position.x, sail.y - tent.position.y)
                val len = hypot(to.x, to.y)
                val e = entryDir(tent)
                // Squared up to the edges, so the entry is at most 30 degrees off the sail direction.
                assertTrue((e.x * to.x + e.y * to.y) / len > 0.86f, "$side: ${tent.presetId} entry not facing sail")
                assertTrue(tent.rotationDeg % 45f == 0f, "$side: ${tent.presetId} rotated ${tent.rotationDeg}")
                // Gap: no tent within ±60° of the visitor direction, seen from the sail.
                val cos = -(to.x * up.x + to.y * up.y) / len
                assertTrue(cos < 0.5f, "$side: ${tent.presetId} blocks the visitor gap")
            }
        }
    }

    @Test
    fun oneRingPerSail() {
        val tents = List(10) { "saxon_2x4" }
        val assets = generate(AutoSetupRequest(parcel, tents, listOf("sun_sail_4x4_flat", "sun_sail_4x4_flat"), VisitorSide.TOP))
        assertCleanAndInside(assets)
        val groups = assets.groupBy { it.groupId }
        assertEquals(2, groups.size)
        groups.values.forEach { g -> assertEquals(5, g.count { !isSail(it) }) }
    }

    @Test
    fun joinedSailsShareEdgeAndOneRing() {
        val assets =
            generate(
                AutoSetupRequest(
                    parcel,
                    mixedTents,
                    listOf("sun_sail_4x4_flat", "sun_sail_4x4_flat"),
                    VisitorSide.BOTTOM,
                    joinSails = true,
                ),
            )
        assertCleanAndInside(assets)
        assertEquals(1, assets.map { it.groupId }.distinct().size)
        val (a, b) = assets.filter(::isSail)
        assertTrue(GeometryEngine.areSunSailsJoinedByEdge(a, presetsById.getValue(a.presetId), b, presetsById.getValue(b.presetId)))
    }

    @Test
    fun shapeControlsAngularSpread() {
        listOf(RingShape.HALF_CIRCLE, RingShape.FULL_CIRCLE).forEach { shape ->
            val assets = generate(AutoSetupRequest(parcel, mixedTents, listOf("sun_sail_6x6_flat"), VisitorSide.TOP, shape = shape))
            assertCleanAndInside(assets)
            val sail = assets.single(::isSail).position
            val angles = assets.filterNot(::isSail).map { atan2(it.position.y - sail.y, it.position.x - sail.x) * 180f / PI.toFloat() }
            if (shape == RingShape.HALF_CIRCLE) {
                // Every tent behind the sail (+y = away from visitors on top).
                assertTrue(angles.all { it in -1f..181f }, "half circle angles $angles")
            } else {
                assertTrue(angles.any { abs(it + 90f) < 30f }, "full circle should also close on the visitor side: $angles")
            }
        }
    }

    @Test
    fun noSailsStillBuildsRing() {
        val assets = generate(AutoSetupRequest(parcel, mixedTents, emptyList(), VisitorSide.LEFT))
        assertEquals(mixedTents.size, assets.size)
        assertCleanAndInside(assets)
    }

    @Test
    fun smallParcelKeepsEverythingInside() {
        val small = Parcel(20f, 15f)
        val tents = mixedTents + "saxon_2x4" + "saxon_2x4"
        val result = run(AutoSetupRequest(small, tents, listOf("sun_sail_4x4_flat"), VisitorSide.BOTTOM))
        val r = report(result, small)
        assertTrue(r.hardCollisions.isEmpty(), "hard collisions: ${r.messages}")
        assertTrue(r.messages.none { "Parzelle" in it || "Eingang" in it }, "outside parcel / blocked entry: ${r.messages}")
        assertTrue(result.skipped.isNotEmpty() || result.walkwayMeters < 1.5f, "10 tents don't fit on 20x15 with 1.5 m walkways")
        assertEquals(tents.size, result.assets.count { !isSail(it) } + result.skipped.size)
        assertTrue(result.assets.any { it.presetId == tents.first() }, "highest priority tent is placed")
    }

    @Test
    fun arenaSitsBesideCampWithoutCollisions() {
        val arena = FreeArea(FreeAreaKind.ARENA, "Kampfplatz", 12f, 10f)
        val result = run(AutoSetupRequest(parcel, mixedTents, listOf("sun_sail_6x6_flat"), VisitorSide.TOP, freeAreas = listOf(arena)))
        val zone = result.zones.single()
        assertEquals("Kampfplatz", zone.label)
        assertTrue(zone.id.startsWith(AutoLayout.AUTO_PREFIX))
        assertTrue(zone.points.all { it.x in 0f..parcel.widthMeters && it.y in 0f..parcel.heightMeters })
        val r = report(result, parcel)
        assertTrue(r.hardCollisions.isEmpty(), "hard collisions: ${r.messages}")
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun fireGoesIntoOpenGroundTwoMetresFromTents() {
        val fire = FreeArea(FreeAreaKind.FIRE, "Feuer", 2f)
        val result = run(AutoSetupRequest(parcel, mixedTents, listOf("sun_sail_6x6_flat"), VisitorSide.LEFT, freeAreas = listOf(fire)))
        val zone = result.zones.single()
        assertTrue(result.skipped.isEmpty())
        assertTrue(report(result, parcel).hardCollisions.isEmpty())
        result.assets.forEach { a ->
            val body = GeometryEngine.assetBodyAabb(a, presetsById.getValue(a.presetId))
            val gap =
                maxOf(
                    zone.points.minOf { it.x } - body.right,
                    body.left - zone.points.maxOf { it.x },
                    zone.points.minOf { it.y } - body.bottom,
                    body.top - zone.points.maxOf { it.y },
                )
            assertTrue(gap >= 1.99f, "${a.presetId} only $gap m from the fire")
        }
    }

    @Test
    fun laneRunsFromVisitorEdgeToBack() {
        val lane = FreeArea(FreeAreaKind.LANE, "Gasse", 3f)
        val sails = listOf("sun_sail_4x4_flat", "sun_sail_4x4_flat")
        val result = run(AutoSetupRequest(parcel, mixedTents, sails, VisitorSide.TOP, freeAreas = listOf(lane)))
        val ys =
            result.zones
                .single()
                .points
                .map { it.y }
        assertEquals(0f, ys.min(), 0.01f)
        assertEquals(parcel.heightMeters, ys.max(), 0.01f)
        assertTrue(report(result, parcel).hardCollisions.isEmpty())
    }

    @Test
    fun pinnedRingStaysAtPin() {
        val pin = Vec2(40f, 25f)
        val result = run(AutoSetupRequest(parcel, mixedTents, listOf("sun_sail_6x6_flat"), VisitorSide.TOP, clusterPins = listOf(pin)))
        val sail = result.assets.single(::isSail).position
        assertEquals(pin.x, sail.x, 0.01f)
        assertEquals(pin.y, sail.y, 0.01f)
        val r = report(result, parcel)
        assertTrue(r.hardCollisions.isEmpty() && r.messages.none { "Parzelle" in it }, r.messages.toString())
    }

    @Test
    fun firstTentGetsBackCentreAndRingAssignmentIsRespected() {
        val tents = listOf("round_3x3") + mixedTents.filterNot { it == "round_3x3" }
        val one = run(AutoSetupRequest(parcel, tents, listOf("sun_sail_6x6_flat"), VisitorSide.TOP))
        val sail = one.assets.single(::isSail).position
        val first = one.assets.single { it.presetId == "round_3x3" }.position
        assertEquals(sail.x, first.x, 0.01f)
        assertTrue(first.y > sail.y, "back centre is away from the visitors")

        val two =
            run(
                AutoSetupRequest(
                    parcel,
                    tents,
                    listOf("sun_sail_4x4_flat", "sun_sail_4x4_flat"),
                    VisitorSide.TOP,
                    tentRings = listOf(1),
                ),
            )
        val ring1 = two.blocks.single { it.key == BlockKey(true, 1) }
        val firstId = two.assets.single { it.presetId == "round_3x3" }.id
        assertTrue(firstId in ring1.assetIds)
    }

    @Test
    fun projectWithAutoSetupSurvivesJson() {
        val req =
            AutoSetupRequest(
                parcel,
                mixedTents,
                listOf("sun_sail_6x6_flat"),
                VisitorSide.RIGHT,
                RingShape.HALF_CIRCLE,
                freeAreas = listOf(FreeArea(FreeAreaKind.ARENA, "Kampfplatz", 12f, 10f, Vec2(5f, 5f))),
                clusterPins = listOf(null),
                tentRings = listOf(0, null),
            )
        val project = PlannerProject(autoSetup = req, blockZones = listOf(BlockZone("auto-1", listOf(Vec2(0f, 0f)), "Kampfplatz")))
        val back = PlannerJson.json.decodeFromString(PlannerProject.serializer(), project.toJsonString())
        assertEquals(project, back)
    }

    @Test
    fun clothToClothFitsMoreWithoutOverlap() {
        // Frame tents have no guy ropes, so cloth to cloth really means touching walls.
        val tight = Parcel(16f, 11f)
        val tents = List(6) { "a_tent_3x4_frame" }
        val roomy =
            run(
                AutoSetupRequest(
                    tight,
                    tents,
                    emptyList(),
                    VisitorSide.TOP,
                    spacing = SpacingRules(tentToTent = 1.5f),
                    shape = RingShape.HALF_CIRCLE,
                ),
            )
        val cloth =
            run(
                AutoSetupRequest(
                    tight,
                    tents,
                    emptyList(),
                    VisitorSide.TOP,
                    spacing = SpacingRules(tentToTent = 0f),
                    shape = RingShape.HALF_CIRCLE,
                ),
            )
        assertTrue(cloth.assets.size >= roomy.assets.size, "cloth to cloth places ${cloth.assets.size}, roomy ${roomy.assets.size}")
        val r = report(cloth, tight)
        assertTrue(r.hardCollisions.isEmpty(), "hard collisions: ${r.messages}")
        assertTrue(r.messages.none { "Parzelle" in it }, r.messages.toString())
    }

    @Test
    fun clothToClothSwingsGuyRopesBehindNeighbours() {
        val tight = Parcel(14f, 10f)
        val tents = List(6) { "saxon_2x4" }
        val cloth = run(AutoSetupRequest(tight, tents, emptyList(), VisitorSide.TOP, spacing = SpacingRules(tentToTent = 0f)))
        assertTrue(cloth.assets.size >= 4, "cloth to cloth placed only ${cloth.assets.size}")
        val r = report(cloth, tight)
        assertTrue(r.hardCollisions.isEmpty(), "hard collisions: ${r.messages}")
        val defaults = PlannerDomainOps.defaultRopes(presetsById.getValue("saxon_2x4")).map { it.outwardAngleDeg }
        assertTrue(
            cloth.assets.any { a -> a.ropes.map { it.outwardAngleDeg } != defaults },
            "some ropes were swung: " +
                cloth.assets.joinToString {
                    "(" + it.position.x + "," + it.position.y + " r" + it.rotationDeg + " " +
                        it.ropes.map { r -> r.outwardAngleDeg } +
                        ")"
                } +
                " skipped=" +
                cloth.skipped,
        )
    }

    @Test
    fun uShapeHasStraightBackRowAndInwardSideRows() {
        VisitorSide.entries.forEach { side ->
            val result = run(AutoSetupRequest(parcel, mixedTents, listOf("sun_sail_6x6_flat"), side, shape = RingShape.U_SHAPE))
            val r = report(result, parcel)
            assertTrue(r.hardCollisions.isEmpty(), "$side hard collisions: ${r.messages}")
            assertTrue(r.messages.none { "Parzelle" in it || "Laufweg" in it }, "$side: ${r.messages}")
            assertTrue(result.skipped.isEmpty())
            val sail = result.assets.single(::isSail).position
            result.assets.filterNot(::isSail).forEach { tent ->
                assertTrue(tent.rotationDeg % 90f == 0f, "$side: ${tent.presetId} rotated ${tent.rotationDeg}")
                val to = Vec2(sail.x - tent.position.x, sail.y - tent.position.y)
                val e = entryDir(tent)
                assertTrue(e.x * to.x + e.y * to.y > 0f, "$side: ${tent.presetId} entry faces away from the sail")
            }
        }
        // Visitors on top: the first (priority) tent sits straight behind the sail, the back row is level.
        val top = run(AutoSetupRequest(parcel, mixedTents, listOf("sun_sail_6x6_flat"), VisitorSide.TOP, shape = RingShape.U_SHAPE))
        val sail = top.assets.single(::isSail).position
        val first = top.assets.single { it.presetId == mixedTents.first() }
        assertEquals(sail.x, first.position.x, 0.5f)
        assertTrue(first.position.y > sail.y)
        assertEquals(0f, first.rotationDeg)
    }

    @Test
    fun emptyOrUnknownRequestGivesEmptyDraft() {
        // The sheet opens with nothing selected yet; that must not crash the live preview.
        listOf(emptyList(), listOf("no_such_preset")).forEach { ids ->
            val result = run(AutoSetupRequest(parcel, ids, ids, VisitorSide.BOTTOM, shape = RingShape.U_SHAPE))
            assertTrue(result.assets.isEmpty() && result.zones.isEmpty() && result.skipped.isEmpty())
        }
    }

    @Test
    fun tentInFrontOfAnEntryIsReported() {
        val saxon = presetsById.getValue("saxon_3x5")
        val front = PlacedAsset(id = "a", presetId = saxon.id, position = Vec2(10f, 10f))
        // Entry points to -y; a small tent 4 m in front of it stands in the door.
        val blocker = PlacedAsset(id = "b", presetId = "round_3x3", position = Vec2(10f, 5.2f))
        assertTrue(GeometryEngine.blocksEntry(front, saxon, blocker, presetsById.getValue("round_3x3")))
        val r = GeometryEngine.evaluate(PlannerProject(parcel = parcel, assets = listOf(front, blocker)), presets)
        assertTrue(r.messages.any { "Eingang" in it }, r.messages.toString())
    }

    @Test
    fun edgeKeepsTentsOnBackAndSideEdges() {
        val result = run(AutoSetupRequest(parcel, mixedTents, listOf("sun_sail_6x6_flat"), VisitorSide.TOP, shape = RingShape.EDGE))
        assertCleanAndInside(result.assets)
        assertTrue(result.skipped.isEmpty())
        result.assets.filterNot(::isSail).forEach { tent ->
            val b = GeometryEngine.assetBodyAabb(tent, presetsById.getValue(tent.presetId))
            // Visitors on top: tents stand against the left, right or bottom edge, never the visitor edge.
            val onEdge = minOf(b.left, parcel.widthMeters - b.right, parcel.heightMeters - b.bottom)
            assertTrue(onEdge < 0.6f, "${tent.presetId} is not on an edge ($b)")
        }
    }

    @Test
    fun spreadKeepsTentsFarApart() {
        val spread = run(AutoSetupRequest(parcel, mixedTents, listOf("sun_sail_6x6_flat"), VisitorSide.TOP, shape = RingShape.SPREAD))
        val u = run(AutoSetupRequest(parcel, mixedTents, listOf("sun_sail_6x6_flat"), VisitorSide.TOP, shape = RingShape.U_SHAPE))
        assertCleanAndInside(spread.assets)
        assertTrue(spread.skipped.isEmpty())

        fun closest(assets: List<PlacedAsset>): Float {
            val tents = assets.filterNot(::isSail)
            return tents.indices.minOf { i ->
                (tents.indices - i).minOf { j ->
                    hypot(tents[i].position.x - tents[j].position.x, tents[i].position.y - tents[j].position.y)
                }
            }
        }
        assertTrue(closest(spread.assets) > closest(u.assets), "spread ${closest(spread.assets)} vs U ${closest(u.assets)}")
    }

    @Test
    fun noEntryFacesTheFireOrArenaEvenWhenCompressed() {
        val small = Parcel(25f, 15f)
        val fire = FreeArea(FreeAreaKind.FIRE, "Feuerstelle", 2f)
        val arena = FreeArea(FreeAreaKind.ARENA, "Kampfplatz", 5f, 10f)
        listOf(RingShape.HALF_CIRCLE, RingShape.HORSESHOE, RingShape.U_SHAPE).forEach { shape ->
            val result =
                run(
                    AutoSetupRequest(
                        small,
                        listOf("saxon_4x6", "saxon_2x4", "saxon_2x4", "round_4", "a_tent_3x4_frame"),
                        listOf("sun_sail_4x4_flat"),
                        VisitorSide.BOTTOM,
                        shape = shape,
                        freeAreas = listOf(arena, fire),
                    ),
                )
            val zones = result.zones
            result.assets.filterNot(::isSail).forEach { tent ->
                val preset = presetsById.getValue(tent.presetId)
                val door = assertNotNull(GeometryEngine.entryZone(tent, preset))
                val body = GeometryEngine.assetWorldOutline(tent, preset)
                zones.forEach { zone ->
                    assertTrue(
                        !GeometryEngine.polygonsIntersect(door, zone.points),
                        "$shape: ${tent.presetId} door opens into ${zone.label}",
                    )
                    val gap =
                        maxOf(
                            zone.points.minOf { it.x } - body.maxOf { it.x },
                            body.minOf { it.x } - zone.points.maxOf { it.x },
                            zone.points.minOf { it.y } - body.maxOf { it.y },
                            body.minOf { it.y } - zone.points.maxOf { it.y },
                        )
                    assertTrue(gap >= 0.99f, "$shape: ${tent.presetId} only $gap m from ${zone.label}")
                }
            }
        }
    }

    private fun problems(
        result: AutoLayoutResult,
        parcel: Parcel,
    ): Int {
        val r = report(result, parcel)
        return r.hardCollisions.size + r.messages.count { "Eingang" in it || "Parzelle" in it }
    }

    @Test
    fun optimizerNeverAddsProblems() {
        val cases =
            listOf(
                AutoSetupRequest(Parcel(22f, 16f), mixedTents, listOf("sun_sail_4x4_flat"), VisitorSide.BOTTOM, shape = RingShape.U_SHAPE),
                AutoSetupRequest(
                    Parcel(25f, 15f),
                    mixedTents,
                    listOf("sun_sail_4x4_flat"),
                    VisitorSide.BOTTOM,
                    shape = RingShape.HALF_CIRCLE,
                ),
                AutoSetupRequest(Parcel(36f, 24f), mixedTents, listOf("sun_sail_6x6_flat"), VisitorSide.TOP, shape = RingShape.SPREAD),
                AutoSetupRequest(
                    Parcel(30f, 20f),
                    mixedTents,
                    listOf("sun_sail_6x6_flat"),
                    VisitorSide.LEFT,
                    clusterPins = listOf(Vec2(5f, 5f)),
                ),
            )
        cases.forEach { req ->
            val plain = run(req.copy(optimize = false))
            val tuned = run(req)
            assertTrue(problems(tuned, req.parcel) <= problems(plain, req.parcel), "${req.shape}: ${report(tuned, req.parcel).messages}")
            assertEquals(plain.assets.size, tuned.assets.size)
        }
    }

    @Test
    fun optimizerPullsTentsTowardADraggedSail() {
        val req =
            AutoSetupRequest(
                Parcel(30f, 20f),
                mixedTents,
                listOf("sun_sail_6x6_flat"),
                VisitorSide.BOTTOM,
                clusterPins = listOf(Vec2(6f, 6f)),
            )

        fun spread(result: AutoLayoutResult): Float {
            val sail = result.assets.single(::isSail).position
            return result.assets
                .filterNot(::isSail)
                .map { hypot(it.position.x - sail.x, it.position.y - sail.y) }
                .average()
                .toFloat()
        }
        val plain = run(req.copy(optimize = false))
        val tuned = run(req)
        assertTrue(spread(tuned) < spread(plain), "tuned ${spread(tuned)} vs plain ${spread(plain)}")
        assertTrue(report(tuned, req.parcel).hardCollisions.isEmpty())
    }

    @Test
    fun requiredParcel_isTheSmallestThatFits() {
        val req =
            AutoSetupRequest(
                Parcel(10f, 8f),
                mixedTents.take(5),
                listOf("sun_sail_6x6_flat"),
                VisitorSide.BOTTOM,
                shape = RingShape.U_SHAPE,
            )
        val found = assertNotNull(AutoLayout.requiredParcel(req, presetsById))

        fun fits(p: Parcel) = run(req.copy(parcel = p, optimize = false)).let { it.skipped.isEmpty() && it.walkwayMeters >= 1.5f }
        assertTrue(fits(found), "found $found must fit")
        assertTrue(!fits(found.copy(widthMeters = found.widthMeters - 1f)), "1 m narrower than $found still fits")
        assertTrue(!fits(found.copy(heightMeters = found.heightMeters - 1f)), "1 m shallower than $found still fits")
    }

    @Test
    fun requiredParcel_nothingChosen_isNull() {
        assertEquals(null, AutoLayout.requiredParcel(AutoSetupRequest(parcel, emptyList(), emptyList(), VisitorSide.TOP), presetsById))
    }
}
