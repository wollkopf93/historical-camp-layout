package de.voculupo.historicalcamplayout

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object GeometryHelper {
    fun generatePoints(shape: TentShapeType, widthMeters: Float, depthMeters: Float): List<Vec2> {
        val halfW = widthMeters / 2f
        val halfD = depthMeters / 2f
        return when (shape) {
            TentShapeType.RECTANGLE, TentShapeType.A_FRAME -> listOf(
                Vec2(-halfW, -halfD),
                Vec2(halfW, -halfD),
                Vec2(halfW, halfD),
                Vec2(-halfW, halfD),
            )

            TentShapeType.CIRCLE -> {
                val radius = (widthMeters.coerceAtMost(depthMeters)) / 2f
                (0 until 16).map { i ->
                    val angle = (2f * PI.toFloat() * i) / 16f
                    Vec2(cos(angle) * radius, sin(angle) * radius)
                }
            }

            TentShapeType.CAPSULE -> {
                val radius = depthMeters / 2f
                val straightHalf = (widthMeters - depthMeters).coerceAtLeast(0f) / 2f
                val stepsPerSemi = 8
                val left = (0 until stepsPerSemi).map { i ->
                    val t = i.toFloat() / (stepsPerSemi - 1).toFloat()
                    val angle = (PI / 2.0) + t * PI
                    Vec2(
                        x = -straightHalf + (cos(angle).toFloat() * radius),
                        y = sin(angle).toFloat() * radius,
                    )
                }
                val right = (0 until stepsPerSemi).map { i ->
                    val t = i.toFloat() / (stepsPerSemi - 1).toFloat()
                    val angle = (-PI / 2.0) + t * PI
                    Vec2(
                        x = straightHalf + (cos(angle).toFloat() * radius),
                        y = sin(angle).toFloat() * radius,
                    )
                }
                left + right
            }

            TentShapeType.POLYGON, TentShapeType.SAXON -> emptyList()
        }
    }
}

