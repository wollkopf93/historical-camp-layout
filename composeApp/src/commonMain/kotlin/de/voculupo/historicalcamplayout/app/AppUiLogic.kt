package de.voculupo.historicalcamplayout.app

import de.voculupo.historicalcamplayout.PlannerToolMode

internal object AppUiLogic {
    const val PHONE_BREAKPOINT_DP = 600f

    /** Material "expanded" width: enough room for inventory, canvas and properties side by side. */
    const val EXPANDED_BREAKPOINT_DP = 840f

    fun isPhoneLayout(widthDp: Float): Boolean = widthDp < PHONE_BREAKPOINT_DP

    fun shouldUseCompactShell(
        isAndroid: Boolean,
        widthDp: Float,
    ): Boolean = isAndroid && widthDp < EXPANDED_BREAKPOINT_DP

    fun toolForCompactPresetSelection(): PlannerToolMode = PlannerToolMode.PlaceTent

    fun shouldAutoOpenProperties(selectedAssetIds: Set<String>): Boolean = selectedAssetIds.isNotEmpty()
}
