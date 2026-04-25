package de.voculupo.historicalcamplayout.features.planner.state

import de.voculupo.historicalcamplayout.AssetRope
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.Parcel
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.Vec2

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
        defaults.forEach { preset ->
            if (preset.id !in byId) byId[preset.id] = preset
        }
        return byId.values.sortedBy { it.name }
    }

    fun normalizeDegrees(value: Float): Float {
        var d = value % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    fun hydrateProjectRopes(
        project: PlannerProject,
        presets: List<TentPreset>,
    ): PlannerProject {
        val byId = presets.associateBy { it.id }
        return project.copy(
            assets =
                project.assets.map { asset ->
                    if (asset.ropes.isNotEmpty()) {
                        asset
                    } else {
                        val preset = byId[asset.presetId]
                        val ropes =
                            preset?.ropeAnchors?.map {
                                AssetRope(
                                    localX = it.localX,
                                    localY = it.localY,
                                    lengthMeters = it.lengthMeters,
                                    outwardAngleDeg = it.outwardAngleDeg ?: 0f,
                                )
                            } ?: emptyList()
                        asset.copy(ropes = ropes)
                    }
                },
        )
    }

    fun clampAssetsToParcel(
        project: PlannerProject,
        presetsById: Map<String, TentPreset>,
    ): PlannerProject {
        return project.copy(
            assets =
                project.assets.map { asset ->
                    val preset = presetsById[asset.presetId] ?: return@map asset
                    asset.copy(position = constrainAssetCenterToParcel(asset.position, preset, project.parcel))
                },
        )
    }

    fun constrainAssetCenterToParcel(
        position: Vec2,
        preset: TentPreset,
        parcel: Parcel,
    ): Vec2 {
        val probe = PlacedAsset(id = "probe", presetId = preset.id, position = position)
        val body = GeometryEngine.assetBodyAabb(probe, preset)
        val halfW = (body.right - body.left) / 2f
        val halfH = (body.bottom - body.top) / 2f
        val minX = halfW
        val maxX = (parcel.widthMeters - halfW).coerceAtLeast(minX)
        val minY = halfH
        val maxY = (parcel.heightMeters - halfH).coerceAtLeast(minY)
        return Vec2(position.x.coerceIn(minX, maxX), position.y.coerceIn(minY, maxY))
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
            val halfW = (body.right - body.left) / 2f
            val halfH = (body.bottom - body.top) / 2f

            val localMinDx = halfW - asset.position.x
            val localMaxDx = project.parcel.widthMeters - halfW - asset.position.x
            val localMinDy = halfH - asset.position.y
            val localMaxDy = project.parcel.heightMeters - halfH - asset.position.y

            minDx = maxOf(minDx, localMinDx)
            maxDx = minOf(maxDx, localMaxDx)
            minDy = maxOf(minDy, localMinDy)
            maxDy = minOf(maxDy, localMaxDy)
        }

        return Vec2(
            x = requestedDelta.x.coerceIn(minDx, maxDx),
            y = requestedDelta.y.coerceIn(minDy, maxDy),
        )
    }
}
