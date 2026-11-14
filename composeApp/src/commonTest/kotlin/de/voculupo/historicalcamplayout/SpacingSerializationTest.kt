package de.voculupo.historicalcamplayout

import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Saved projects and inventories from before the spacing rules must still load, and new fields must survive a round trip. */
class SpacingSerializationTest {
    private val json = PlannerJson.json

    @Test
    fun projectFromBeforeSpacingRules_loadsWithDefaults() {
        // Shape of a project saved by the previous version: walkwayMeters on the auto setup, no spacing.
        val old =
            """
            {"name":"Alt","parcel":{"widthMeters":20.0,"heightMeters":12.0},
             "assets":[{"id":"a","presetId":"round_4","position":{"x":5.0,"y":5.0}}],
             "autoSetup":{"parcel":{"widthMeters":20.0,"heightMeters":12.0},"tentPresetIds":["round_4"],
                          "sailPresetIds":[],"visitorSide":"TOP","walkwayMeters":2.0}}
            """.trimIndent()
        val project = json.decodeFromString(PlannerProject.serializer(), old)
        assertEquals(SpacingRules(), project.spacing)
        assertEquals(SpacingRules(), project.autoSetup!!.spacing)
        assertEquals(1, project.assets.size)
    }

    @Test
    fun spacingAndOverrides_roundTrip() {
        val rules = SpacingRules(tentToTent = 2f, tentToSail = 0.5f, tentToSailMax = 4f, tentToPath = 1f, sailToPath = 3f)
        val project =
            PlannerProject(
                spacing = rules,
                autoSetup = AutoSetupRequest(Parcel(), listOf("round_4"), emptyList(), VisitorSide.TOP, spacing = rules),
            )
        assertEquals(project, json.decodeFromString(PlannerProject.serializer(), project.toJson()))

        val type = defaultPresets().first().copy(spacing = SpacingOverride(toTents = 3f, toPath = 0.5f), userModified = true)
        val list = ListSerializer(TentPreset.serializer())
        assertEquals(listOf(type), json.decodeFromString(list, json.encodeToString(list, listOf(type))))
    }

    @Test
    fun squeezeCap_isNeverSaved() {
        val encoded = json.encodeToString(SpacingRules.serializer(), SpacingRules().capped(0.5f))
        assertFalse("cap" in encoded, encoded)
        assertEquals(Float.MAX_VALUE, json.decodeFromString(SpacingRules.serializer(), encoded).cap)
    }

    @Test
    fun inventoryFromBeforeOverrides_loads() {
        val old = """[{"id":"x","name":"Mein Zelt","shape":"RECTANGLE","widthMeters":3.0,"heightMeters":2.0,"userModified":true}]"""
        val types = json.decodeFromString(ListSerializer(TentPreset.serializer()), old)
        assertEquals(SpacingOverride(), types.single().spacing)
        assertTrue(types.single().userModified)
    }
}
