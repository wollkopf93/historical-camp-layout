package de.voculupo.historicalcamplayout

import kotlin.math.max
import kotlin.math.min

/** Open areas keep this distance even when the auto setup squeezes tents to cloth to cloth. */
const val MIN_ZONE_CLEAR = 1.0f

/** Gap wanted between two assets: tent to sail, else tent to tent (also sail to sail). */
fun SpacingRules.between(
    a: TentPreset,
    b: TentPreset,
): Float = if (GeometryEngine.isSunSailPreset(a) != GeometryEngine.isSunSailPreset(b)) tentToSail else tentToTent

/** Gap wanted between an asset and a path (lane). */
fun SpacingRules.toPath(preset: TentPreset): Float = if (GeometryEngine.isSunSailPreset(preset)) sailToPath else tentToPath

/**
 * The rules squeezed to a walkway of at most [cap] (auto setup when space runs out). Paths keep at
 * least [MIN_ZONE_CLEAR] unless the user asked for less.
 */
fun SpacingRules.capped(cap: Float): SpacingRules {
    fun path(v: Float) = min(v, max(cap, MIN_ZONE_CLEAR))
    return copy(
        tentToTent = min(tentToTent, cap),
        tentToSail = min(tentToSail, cap),
        tentToPath = path(tentToPath),
        sailToPath = path(sailToPath),
    )
}
