package de.voculupo.historicalcamplayout.app

import de.voculupo.historicalcamplayout.PlannerToolMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUiLogicTest {

    @Test
    fun isPhoneLayout_usesCompactBreakpoint() {
        assertTrue(AppUiLogic.isPhoneLayout(320f))
        assertTrue(AppUiLogic.isPhoneLayout(599.99f))
        assertFalse(AppUiLogic.isPhoneLayout(600f))
    }

    @Test
    fun compactPresetSelection_switchesToPlaceTentTool() {
        assertEquals(PlannerToolMode.PlaceTent, AppUiLogic.toolForCompactPresetSelection())
    }

    @Test
    fun autoOpenProperties_onlyWhenSelectionExists() {
        assertFalse(AppUiLogic.shouldAutoOpenProperties(emptySet()))
        assertTrue(AppUiLogic.shouldAutoOpenProperties(setOf("asset-1")))
    }
}

