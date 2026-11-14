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

    private fun preset(id: String) = presets.first { it.id == id }

    private fun withTypes(vararg types: TentPreset) = presets.map { p -> types.firstOrNull { it.id == p.id } ?: p }

    @Test
    fun defaults_keepTheOldOnePointFiveMetreWarning() {
        // Exactly 1.5 m counted as "under 1.5 m" before; 1.6 m did not.
        assertTrue(messages(listOf(tent("a", 10f), tent("b", 14.5f)), SpacingRules()).any { "Laufweg" in it })
        assertFalse(messages(listOf(tent("a", 10f), tent("b", 14.6f)), SpacingRules()).any { "Laufweg" in it })
        assertTrue(SpacingRules() == PlannerProject().spacing)
    }

    @Test
    fun twoSails_useTheTentToTentGap() {
        val sails = listOf(sail.copy(id = "s1", position = Vec2(10f, 10f)), sail.copy(id = "s2", position = Vec2(17f, 10f))) // 1.0 m
        assertTrue(messages(sails, SpacingRules(tentToTent = 1.5f, tentToSail = 0.5f)).any { "Laufweg" in it })
        assertFalse(messages(sails, SpacingRules(tentToTent = 0.5f, tentToSail = 3f)).any { "Laufweg" in it })
    }

    @Test
    fun between_isSymmetric_andPicksTheRightRule() {
        val t = preset("round_3x3")
        val s = preset("sun_sail_6x6_flat")
        val rules = SpacingRules(tentToTent = 1f, tentToSail = 2f, tentToPath = 3f, sailToPath = 4f)
        assertTrue(rules.between(t, t) == 1f)
        assertTrue(rules.between(t, s) == 2f && rules.between(s, t) == 2f)
        assertTrue(rules.between(s, s) == 1f)
        assertTrue(rules.toPath(t) == 3f)
        assertTrue(rules.toPath(s) == 4f)
    }

    @Test
    fun overrides_replaceInBothDirections_largerWins_smallerAlsoApplies() {
        val t = preset("round_3x3")
        val s = preset("sun_sail_6x6_flat")
        val rules = SpacingRules()
        // Smaller than the camp's value also replaces it ("dominant").
        assertTrue(rules.between(t.copy(spacing = SpacingOverride(toTents = 0.5f)), t) == 0.5f)
        // Both set: the larger is kept so both are satisfied.
        assertTrue(rules.between(t.copy(spacing = SpacingOverride(toTents = 0.5f)), t.copy(spacing = SpacingOverride(toTents = 2f))) == 2f)
        // Tent to sail: the tent's toSails or the sail's toTents.
        assertTrue(rules.between(t.copy(spacing = SpacingOverride(toSails = 4f)), s) == 4f)
        assertTrue(rules.between(t, s.copy(spacing = SpacingOverride(toTents = 3f))) == 3f)
        // An override for the other kind does not leak.
        assertTrue(rules.between(t.copy(spacing = SpacingOverride(toSails = 4f)), t) == rules.tentToTent)
        assertTrue(rules.toPath(t.copy(spacing = SpacingOverride(toPath = 0.2f))) == 0.2f)
    }

    @Test
    fun overrideOnSail_changesTheWarning() {
        val near = listOf(tent("a", 14.5f), sail) // 1.0 m from the sail
        val ownSail = preset("sun_sail_6x6_flat").copy(spacing = SpacingOverride(toTents = 0.5f))
        val report = GeometryEngine.evaluate(PlannerProject(parcel = Parcel(40f, 30f), assets = near), withTypes(ownSail))
        assertFalse(report.messages.any { "Laufweg" in it }, report.messages.toString())
    }

    @Test
    fun capped_keepsPathsAtOneMetre_unlessAskedForLess() {
        val t = preset("round_3x3")
        assertTrue(SpacingRules().capped(0f).toPath(t) == MIN_ZONE_CLEAR)
        assertTrue(SpacingRules(tentToPath = 0.5f).capped(0f).toPath(t) == 0.5f)
        assertTrue(SpacingRules().capped(0f).between(t, t) == 0f)
        assertTrue(SpacingRules(tentToSail = 0.4f).capped(1f).tentToSail == 0.4f, "a cap never raises a gap")
        assertTrue(SpacingRules().capped(1f).tentToSailMax == null)
    }

    @Test
    fun tentToSailMax_withoutSails_isQuiet_andSailsAreNotChecked() {
        assertFalse(messages(listOf(tent("a", 5f)), SpacingRules(tentToSailMax = 0.1f)).any { "Zu weit" in it })
        val twoSails = listOf(sail, sail.copy(id = "s2", position = Vec2(35f, 10f)))
        assertFalse(messages(twoSails, SpacingRules(tentToSailMax = 0.1f)).any { "Zu weit" in it })
    }

    @Test
    fun tentToSailMax_usesTheNearestSail() {
        val sails = listOf(sail, sail.copy(id = "s2", position = Vec2(2f, 10f))) // second sail body -1..5
        // Tent at 7.5: 1.0 m from the second sail, far from the first.
        assertFalse(messages(listOf(tent("a", 7.5f)) + sails, SpacingRules(tentToSailMax = 2f)).any { "Zu weit" in it })
    }

    @Test
    fun pathWarning_forSails_usesSailToPath() {
        val lane = BlockZone("auto-l", listOf(Vec2(24f, 0f), Vec2(26f, 0f), Vec2(26f, 30f), Vec2(24f, 30f)), kind = FreeAreaKind.LANE)
        val one = listOf(sail) // sail body ends at 23: 1.0 m from the lane
        assertTrue(messages(one, SpacingRules(sailToPath = 1.5f, tentToPath = 0f), listOf(lane)).any { "Abstand zum Weg" in it })
        assertFalse(messages(one, SpacingRules(sailToPath = 0.5f, tentToPath = 5f), listOf(lane)).any { "Abstand zum Weg" in it })
    }

    @Test
    fun tentInsideLane_isAZoneProblemNotAPathGap() {
        val lane = BlockZone("auto-l", listOf(Vec2(9f, 0f), Vec2(11f, 0f), Vec2(11f, 30f), Vec2(9f, 30f)), kind = FreeAreaKind.LANE)
        val msgs = messages(listOf(tent("a", 10f)), SpacingRules(), listOf(lane))
        assertTrue(msgs.any { "Sperrzone" in it })
        assertFalse(msgs.any { "Abstand zum Weg" in it })
    }
}
