package de.voculupo.historicalcamplayout.features.planner.state

import de.voculupo.historicalcamplayout.Parcel
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.TentShapeType
import de.voculupo.historicalcamplayout.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals

class PlannerDomainOpsTest {
    @Test
    fun rememberRecentPreset_movesIdToFront_andLimitsTo8() {
        val current = listOf("a", "b", "c", "d", "e", "f", "g", "h")
        val updated = PlannerDomainOps.rememberRecentPreset(current, "c")
        assertEquals(listOf("c", "a", "b", "d", "e", "f", "g", "h"), updated)

        val withNew = PlannerDomainOps.rememberRecentPreset(current, "x")
        assertEquals(8, withNew.size)
        assertEquals("x", withNew.first())
    }

    @Test
    fun normalizeDegrees_wrapsIntoMinus180To180() {
        assertEquals(-170f, PlannerDomainOps.normalizeDegrees(190f))
        assertEquals(170f, PlannerDomainOps.normalizeDegrees(-190f))
        assertEquals(0f, PlannerDomainOps.normalizeDegrees(360f))
    }

    @Test
    fun constrainAssetCenterToParcel_clampsWithinBounds() {
        val preset = circlePreset(id = "circle", diameter = 2f)
        val parcel = Parcel(widthMeters = 10f, heightMeters = 8f)

        val clampedLeftTop =
            PlannerDomainOps.constrainAssetCenterToParcel(
                position = Vec2(0f, 0f),
                preset = preset,
                parcel = parcel,
            )
        assertEquals(1f, clampedLeftTop.x)
        assertEquals(1f, clampedLeftTop.y)

        val clampedRightBottom =
            PlannerDomainOps.constrainAssetCenterToParcel(
                position = Vec2(10f, 8f),
                preset = preset,
                parcel = parcel,
            )
        assertEquals(9f, clampedRightBottom.x)
        assertEquals(7f, clampedRightBottom.y)
    }

    @Test
    fun clampDeltaForAssetsInParcel_limitsMotionToParcel() {
        val preset = circlePreset(id = "circle", diameter = 2f)
        val project =
            PlannerProject(
                parcel = Parcel(widthMeters = 10f, heightMeters = 8f),
                assets =
                    listOf(
                        PlacedAsset(
                            id = "asset-1",
                            presetId = preset.id,
                            position = Vec2(1f, 1f),
                        ),
                    ),
            )

        val clamped =
            PlannerDomainOps.clampDeltaForAssetsInParcel(
                project = project,
                presetsById = mapOf(preset.id to preset),
                assetIds = setOf("asset-1"),
                requestedDelta = Vec2(-5f, -3f),
            )

        assertEquals(0f, clamped.x)
        assertEquals(0f, clamped.y)
    }

    private fun circlePreset(
        id: String,
        diameter: Float,
    ): TentPreset =
        TentPreset(
            id = id,
            name = id,
            shape = TentShapeType.CIRCLE,
            widthMeters = diameter,
            heightMeters = diameter,
        )

    @Test
    fun resized_stretchesOutlineAndRopeAnchors() {
        val saxon =
            de.voculupo.historicalcamplayout
                .defaultPresets()
                .first { it.id == "saxon_2x4" }
        val big = PlannerDomainOps.resized(saxon, 8f, 6f) // twice as wide and deep
        assertEquals(saxon.polygonPoints.map { Vec2(it.x * 2f, it.y * 2f) }, big.polygonPoints)
        assertEquals(saxon.ropeAnchors.map { it.localX * 2f to it.localY * 2f }, big.ropeAnchors.map { it.localX to it.localY })
        assertEquals(saxon.ropeAnchors.map { it.lengthMeters }, big.ropeAnchors.map { it.lengthMeters }, "peg distances stay")
    }

    @Test
    fun stakeDistance_followsTheSlope() {
        assertEquals(2f, PlannerDomainOps.stakeDistance(2f, 45f), 0.001f)
        assertEquals(3.464f, PlannerDomainOps.stakeDistance(2f, 30f), 0.001f) // flatter rope, peg farther out
    }
}
