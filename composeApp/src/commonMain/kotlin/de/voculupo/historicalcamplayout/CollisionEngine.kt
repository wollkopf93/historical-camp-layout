package de.voculupo.historicalcamplayout

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation

object CollisionEngine {
    /** 2D footprint overlap with real shared area; edges that only touch do not count. */
    fun pathsIntersect(
        pathA: Path,
        pathB: Path,
    ): Boolean = !Path.combine(PathOperation.Intersect, pathA, pathB).isEmpty
}
