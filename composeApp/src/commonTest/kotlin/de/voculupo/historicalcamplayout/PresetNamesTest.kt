package de.voculupo.historicalcamplayout

import kotlin.test.Test
import kotlin.test.assertEquals

class PresetNamesTest {
    private val saxon = defaultPresets().first { it.id == "saxon_4x6" }
    private val tent = PlacedAsset(id = "a", presetId = saxon.id, position = Vec2(5f, 5f))

    @Test
    fun storedDefaultLabel_followsAppLanguage() {
        assertEquals("Saxon tent 4x6", assetDisplayLabel(tent.copy(label = saxon.name), "Saxon tent 4x6"))
        assertEquals("Saxon tent 4x6", assetDisplayLabel(tent.copy(label = ""), "Saxon tent 4x6"))
    }

    @Test
    fun typedLabel_staysAsTyped() {
        assertEquals("Küche", assetDisplayLabel(tent.copy(label = "Küche"), "Saxon tent 4x6"))
    }
}
