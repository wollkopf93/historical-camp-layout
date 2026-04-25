package de.voculupo.historicalcamplayout.app

import de.voculupo.historicalcamplayout.PlannerToolMode

internal object AppUiLogic {
    const val PHONE_BREAKPOINT_DP = 600f

    fun isPhoneLayout(widthDp: Float): Boolean = widthDp < PHONE_BREAKPOINT_DP

    fun shouldUseCompactShell(isAndroid: Boolean): Boolean {
        return isAndroid
    }

    fun toolForCompactPresetSelection(): PlannerToolMode = PlannerToolMode.PlaceTent

    fun shouldAutoOpenProperties(selectedAssetIds: Set<String>): Boolean = selectedAssetIds.isNotEmpty()
}

