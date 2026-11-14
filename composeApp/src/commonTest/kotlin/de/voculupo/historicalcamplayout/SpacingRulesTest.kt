package de.voculupo.historicalcamplayout

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpacingRulesTest {
    private val presets = defaultPresets()

    // Round 3x3 tents without margin: body is x +- 1.5 m; entries point to -y, so side by side they don't block each other.
    private fun tent(
        id: String,
        x: Float,
    ) = PlacedAsset(id = id, presetId = "round_3x3", position = Vec2(x, 10f), safetyMarginMeters = 0f, showRopes = false)

    private val sail = PlacedAsset(id = "s", presetId = "sun_sail_6x6_flat", position = Vec2(20f, 10f), safetyMarginMeters = 0f)

    private fun messages(
        assets: List<PlacedAsset>,
        rules: SpacingRules,
        zones: List<BlockZone> = emptyList(),
    ) = GeometryEngine
        .evaluate(
            PlannerProject(parcel = Parcel(40f, 30f), assets = assets, blockZones = zones, spacing = rules),
            presets,
        ).messages

    @Test
    fun tentToTent_followsTheRule() {
        val pair = listOf(tent("a", 10f), tent("b", 14f)) // 1.0 m apart
        assertTrue(messages(pair, SpacingRules()).any { "Laufweg" in it })
        assertFalse(messages(pair, SpacingRules(tentToTent = 0.8f)).any { "Laufweg" in it })
    }

    @Test
    fun tentToSail_usesItsOwnGap() {
        val near = listOf(tent("a", 14.5f), sail) // 1.0 m from the sail
        assertTrue(messages(near, SpacingRules(tentToTent = 3f, tentToSail = 1.5f)).any { "Laufweg" in it })
        assertFalse(messages(near, SpacingRules(tentToTent = 3f, tentToSail = 0.5f)).any { "Laufweg" in it })
    }

    @Test
    fun tentToSailMax_warnsWhenTooFar() {
        val far = listOf(tent("a", 5f), sail) // 10.5 m from the sail
        assertTrue(messages(far, SpacingRules(tentToSailMax = 3f)).any { "Zu weit" in it })
        assertFalse(messages(far, SpacingRules()).any { "Zu weit" in it })
    }

    @Test
    fun tentToPath_checksLanes() {
        val lane = BlockZone("auto-l", listOf(Vec2(12f, 0f), Vec2(14f, 0f), Vec2(14f, 30f), Vec2(12f, 30f)), kind = FreeAreaKind.LANE)
        val one = listOf(tent("a", 10f)) // 0.5 m from the lane
        assertTrue(messages(one, SpacingRules(), listOf(lane)).any { "Weg" in it })
        assertFalse(messages(one, SpacingRules(tentToPath = 0.3f), listOf(lane)).any { "Abstand zum Weg" in it })
        // A zone that is not a lane has no path gap.
        assertFalse(messages(one, SpacingRules(), listOf(lane.copy(kind = null))).any { "Abstand zum Weg" in it })
    }

    @Test
    fun tentTypeOverride_replacesTheCampValue() {
        val pair = listOf(tent("a", 10f), tent("b", 15f)) // 2.0 m apart

        fun withOwn(gap: Float?) =
            GeometryEngine
                .evaluate(
                    PlannerProject(parcel = Parcel(40f, 30f), assets = pair),
                    presets.map { if (it.id == "round_3x3") it.copy(spacing = SpacingOverride(toTents = gap)) else it },
                ).messages
                .any { "Laufweg" in it }
        assertFalse(withOwn(null), "camp default 1.5 m is met")
        assertTrue(withOwn(3f), "the tent type wants 3 m")
    }

    @Test
    fun capped_alsoCapsOverrides() {
        val wide = presets.first { it.id == "round_3x3" }.copy(spacing = SpacingOverride(toTents = 3f, toPath = 4f))
        val rules = SpacingRules().capped(1f)
        assertTrue(rules.between(wide, wide) == 1f)
        assertTrue(rules.toPath(wide) == 1f)
        assertTrue(SpacingRules().between(wide, wide) == 3f)
    }
}
