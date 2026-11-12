package de.voculupo.historicalcamplayout.features.planner.state

import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.Parcel
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.Vec2
import de.voculupo.historicalcamplayout.defaultPresets
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoLayoutTest {
    private val presets = defaultPresets()
    private val presetsById = presets.associateBy { it.id }
    private val mixedTents =
        listOf("saxon_3x5", "saxon_2x4", "saxon_2x4", "round_4", "a_tent_3x4_frame", "a_tent_2x3_basic", "saxon_4x6", "round_3x3")
    private val parcel = Parcel(60f, 50f)

    private fun generate(req: AutoSetupRequest): List<PlacedAsset> {
        var n = 0
        return AutoLayout.generate(req, presetsById) { "$it${n++}" }
    }

    private fun isSail(a: PlacedAsset) = GeometryEngine.isSunSailPreset(presetsById.getValue(a.presetId))

    private fun assertCleanAndInside(assets: List<PlacedAsset>) {
        val report = GeometryEngine.evaluate(PlannerProject(parcel = parcel, assets = assets), presets)
        assertTrue(report.hardCollisions.isEmpty(), "hard collisions: ${report.messages}")
        assertTrue(report.messages.none { "Laufweg" in it || "Parzelle" in it }, "warnings: ${report.messages}")
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
                assertTrue((e.x * to.x + e.y * to.y) / len > 0.99f, "$side: ${tent.presetId} entry not facing sail")
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
}
