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
}
