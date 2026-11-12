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

    @Test
    fun compactShell_onlyOnAndroidBelowExpandedWidth() {
        assertTrue(AppUiLogic.shouldUseCompactShell(isAndroid = true, widthDp = 411f))
        assertTrue(AppUiLogic.shouldUseCompactShell(isAndroid = true, widthDp = 839f))
        assertFalse(AppUiLogic.shouldUseCompactShell(isAndroid = true, widthDp = 1280f))
        assertFalse(AppUiLogic.shouldUseCompactShell(isAndroid = false, widthDp = 411f))
    }
}
