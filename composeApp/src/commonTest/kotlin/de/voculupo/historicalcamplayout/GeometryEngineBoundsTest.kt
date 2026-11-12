package de.voculupo.historicalcamplayout

import de.voculupo.historicalcamplayout.features.planner.state.PlannerDomainOps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryEngineBoundsTest {
    private val presets = defaultPresets()

    private fun preset(id: String) = presets.first { it.id == id }

    @Test
    fun evaluate_findsCollision_ofRotatedTent() {
        // Capsule 6x4 rotated 90° is 6 m tall; the round tent overlaps its bottom by 0.9 m.
        val capsule = PlacedAsset(id = "a", presetId = "oval_6x4", position = Vec2(10f, 10f), rotationDeg = 90f, showRopes = false)
        val round = PlacedAsset(id = "b", presetId = "round_3x3", position = Vec2(10f, 13.6f), showRopes = false)
        val project = PlannerProject(parcel = Parcel(30f, 30f), assets = listOf(capsule, round))

        val report = GeometryEngine.evaluate(project, presets)

        assertEquals(setOf("a", "b"), report.hardCollisions)
    }

    @Test
    fun constrainToParcel_usesRotatedFootprint() {
        // Sun sail 6x2 rotated 90° reaches 3 m above its center, so it must sit at y >= 3.
        val clamped =
            PlannerDomainOps.constrainAssetCenterToParcel(
                position = Vec2(5f, 0f),
                preset = preset("sun_sail_6x2_flat"),
                parcel = Parcel(20f, 20f),
                rotationDeg = 90f,
            )
        assertEquals(3f, clamped.y, absoluteTolerance = 0.001f)
    }

    @Test
    fun ropeSegments_staysEmpty_whenAllRopesRemoved() {
        val asset = PlacedAsset(id = "a", presetId = "round_4", position = Vec2(5f, 5f), ropes = emptyList())
        assertTrue(GeometryEngine.ropeSegments(asset).isEmpty())
    }

    @Test
    fun pngExport_scalesLargePlanToFit() {
        val project = PlannerProject(parcel = Parcel(100f, 100f), pixelsPerMeter = 180f)
        val scene = buildPngExportScene(project, presets)
        assertTrue(scene.widthPx <= 6000 && scene.heightPx <= 6000)
        assertTrue(scene.parcel.right <= scene.widthPx && scene.parcel.bottom <= scene.heightPx)
    }
}
