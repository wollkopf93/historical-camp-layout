package de.voculupo.historicalcamplayout.features.planner.state

import de.voculupo.historicalcamplayout.AssetRope
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.Parcel
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.Vec2
import kotlin.math.PI
import kotlin.math.tan

internal object PlannerDomainOps {
    fun rememberRecentPreset(
        current: List<String>,
        id: String,
    ): List<String> = (listOf(id) + current.filterNot { it == id }).take(8)

    fun mergePresets(
        stored: List<TentPreset>?,
        defaults: List<TentPreset>,
    ): List<TentPreset> {
        if (stored == null) return defaults
        val byId = stored.associateBy { it.id }.toMutableMap()
        // Built-in presets win over stale stored copies so fixes reach existing installs;
        // only presets explicitly marked as user-edited keep their stored version.
        defaults.forEach { preset ->
            if (byId[preset.id]?.userModified != true) byId[preset.id] = preset
        }
        return byId.values.sortedBy { it.name }
    }

    fun defaultRopes(preset: TentPreset): List<AssetRope> =
        preset.ropeAnchors.map { anchor ->
            AssetRope(
                localX = anchor.localX,
                localY = anchor.localY,
                lengthMeters = anchor.lengthMeters,
                outwardAngleDeg = anchor.outwardAngleDeg ?: 0f,
            )
        }

    /** The tent type at a new size: outline and rope anchors are stretched along with it. */
    fun resized(
        preset: TentPreset,
        widthMeters: Float,
        heightMeters: Float,
    ): TentPreset {
        if (widthMeters == preset.widthMeters && heightMeters == preset.heightMeters) return preset
        val sx = widthMeters / preset.widthMeters
        val sy = heightMeters / preset.heightMeters
        return preset.copy(
            widthMeters = widthMeters,
            heightMeters = heightMeters,
            polygonPoints = preset.polygonPoints.map { Vec2(it.x * sx, it.y * sy) },
            ropeAnchors = preset.ropeAnchors.map { it.copy(localX = it.localX * sx, localY = it.localY * sy) },
        )
    }

    /**
     * How far from the attachment point the peg goes (seen from above) for a rope tied [heightMeters] up
     * that should meet the ground at [slopeDeg] (45 = as far out as high; flatter = farther).
     */
    fun stakeDistance(
        heightMeters: Float,
        slopeDeg: Float,
    ): Float = heightMeters / tan(slopeDeg * PI.toFloat() / 180f)

    fun normalizeDegrees(value: Float): Float {
        var d = value % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    fun clampAssetsToParcel(
        project: PlannerProject,
        presetsById: Map<String, TentPreset>,
    ): PlannerProject {
        return project.copy(
            assets =
                project.assets.map { asset ->
                    val preset = presetsById[asset.presetId] ?: return@map asset
                    asset.copy(position = constrainAssetCenterToParcel(asset.position, preset, project.parcel, asset.rotationDeg))
                },
        )
    }

    fun constrainAssetCenterToParcel(
        position: Vec2,
        preset: TentPreset,
        parcel: Parcel,
        rotationDeg: Float = 0f,
    ): Vec2 {
        val probe = PlacedAsset(id = "probe", presetId = preset.id, position = position, rotationDeg = rotationDeg)
        val body = GeometryEngine.assetBodyAabb(probe, preset)
        return Vec2(
            position.x + shiftInside(body.left, body.right, parcel.widthMeters),
            position.y + shiftInside(body.top, body.bottom, parcel.heightMeters),
        )
    }

    /** Shift that moves the span [low, high] into [0, size]; a span larger than size is aligned to 0. */
    private fun shiftInside(
        low: Float,
        high: Float,
        size: Float,
    ): Float =
        when {
            low < 0f -> -low
            high > size -> (size - high).coerceAtLeast(-low)
            else -> 0f
        }

    fun clampDeltaForAssetsInParcel(
        project: PlannerProject,
        presetsById: Map<String, TentPreset>,
        assetIds: Set<String>,
        requestedDelta: Vec2,
    ): Vec2 {
        if (assetIds.isEmpty()) return requestedDelta
        var minDx = Float.NEGATIVE_INFINITY
        var maxDx = Float.POSITIVE_INFINITY
        var minDy = Float.NEGATIVE_INFINITY
        var maxDy = Float.POSITIVE_INFINITY

        project.assets.forEach { asset ->
            if (asset.id !in assetIds) return@forEach
            val preset = presetsById[asset.presetId] ?: return@forEach
            val body = GeometryEngine.assetBodyAabb(asset, preset)
            minDx = maxOf(minDx, 0f - body.left)
            maxDx = minOf(maxDx, project.parcel.widthMeters - body.right)
            minDy = maxOf(minDy, 0f - body.top)
            maxDy = minOf(maxDy, project.parcel.heightMeters - body.bottom)
        }

        // min > max when the moved set is larger than the parcel; coerceIn would throw, so align to the top-left.
        return Vec2(
            x = if (minDx > maxDx) minDx else requestedDelta.x.coerceIn(minDx, maxDx),
            y = if (minDy > maxDy) minDy else requestedDelta.y.coerceIn(minDy, maxDy),
        )
    }
}
