package de.voculupo.historicalcamplayout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeometryEngineSunSailTest {
    private val preset = squareSunSailPreset()

    @Test
    fun snapSunSailToNeighbor_alignsEdges() {
        val moving = sail("moving", 4f)
        val target = sail("target", 8.3f)

        val snapped = GeometryEngine.snapSunSailToNeighbor(moving, preset, target, preset)
        assertNotNull(snapped)
        assertTrue(abs(snapped.x - 4.3f) < 0.001f && abs(snapped.y - 6f) < 0.001f, "expected (4.3, 6), got $snapped")
        assertTrue(GeometryEngine.areSunSailsJoinedByEdge(moving.copy(position = snapped), preset, target, preset))
    }

    @Test
    fun areSunSailsJoinedByEdge_returnsTrue_whenEdgesTouch() {
        assertTrue(GeometryEngine.areSunSailsJoinedByEdge(sail("left", 4f), preset, sail("right", 8f), preset))
    }

    @Test
    fun joinHiddenRopeIndices_middleSailOfThree_hidesRopesOnBothEdges() {
        val project = projectOf(sail("left", 4f), sail("mid", 8f), sail("right", 12f))

        val hidden = GeometryEngine.joinHiddenRopeIndices(project, listOf(preset))

        // Middle sail: left edge (x = -2) ropes 0, 3, 7 and right edge (x = 2) ropes 1, 2, 5.
        assertEquals(setOf(0, 1, 2, 3, 5, 7), hidden["mid"])
        // Outer sails only lose the ropes on the shared edge.
        assertEquals(setOf(1, 2, 5), hidden["left"])
        assertEquals(setOf(0, 3, 7), hidden["right"])
    }

    @Test
    fun joinHiddenRopeIndices_sailsApart_hideNothing() {
        val project = projectOf(sail("left", 4f), sail("right", 10f))

        assertTrue(GeometryEngine.joinHiddenRopeIndices(project, listOf(preset)).isEmpty())
    }

    @Test
    fun evaluate_stackedSunSails_giveSoftWarningNotHardCollision() {
        val project = projectOf(sail("a", 6f), sail("b", 6.5f))

        val report = GeometryEngine.evaluate(project, listOf(preset))

        assertEquals(setOf("a", "b"), report.warningCollisions)
        assertTrue(report.hardCollisions.isEmpty())
        assertTrue(report.messages.any { it.startsWith("Sonnensegel überlappen") }, report.messages.toString())
    }

    @Test
    fun evaluate_joinedSunSails_noWarningAndHiddenRopesReported() {
        val project = projectOf(sail("a", 4f), sail("b", 8f))

        val report = GeometryEngine.evaluate(project, listOf(preset))

        assertTrue(report.warningCollisions.isEmpty(), report.messages.toString())
        assertTrue(report.hardCollisions.isEmpty())
        assertFalse(report.hiddenRopeIndices["a"].isNullOrEmpty())
        assertFalse(report.hiddenRopeIndices["b"].isNullOrEmpty())
    }

    private fun projectOf(vararg sails: PlacedAsset) = PlannerProject(parcel = Parcel(25f, 15f), assets = sails.toList())

    private fun sail(
        id: String,
        x: Float,
    ) = PlacedAsset(
        id = id,
        presetId = preset.id,
        position = Vec2(x, 6f),
        ropes = defaultSquareRopes(),
    )

    private fun squareSunSailPreset(): TentPreset =
        TentPreset(
            id = "sun_sail_test",
            name = "Sun sail test",
            shape = TentShapeType.POLYGON,
            widthMeters = 4f,
            heightMeters = 4f,
            polygonPoints =
                listOf(
                    Vec2(-2f, -2f),
                    Vec2(2f, -2f),
                    Vec2(2f, 2f),
                    Vec2(-2f, 2f),
                ),
            ropeAnchors = emptyList(),
        )

    private fun defaultSquareRopes(): List<AssetRope> =
        listOf(
            AssetRope(localX = -2f, localY = -2f, lengthMeters = 1.5f),
            AssetRope(localX = 2f, localY = -2f, lengthMeters = 1.5f),
            AssetRope(localX = 2f, localY = 2f, lengthMeters = 1.5f),
            AssetRope(localX = -2f, localY = 2f, lengthMeters = 1.5f),
            AssetRope(localX = 0f, localY = -2f, lengthMeters = 1.5f),
            AssetRope(localX = 2f, localY = 0f, lengthMeters = 1.5f),
            AssetRope(localX = 0f, localY = 2f, lengthMeters = 1.5f),
            AssetRope(localX = -2f, localY = 0f, lengthMeters = 1.5f),
        )
}
