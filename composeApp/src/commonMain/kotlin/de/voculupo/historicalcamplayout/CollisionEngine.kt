package de.voculupo.historicalcamplayout

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation

object CollisionEngine {
    fun isColliding(assetA: PlacedAsset, assetB: PlacedAsset, pathA: Path, pathB: Path): Boolean {
        if (!heightRangesOverlap(assetA.heightRange, assetB.heightRange)) return false
        val intersection = Path.combine(PathOperation.Intersect, pathA, pathB)
        return !intersection.isEmpty
    }

    private fun heightRangesOverlap(a: HeightRange, b: HeightRange): Boolean {
        val lower = maxOf(a.minMeters, b.minMeters)
        val upper = minOf(a.maxMeters, b.maxMeters)
        return lower < upper
    }
}

