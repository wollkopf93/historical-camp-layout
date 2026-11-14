package de.voculupo.historicalcamplayout

import kotlin.math.max
import kotlin.math.min

/** Open areas keep this distance even when the auto setup squeezes tents to cloth to cloth. */
const val MIN_ZONE_CLEAR = 1.0f

private fun SpacingOverride.toward(otherIsSail: Boolean) = if (otherIsSail) toSails else toTents

/**
 * Gap wanted between two assets. A tent type's own value replaces the camp's (the larger one wins when
 * both have one); otherwise tent to sail, else tent to tent (also sail to sail).
 */
fun SpacingRules.between(
    a: TentPreset,
    b: TentPreset,
): Float {
    val sailA = GeometryEngine.isSunSailPreset(a)
    val sailB = GeometryEngine.isSunSailPreset(b)
    val own = listOfNotNull(a.spacing.toward(sailB), b.spacing.toward(sailA)).maxOrNull()
    return min(own ?: if (sailA != sailB) tentToSail else tentToTent, cap)
}

/** Gap wanted between an asset and a path (lane). */
fun SpacingRules.toPath(preset: TentPreset): Float {
    val wanted = preset.spacing.toPath ?: if (GeometryEngine.isSunSailPreset(preset)) sailToPath else tentToPath
    return min(wanted, max(cap, MIN_ZONE_CLEAR))
}

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
        cap = cap,
    )
}
