package de.voculupo.historicalcamplayout.domain.model

import de.voculupo.historicalcamplayout.CollisionIssue
import de.voculupo.historicalcamplayout.GeometryHelper
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Serializable
enum class TentShapeType {
    RECTANGLE,
    CIRCLE,
    CAPSULE,
    POLYGON,
    SAXON,
    A_FRAME,
}

@Serializable
enum class PlannerToolMode {
    Select,
    PlaceTent,
    Measure,
    BlockZone,
}

@Serializable
data class Vec2(
    val x: Float,
    val y: Float,
)

@Serializable
data class Parcel(
    val widthMeters: Float = 25f,
    val heightMeters: Float = 15f,
)

@Serializable
data class RopeAnchor(
    val localX: Float,
    val localY: Float,
    val lengthMeters: Float,
    val outwardAngleDeg: Float? = null,
    val minAngleDeg: Float = -20f,
    val maxAngleDeg: Float = 20f,
)

@Serializable
data class AssetRope(
    val localX: Float,
    val localY: Float,
    val lengthMeters: Float,
    val outwardAngleDeg: Float = 0f,
)

@Serializable
data class HeightRange(
    val minMeters: Float = 0f,
    val maxMeters: Float = 2f,
)

@Serializable
data class TentPreset(
    val id: String,
    val name: String,
    val shape: TentShapeType,
    val widthMeters: Float,
    val heightMeters: Float,
    val polygonPoints: List<Vec2> = emptyList(),
    val ropeAnchors: List<RopeAnchor> = emptyList(),
    val defaultSafetyMarginMeters: Float = 1f,
    val defaultHeightRange: HeightRange = HeightRange(),
    /** Lets custom presets take part in sun-sail edge joining (built-in sails are detected by id). */
    val isSunSail: Boolean = false,
    /** Set when the user edited this preset, so newer built-in defaults with the same id don't overwrite it. */
    val userModified: Boolean = false,
)

@Serializable
data class PlacedAsset(
    val id: String,
    val presetId: String,
    val label: String = "",
    val position: Vec2,
    val rotationDeg: Float = 0f,
    val groupId: String? = null,
    val safetyMarginMeters: Float = 1f,
    val heightRange: HeightRange = HeightRange(),
    val showRopes: Boolean = true,
    val ropes: List<AssetRope> = emptyList(),
    val isLocked: Boolean = false,
)

@Serializable
data class BlockZone(
    val id: String,
    val points: List<Vec2>,
    val label: String = "",
    /** Set for zones the auto setup made from a free area; FIRE zones are drawn as a fire pit. */
    val kind: FreeAreaKind? = null,
)

@Serializable
enum class VisitorSide { TOP, RIGHT, BOTTOM, LEFT }

@Serializable
enum class RingShape(
    val arcDeg: Float,
) {
    HORSESHOE(240f),
    FULL_CIRCLE(360f),
    HALF_CIRCLE(180f),

    /** Straight back row plus two side rows, square to the parcel: how most groups actually pitch. */
    U_SHAPE(0f),

    /** Tents with their backs to the back and side edges of the parcel: the largest open area stays free. */
    EDGE(0f),

    /** Tents spread over the whole parcel, each as far as possible from everything else. */
    SPREAD(0f),
}

@Serializable
enum class FreeAreaKind { ARENA, FIRE, LANE, CUSTOM }

/** Space the auto setup keeps free. FIRE: [widthMeters] is the diameter; LANE: the width (runs the whole depth). */
@Serializable
data class FreeArea(
    val kind: FreeAreaKind,
    val label: String = "",
    val widthMeters: Float,
    val depthMeters: Float = widthMeters,
    /** Centre the user dragged it to in the preview; null = placed automatically. */
    val position: Vec2? = null,
)

/** Everything the auto setup sheet asks for; stored on the project so it can be reopened and edited. */
@Serializable
data class AutoSetupRequest(
    val parcel: Parcel,
    /** In priority order: the first tent gets the back-centre spot; the last ones are dropped first. */
    val tentPresetIds: List<String>,
    val sailPresetIds: List<String>,
    val visitorSide: VisitorSide,
    val shape: RingShape = RingShape.HORSESHOE,
    val joinSails: Boolean = false,
    val replaceExisting: Boolean = true,
    val freeAreas: List<FreeArea> = emptyList(),
    /** Per sail ring: centre the user dragged it to; null = automatic. */
    val clusterPins: List<Vec2?> = emptyList(),
    /** Parallel to [tentPresetIds]: ring index the tent must go to; null = automatic. */
    val tentRings: List<Int?> = emptyList(),
    val walkwayMeters: Float = 1.5f,
    /** Fine-tune the rule-based draft with an optimizer (spacing, doors, staying near the sail). */
    val optimize: Boolean = true,
)

@Serializable
data class PlannerProject(
    val name: String = "Neues Lager",
    val parcel: Parcel = Parcel(),
    val assets: List<PlacedAsset> = emptyList(),
    val blockZones: List<BlockZone> = emptyList(),
    val pixelsPerMeter: Float = 50f,
    val gridMeters: Float = 0.5f,
    val autoSetup: AutoSetupRequest? = null,
)

data class PlannerUiState(
    val project: PlannerProject = PlannerProject(),
    val presets: List<TentPreset> = defaultPresets(),
    val selectedAssetIds: Set<String> = emptySet(),
    val selectedRopeIndex: Int? = null,
    val currentTool: PlannerToolMode = PlannerToolMode.Select,
    val activePresetId: String? = null,
    val recentPresetIds: List<String> = emptyList(),
    val warnings: List<CollisionIssue> = emptyList(),
    val warningAssetIds: Set<String> = emptySet(),
    val hardCollisionAssetIds: Set<String> = emptySet(),
    val hiddenRopeIndices: Map<String, Set<Int>> = emptyMap(),
    val measurementStart: Vec2? = null,
    val measurementEnd: Vec2? = null,
    val pendingBlockZoneStart: Vec2? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

/**
 * Saxon tent footprint: straight sides under the ridge, rounded (half-elliptic) ends where the end gores
 * come down, and an optional porch sticking out at the front (-y, the entry side).
 */
private fun saxonOutline(
    ridgeHalf: Float,
    halfDepth: Float,
    halfLength: Float,
    porchHalfWidth: Float = 0f,
    porchDepth: Float = 0f,
): List<Vec2> {
    val steps = 6
    val rx = halfLength - ridgeHalf

    fun arc(
        sign: Float,
        from: Float,
        to: Float,
    ) = (1 until steps).map { i ->
        val a = from + (to - from) * i / steps
        Vec2(sign * (ridgeHalf + rx * cos(a)), halfDepth * sin(a))
    }
    val quarter = (PI / 2).toFloat()
    val porch =
        if (porchDepth > 0f) {
            listOf(
                Vec2(porchHalfWidth, -halfDepth),
                Vec2(porchHalfWidth, -halfDepth - porchDepth),
                Vec2(-porchHalfWidth, -halfDepth - porchDepth),
                Vec2(-porchHalfWidth, -halfDepth),
            )
        } else {
            emptyList()
        }
    return listOf(Vec2(-ridgeHalf, halfDepth), Vec2(ridgeHalf, halfDepth)) +
        arc(1f, quarter, -quarter) +
        Vec2(ridgeHalf, -halfDepth) +
        porch +
        Vec2(-ridgeHalf, -halfDepth) +
        arc(-1f, -quarter, quarter)
}

fun defaultPresets(): List<TentPreset> =
    listOf(
        // ==========================================
        // SACHSENZELTE (Masten-Ankerpunkte innen, Seile verlängert)
        // ==========================================
        TentPreset(
            id = "saxon_2x4",
            name = "Sachsenzelt 2x4",
            shape = TentShapeType.SAXON,
            widthMeters = 4f,
            heightMeters = 3f,
            polygonPoints = saxonOutline(ridgeHalf = 1.0f, halfDepth = 1.0f, halfLength = 2.0f, porchHalfWidth = 0.5f, porchDepth = 1.0f),
            ropeAnchors =
                listOf(
                    RopeAnchor(localX = -1.0f, localY = 0f, lengthMeters = 2.0f, outwardAngleDeg = 180f),
                    RopeAnchor(localX = 1.0f, localY = 0f, lengthMeters = 2.0f, outwardAngleDeg = 0f),
                    RopeAnchor(localX = 0.5f, localY = -2.0f, lengthMeters = 1.5f, outwardAngleDeg = -45f),
                    RopeAnchor(localX = -0.5f, localY = -2.0f, lengthMeters = 1.5f, outwardAngleDeg = 225f),
                ),
            defaultHeightRange = HeightRange(0f, 2.0f),
            defaultSafetyMarginMeters = 0.5f,
        ),
        TentPreset(
            id = "saxon_3x5",
            name = "Sachsenzelt 3x5",
            shape = TentShapeType.SAXON,
            widthMeters = 5f,
            heightMeters = 4.2f,
            polygonPoints = saxonOutline(ridgeHalf = 1.5f, halfDepth = 1.5f, halfLength = 2.5f, porchHalfWidth = 0.75f, porchDepth = 1.2f),
            ropeAnchors =
                listOf(
                    RopeAnchor(localX = -1.5f, localY = 0f, lengthMeters = 2.5f, outwardAngleDeg = 180f),
                    RopeAnchor(localX = 1.5f, localY = 0f, lengthMeters = 2.5f, outwardAngleDeg = 0f),
                    RopeAnchor(localX = 0.75f, localY = -2.7f, lengthMeters = 1.8f, outwardAngleDeg = -45f),
                    RopeAnchor(localX = -0.75f, localY = -2.7f, lengthMeters = 1.8f, outwardAngleDeg = 225f),
                ),
            defaultHeightRange = HeightRange(0f, 2.2f),
            defaultSafetyMarginMeters = 0.6f,
        ),
        TentPreset(
            id = "saxon_4x6",
            name = "Sachsenzelt 4x6",
            shape = TentShapeType.SAXON,
            widthMeters = 6f,
            heightMeters = 5.5f,
            polygonPoints = saxonOutline(ridgeHalf = 1.5f, halfDepth = 2.0f, halfLength = 3.0f, porchHalfWidth = 1.0f, porchDepth = 1.5f),
            ropeAnchors =
                listOf(
                    RopeAnchor(localX = -1.5f, localY = 0f, lengthMeters = 3.0f, outwardAngleDeg = 180f),
                    RopeAnchor(localX = 1.5f, localY = 0f, lengthMeters = 3.0f, outwardAngleDeg = 0f),
                    RopeAnchor(localX = 1.0f, localY = -3.5f, lengthMeters = 2.0f, outwardAngleDeg = -45f),
                    RopeAnchor(localX = -1.0f, localY = -3.5f, lengthMeters = 2.0f, outwardAngleDeg = 225f),
                ),
            defaultHeightRange = HeightRange(0f, 2.5f),
            defaultSafetyMarginMeters = 0.8f,
        ),
        // ==========================================
        // SACHSENZELTE (GESCHLOSSEN - OHNE VORDACH)
        // ==========================================
        // 1. Sachsen 2x4 Geschlossen (Reine Grundfläche ca. 2x4m)
        TentPreset(
            id = "saxon_2x4_closed",
            name = "Sachsenzelt 2x4 (Geschlossen)",
            shape = TentShapeType.SAXON,
            widthMeters = 4.0f,
            heightMeters = 2.0f,
            polygonPoints = saxonOutline(ridgeHalf = 1.0f, halfDepth = 1.0f, halfLength = 2.0f),
            ropeAnchors =
                listOf(
                    RopeAnchor(-1.0f, 1.0f, 1.5f, -90f),
                    RopeAnchor(1.0f, 1.0f, 1.5f, -90f),
                    RopeAnchor(1.0f, -1.0f, 1.5f, 90f),
                    RopeAnchor(-1.0f, -1.0f, 1.5f, 90f),
                ),
            defaultHeightRange = HeightRange(0f, 2.0f),
        ),
        // 2. Sachsen 3x5 Geschlossen (Reine Grundfläche ca. 3x5m)
        TentPreset(
            id = "saxon_3x5_closed",
            name = "Sachsenzelt 3x5 (Geschlossen)",
            shape = TentShapeType.SAXON,
            widthMeters = 5.0f,
            heightMeters = 3.0f,
            polygonPoints = saxonOutline(ridgeHalf = 1.0f, halfDepth = 1.5f, halfLength = 2.5f),
            ropeAnchors =
                listOf(
                    RopeAnchor(-1.0f, 0f, 2.5f, 180f),
                    RopeAnchor(1.0f, 0f, 2.5f, 0f),
                ),
            defaultHeightRange = HeightRange(0f, 2.2f),
        ),
        // 3. Sachsen 4x6 Geschlossen (Reine Grundfläche ca. 4x6m)
        TentPreset(
            id = "saxon_4x6_closed",
            name = "Sachsenzelt 4x6 (Geschlossen)",
            shape = TentShapeType.SAXON,
            widthMeters = 6.0f,
            heightMeters = 4.0f,
            polygonPoints = saxonOutline(ridgeHalf = 1.5f, halfDepth = 2.0f, halfLength = 3.0f),
            ropeAnchors =
                listOf(
                    RopeAnchor(-1.5f, 0f, 3.0f, 180f),
                    RopeAnchor(1.5f, 0f, 3.0f, 0f),
                ),
            defaultHeightRange = HeightRange(0f, 2.5f),
        ),
        TentPreset(
            id = "saxon_niord_6",
            name = "Sachsenzelt Niord 6m",
            shape = TentShapeType.SAXON,
            widthMeters = 6f,
            heightMeters = 4f,
            polygonPoints = saxonOutline(ridgeHalf = 1.5f, halfDepth = 1.25f, halfLength = 3.0f, porchHalfWidth = 0.75f, porchDepth = 1.5f),
            ropeAnchors =
                listOf(
                    RopeAnchor(localX = -1.5f, localY = 0f, lengthMeters = 3.0f, outwardAngleDeg = 180f),
                    RopeAnchor(localX = 1.5f, localY = 0f, lengthMeters = 3.0f, outwardAngleDeg = 0f),
                    RopeAnchor(localX = 0.75f, localY = -2.75f, lengthMeters = 2.0f, outwardAngleDeg = -45f),
                    RopeAnchor(localX = -0.75f, localY = -2.75f, lengthMeters = 2.0f, outwardAngleDeg = 225f),
                ),
            defaultSafetyMarginMeters = 0.8f,
            defaultHeightRange = HeightRange(0f, 2.2f),
        ),
        // ==========================================
        // KEILZELTE (A-FRAME) - RECHTECKIGE FORM
        // ==========================================
        TentPreset(
            id = "a_tent_2x3_basic",
            name = "Keilzelt 2x3 (Minimal)",
            shape = TentShapeType.A_FRAME,
            widthMeters = 2.0f,
            heightMeters = 3.0f,
            ropeAnchors =
                listOf(
                    RopeAnchor(localX = 0f, localY = -1.5f, lengthMeters = 1.8f, outwardAngleDeg = -90f),
                    RopeAnchor(localX = 0f, localY = 1.5f, lengthMeters = 1.8f, outwardAngleDeg = 90f),
                ),
            defaultHeightRange = HeightRange(0f, 1.8f),
            defaultSafetyMarginMeters = 0.3f,
        ),
        TentPreset(
            id = "a_tent_2_5x3_full",
            name = "Keilzelt 2.5x3 (Sturm-Setup)",
            shape = TentShapeType.A_FRAME,
            widthMeters = 2.5f,
            heightMeters = 3.0f,
            ropeAnchors =
                listOf(
                    RopeAnchor(localX = 0f, localY = -1.5f, lengthMeters = 2.0f, outwardAngleDeg = -90f),
                    RopeAnchor(localX = 0f, localY = 1.5f, lengthMeters = 2.0f, outwardAngleDeg = 90f),
                    RopeAnchor(localX = -1.25f, localY = 0f, lengthMeters = 0.6f, outwardAngleDeg = 180f),
                    RopeAnchor(localX = 1.25f, localY = 0f, lengthMeters = 0.6f, outwardAngleDeg = 0f),
                ),
            defaultHeightRange = HeightRange(0f, 2.0f),
            defaultSafetyMarginMeters = 0.5f,
        ),
        TentPreset(
            id = "a_tent_3x4_frame",
            name = "Keilzelt 3x4 (Holzgestell)",
            shape = TentShapeType.A_FRAME,
            widthMeters = 3.0f,
            heightMeters = 4.0f,
            ropeAnchors = emptyList(),
            defaultHeightRange = HeightRange(0f, 2.3f),
            defaultSafetyMarginMeters = 0.2f,
        ),
        TentPreset(
            id = "a_tent_4x5_pro",
            name = "Keilzelt 4x5 (V-Abspannung)",
            shape = TentShapeType.A_FRAME,
            widthMeters = 4.0f,
            heightMeters = 5.0f,
            ropeAnchors =
                listOf(
                    RopeAnchor(localX = 0f, localY = -2.5f, lengthMeters = 2.5f, outwardAngleDeg = -75f),
                    RopeAnchor(localX = 0f, localY = -2.5f, lengthMeters = 2.5f, outwardAngleDeg = -105f),
                    RopeAnchor(localX = 0f, localY = 2.5f, lengthMeters = 2.5f, outwardAngleDeg = 75f),
                    RopeAnchor(localX = 0f, localY = 2.5f, lengthMeters = 2.5f, outwardAngleDeg = 105f),
                ),
            defaultHeightRange = HeightRange(0f, 2.5f),
            defaultSafetyMarginMeters = 0.8f,
        ),
        // ==========================================
        // RUNDZELTE & SPEICHENRADZELTE (Kapseln)
        // ==========================================
        TentPreset(
            id = "oval_6x4",
            name = "Speichenrad 6x4",
            shape = TentShapeType.CAPSULE,
            widthMeters = 6f,
            heightMeters = 4f,
            ropeAnchors =
                listOf(
                    RopeAnchor(localX = -3f, localY = 0f, lengthMeters = 1.5f, outwardAngleDeg = 180f),
                    RopeAnchor(localX = 3f, localY = 0f, lengthMeters = 1.5f, outwardAngleDeg = 0f),
                    RopeAnchor(localX = 0f, localY = -2f, lengthMeters = 1.5f, outwardAngleDeg = -90f),
                    RopeAnchor(localX = 0f, localY = 2f, lengthMeters = 1.5f, outwardAngleDeg = 90f),
                ),
            defaultHeightRange = HeightRange(0f, 2.3f),
            defaultSafetyMarginMeters = 0.8f,
        ),
        TentPreset(
            id = "round_4",
            name = "Rundzelt 4m",
            shape = TentShapeType.CIRCLE,
            widthMeters = 4f,
            heightMeters = 4f,
            ropeAnchors =
                listOf(
                    RopeAnchor(localX = 0f, localY = -2f, lengthMeters = 1.5f, outwardAngleDeg = -90f),
                    RopeAnchor(localX = 2f, localY = 0f, lengthMeters = 1.5f, outwardAngleDeg = 0f),
                    RopeAnchor(localX = 0f, localY = 2f, lengthMeters = 1.5f, outwardAngleDeg = 90f),
                    RopeAnchor(localX = -2f, localY = 0f, lengthMeters = 1.5f, outwardAngleDeg = 180f),
                ),
            defaultHeightRange = HeightRange(0f, 2.6f),
        ),
        TentPreset(
            id = "round_3x3",
            name = "Rundzelt 3x3",
            shape = TentShapeType.CIRCLE,
            widthMeters = 3f,
            heightMeters = 3f,
            ropeAnchors =
                listOf(
                    RopeAnchor(localX = 0f, localY = -1.5f, lengthMeters = 1.3f, outwardAngleDeg = -90f),
                    RopeAnchor(localX = 1.5f, localY = 0f, lengthMeters = 1.3f, outwardAngleDeg = 0f),
                    RopeAnchor(localX = 0f, localY = 1.5f, lengthMeters = 1.3f, outwardAngleDeg = 90f),
                    RopeAnchor(localX = -1.5f, localY = 0f, lengthMeters = 1.3f, outwardAngleDeg = 180f),
                ),
            defaultHeightRange = HeightRange(0f, 2.3f),
        ),
        TentPreset(
            id = "round_5",
            name = "Rundzelt 5m",
            shape = TentShapeType.CIRCLE,
            widthMeters = 5f,
            heightMeters = 5f,
            ropeAnchors =
                listOf(
                    RopeAnchor(localX = 0f, localY = -2.5f, lengthMeters = 1.8f, outwardAngleDeg = -90f),
                    RopeAnchor(localX = 2.5f, localY = 0f, lengthMeters = 1.8f, outwardAngleDeg = 0f),
                    RopeAnchor(localX = 0f, localY = 2.5f, lengthMeters = 1.8f, outwardAngleDeg = 90f),
                    RopeAnchor(localX = -2.5f, localY = 0f, lengthMeters = 1.8f, outwardAngleDeg = 180f),
                ),
            defaultHeightRange = HeightRange(0f, 3.5f),
        ),
        // ==========================================
        // SONNENSEGEL - 6 METER KLASSE
        // ==========================================
        TentPreset(
            id = "sun_sail_6x6_flat",
            name = "Sonnensegel 6x6 (Flach/Dach)",
            shape = TentShapeType.POLYGON,
            widthMeters = 6f,
            heightMeters = 6f,
            polygonPoints = listOf(Vec2(-3f, -3f), Vec2(3f, -3f), Vec2(3f, 3f), Vec2(-3f, 3f)),
            ropeAnchors =
                listOf(
                    RopeAnchor(-3f, -3f, 2.5f, 210f),
                    RopeAnchor(-3f, -3f, 2.5f, 240f),
                    RopeAnchor(3f, -3f, 2.5f, 300f),
                    RopeAnchor(3f, -3f, 2.5f, 330f),
                    RopeAnchor(3f, 3f, 2.5f, 30f),
                    RopeAnchor(3f, 3f, 2.5f, 60f),
                    RopeAnchor(-3f, 3f, 2.5f, 120f),
                    RopeAnchor(-3f, 3f, 2.5f, 150f),
                    RopeAnchor(0f, -3f, 2.0f, 255f),
                    RopeAnchor(0f, -3f, 2.0f, 285f),
                    RopeAnchor(3f, 0f, 2.0f, 345f),
                    RopeAnchor(3f, 0f, 2.0f, 15f),
                    RopeAnchor(0f, 3f, 2.0f, 75f),
                    RopeAnchor(0f, 3f, 2.0f, 105f),
                    RopeAnchor(-3f, 0f, 2.0f, 165f),
                    RopeAnchor(-3f, 0f, 2.0f, 195f),
                ),
            defaultHeightRange = HeightRange(2.0f, 3.0f),
        ),
        TentPreset(
            id = "sun_sail_6x6_diamond",
            name = "Sonnensegel 6x6 (Diagonal/Raute)",
            shape = TentShapeType.POLYGON,
            widthMeters = 8.5f,
            heightMeters = 8.5f,
            polygonPoints = listOf(Vec2(0f, -4.25f), Vec2(4.25f, 0f), Vec2(0f, 4.25f), Vec2(-4.25f, 0f)),
            ropeAnchors =
                listOf(
                    RopeAnchor(0f, -4.25f, 2.0f, 255f),
                    RopeAnchor(0f, -4.25f, 2.0f, 285f),
                    RopeAnchor(4.25f, 0f, 2.0f, 345f),
                    RopeAnchor(4.25f, 0f, 2.0f, 15f),
                    RopeAnchor(0f, 4.25f, 2.0f, 75f),
                    RopeAnchor(0f, 4.25f, 2.0f, 105f),
                    RopeAnchor(-4.25f, 0f, 2.0f, 165f),
                    RopeAnchor(-4.25f, 0f, 2.0f, 195f),
                    RopeAnchor(2.12f, -2.12f, 1.5f, 300f),
                    RopeAnchor(2.12f, -2.12f, 1.5f, 330f),
                    RopeAnchor(2.12f, 2.12f, 1.5f, 30f),
                    RopeAnchor(2.12f, 2.12f, 1.5f, 60f),
                    RopeAnchor(-2.12f, 2.12f, 1.5f, 120f),
                    RopeAnchor(-2.12f, 2.12f, 1.5f, 150f),
                    RopeAnchor(-2.12f, -2.12f, 1.5f, 210f),
                    RopeAnchor(-2.12f, -2.12f, 1.5f, 240f),
                ),
            defaultHeightRange = HeightRange(0.5f, 3.5f),
        ),
        TentPreset(
            id = "sun_sail_6x2_flat",
            name = "Sonnensegel 6x2 (Flach aufgebaut)",
            shape = TentShapeType.POLYGON,
            widthMeters = 6f,
            heightMeters = 2f,
            polygonPoints = listOf(Vec2(-3f, -1f), Vec2(3f, -1f), Vec2(3f, 1f), Vec2(-3f, 1f)),
            ropeAnchors =
                listOf(
                    RopeAnchor(-3f, -1f, 1.5f, 210f),
                    RopeAnchor(-3f, -1f, 1.5f, 240f),
                    RopeAnchor(3f, -1f, 1.5f, 300f),
                    RopeAnchor(3f, -1f, 1.5f, 330f),
                    RopeAnchor(3f, 1f, 1.5f, 30f),
                    RopeAnchor(3f, 1f, 1.5f, 60f),
                    RopeAnchor(-3f, 1f, 1.5f, 120f),
                    RopeAnchor(-3f, 1f, 1.5f, 150f),
                    RopeAnchor(0f, -1f, 1.5f, 255f),
                    RopeAnchor(0f, -1f, 1.5f, 285f),
                    RopeAnchor(0f, 1f, 1.5f, 75f),
                    RopeAnchor(0f, 1f, 1.5f, 105f),
                ),
            defaultHeightRange = HeightRange(2.0f, 2.4f),
        ),
        TentPreset(
            id = "sun_sail_6x2_folded",
            name = "Sonnensegel 6x2 (als 4x2, Seiten geklappt)",
            shape = TentShapeType.POLYGON,
            widthMeters = 4f,
            heightMeters = 2f,
            polygonPoints = listOf(Vec2(-2f, -1f), Vec2(2f, -1f), Vec2(2f, 1f), Vec2(-2f, 1f)),
            ropeAnchors =
                listOf(
                    RopeAnchor(-2f, -1f, 1.2f, 210f),
                    RopeAnchor(-2f, -1f, 1.2f, 240f),
                    RopeAnchor(2f, -1f, 1.2f, 300f),
                    RopeAnchor(2f, -1f, 1.2f, 330f),
                    RopeAnchor(2f, 1f, 1.2f, 30f),
                    RopeAnchor(2f, 1f, 1.2f, 60f),
                    RopeAnchor(-2f, 1f, 1.2f, 120f),
                    RopeAnchor(-2f, 1f, 1.2f, 150f),
                    RopeAnchor(0f, -1f, 1.2f, 255f),
                    RopeAnchor(0f, -1f, 1.2f, 285f),
                    RopeAnchor(0f, 1f, 1.2f, 75f),
                    RopeAnchor(0f, 1f, 1.2f, 105f),
                ),
            defaultHeightRange = HeightRange(1.0f, 2.2f),
        ),
        // ==========================================
        // SONNENSEGEL - 5 METER KLASSE
        // ==========================================
        TentPreset(
            id = "sun_sail_5x5_flat",
            name = "Sonnensegel 5x5 (Flach/Dach)",
            shape = TentShapeType.POLYGON,
            widthMeters = 5f,
            heightMeters = 5f,
            polygonPoints = listOf(Vec2(-2.5f, -2.5f), Vec2(2.5f, -2.5f), Vec2(2.5f, 2.5f), Vec2(-2.5f, 2.5f)),
            ropeAnchors =
                listOf(
                    RopeAnchor(-2.5f, -2.5f, 2.0f, 210f),
                    RopeAnchor(-2.5f, -2.5f, 2.0f, 240f),
                    RopeAnchor(2.5f, -2.5f, 2.0f, 300f),
                    RopeAnchor(2.5f, -2.5f, 2.0f, 330f),
                    RopeAnchor(2.5f, 2.5f, 2.0f, 30f),
                    RopeAnchor(2.5f, 2.5f, 2.0f, 60f),
                    RopeAnchor(-2.5f, 2.5f, 2.0f, 120f),
                    RopeAnchor(-2.5f, 2.5f, 2.0f, 150f),
                    RopeAnchor(0f, -2.5f, 1.5f, 255f),
                    RopeAnchor(0f, -2.5f, 1.5f, 285f),
                    RopeAnchor(2.5f, 0f, 1.5f, 345f),
                    RopeAnchor(2.5f, 0f, 1.5f, 15f),
                    RopeAnchor(0f, 2.5f, 1.5f, 75f),
                    RopeAnchor(0f, 2.5f, 1.5f, 105f),
                    RopeAnchor(-2.5f, 0f, 1.5f, 165f),
                    RopeAnchor(-2.5f, 0f, 1.5f, 195f),
                ),
            defaultHeightRange = HeightRange(2.0f, 2.5f),
        ),
        TentPreset(
            id = "sun_sail_5x5_diamond",
            name = "Sonnensegel 5x5 (Diagonal/Raute)",
            shape = TentShapeType.POLYGON,
            widthMeters = 7.1f,
            heightMeters = 7.1f,
            polygonPoints = listOf(Vec2(0f, -3.55f), Vec2(3.55f, 0f), Vec2(0f, 3.55f), Vec2(-3.55f, 0f)),
            ropeAnchors =
                listOf(
                    RopeAnchor(0f, -3.55f, 1.5f, 255f),
                    RopeAnchor(0f, -3.55f, 1.5f, 285f),
                    RopeAnchor(3.55f, 0f, 1.5f, 345f),
                    RopeAnchor(3.55f, 0f, 1.5f, 15f),
                    RopeAnchor(0f, 3.55f, 1.5f, 75f),
                    RopeAnchor(0f, 3.55f, 1.5f, 105f),
                    RopeAnchor(-3.55f, 0f, 1.5f, 165f),
                    RopeAnchor(-3.55f, 0f, 1.5f, 195f),
                    RopeAnchor(1.77f, -1.77f, 1.5f, 300f),
                    RopeAnchor(1.77f, -1.77f, 1.5f, 330f),
                    RopeAnchor(1.77f, 1.77f, 1.5f, 30f),
                    RopeAnchor(1.77f, 1.77f, 1.5f, 60f),
                    RopeAnchor(-1.77f, 1.77f, 1.5f, 120f),
                    RopeAnchor(-1.77f, 1.77f, 1.5f, 150f),
                    RopeAnchor(-1.77f, -1.77f, 1.5f, 210f),
                    RopeAnchor(-1.77f, -1.77f, 1.5f, 240f),
                ),
            defaultHeightRange = HeightRange(0.5f, 3.2f),
        ),
        TentPreset(
            id = "sun_sail_5x3_flat",
            name = "Sonnensegel 5x3 (Flach/Dach)",
            shape = TentShapeType.POLYGON,
            widthMeters = 5f,
            heightMeters = 3f,
            polygonPoints = listOf(Vec2(-2.5f, -1.5f), Vec2(2.5f, -1.5f), Vec2(2.5f, 1.5f), Vec2(-2.5f, 1.5f)),
            ropeAnchors =
                listOf(
                    RopeAnchor(-2.5f, -1.5f, 1.8f, 210f),
                    RopeAnchor(-2.5f, -1.5f, 1.8f, 240f),
                    RopeAnchor(2.5f, -1.5f, 1.8f, 300f),
                    RopeAnchor(2.5f, -1.5f, 1.8f, 330f),
                    RopeAnchor(2.5f, 1.5f, 1.8f, 30f),
                    RopeAnchor(2.5f, 1.5f, 1.8f, 60f),
                    RopeAnchor(-2.5f, 1.5f, 1.8f, 120f),
                    RopeAnchor(-2.5f, 1.5f, 1.8f, 150f),
                    RopeAnchor(0f, -1.5f, 1.5f, 255f),
                    RopeAnchor(0f, -1.5f, 1.5f, 285f),
                    RopeAnchor(0f, 1.5f, 1.5f, 75f),
                    RopeAnchor(0f, 1.5f, 1.5f, 105f),
                ),
            defaultHeightRange = HeightRange(1.9f, 2.3f),
        ),
        // ==========================================
        // SONNENSEGEL - 4 METER KLASSE
        // ==========================================
        TentPreset(
            id = "sun_sail_4x6",
            name = "Sonnensegel 4x6",
            shape = TentShapeType.POLYGON,
            widthMeters = 6f,
            heightMeters = 4f,
            polygonPoints = listOf(Vec2(-3f, -2f), Vec2(3f, -2f), Vec2(3f, 2f), Vec2(-3f, 2f)),
            ropeAnchors =
                listOf(
                    RopeAnchor(-3f, -2f, 2.0f, 210f),
                    RopeAnchor(-3f, -2f, 2.0f, 240f),
                    RopeAnchor(3f, -2f, 2.0f, 300f),
                    RopeAnchor(3f, -2f, 2.0f, 330f),
                    RopeAnchor(3f, 2f, 2.0f, 30f),
                    RopeAnchor(3f, 2f, 2.0f, 60f),
                    RopeAnchor(-3f, 2f, 2.0f, 120f),
                    RopeAnchor(-3f, 2f, 2.0f, 150f),
                    RopeAnchor(0f, -2f, 1.5f, 255f),
                    RopeAnchor(0f, -2f, 1.5f, 285f),
                    RopeAnchor(0f, 2f, 1.5f, 75f),
                    RopeAnchor(0f, 2f, 1.5f, 105f),
                ),
            defaultHeightRange = HeightRange(1.9f, 3.4f),
        ),
        TentPreset(
            id = "sun_sail_4x4_flat",
            name = "Sonnensegel 4x4 (Flach/Dach)",
            shape = TentShapeType.POLYGON,
            widthMeters = 4f,
            heightMeters = 4f,
            polygonPoints = listOf(Vec2(-2f, -2f), Vec2(2f, -2f), Vec2(2f, 2f), Vec2(-2f, 2f)),
            ropeAnchors =
                listOf(
                    RopeAnchor(-2f, -2f, 1.8f, 210f),
                    RopeAnchor(-2f, -2f, 1.8f, 240f),
                    RopeAnchor(2f, -2f, 1.8f, 300f),
                    RopeAnchor(2f, -2f, 1.8f, 330f),
                    RopeAnchor(2f, 2f, 1.8f, 30f),
                    RopeAnchor(2f, 2f, 1.8f, 60f),
                    RopeAnchor(-2f, 2f, 1.8f, 120f),
                    RopeAnchor(-2f, 2f, 1.8f, 150f),
                ),
            defaultHeightRange = HeightRange(1.9f, 2.3f),
        ),
        TentPreset(
            id = "sun_sail_4x4_diamond",
            name = "Sonnensegel 4x4 (Diagonal/Raute)",
            shape = TentShapeType.POLYGON,
            widthMeters = 5.6f,
            heightMeters = 5.6f,
            polygonPoints = listOf(Vec2(0f, -2.8f), Vec2(2.8f, 0f), Vec2(0f, 2.8f), Vec2(-2.8f, 0f)),
            ropeAnchors =
                listOf(
                    RopeAnchor(0f, -2.8f, 1.5f, 255f),
                    RopeAnchor(0f, -2.8f, 1.5f, 285f),
                    RopeAnchor(2.8f, 0f, 1.5f, 345f),
                    RopeAnchor(2.8f, 0f, 1.5f, 15f),
                    RopeAnchor(0f, 2.8f, 1.5f, 75f),
                    RopeAnchor(0f, 2.8f, 1.5f, 105f),
                    RopeAnchor(-2.8f, 0f, 1.5f, 165f),
                    RopeAnchor(-2.8f, 0f, 1.5f, 195f),
                ),
            defaultHeightRange = HeightRange(0.5f, 3.0f),
        ),
        // ==========================================
        // SONNENSEGEL - 3 METER KLASSE
        // ==========================================
        TentPreset(
            id = "sun_sail_3x3_flat",
            name = "Sonnensegel 3x3 (Flach/Dach)",
            shape = TentShapeType.POLYGON,
            widthMeters = 3f,
            heightMeters = 3f,
            polygonPoints = listOf(Vec2(-1.5f, -1.5f), Vec2(1.5f, -1.5f), Vec2(1.5f, 1.5f), Vec2(-1.5f, 1.5f)),
            ropeAnchors =
                listOf(
                    RopeAnchor(-1.5f, -1.5f, 1.5f, 210f),
                    RopeAnchor(-1.5f, -1.5f, 1.5f, 240f),
                    RopeAnchor(1.5f, -1.5f, 1.5f, 300f),
                    RopeAnchor(1.5f, -1.5f, 1.5f, 330f),
                    RopeAnchor(1.5f, 1.5f, 1.5f, 30f),
                    RopeAnchor(1.5f, 1.5f, 1.5f, 60f),
                    RopeAnchor(-1.5f, 1.5f, 1.5f, 120f),
                    RopeAnchor(-1.5f, 1.5f, 1.5f, 150f),
                ),
            defaultHeightRange = HeightRange(1.8f, 2.1f),
        ),
        TentPreset(
            id = "sun_sail_3x3_diamond",
            name = "Sonnensegel 3x3 (Diagonal/Raute)",
            shape = TentShapeType.POLYGON,
            widthMeters = 4.2f,
            heightMeters = 4.2f,
            polygonPoints = listOf(Vec2(0f, -2.1f), Vec2(2.1f, 0f), Vec2(0f, 2.1f), Vec2(-2.1f, 0f)),
            ropeAnchors =
                listOf(
                    RopeAnchor(0f, -2.1f, 1.2f, 255f),
                    RopeAnchor(0f, -2.1f, 1.2f, 285f),
                    RopeAnchor(2.1f, 0f, 1.2f, 345f),
                    RopeAnchor(2.1f, 0f, 1.2f, 15f),
                    RopeAnchor(0f, 2.1f, 1.2f, 75f),
                    RopeAnchor(0f, 2.1f, 1.2f, 105f),
                    RopeAnchor(-2.1f, 0f, 1.2f, 165f),
                    RopeAnchor(-2.1f, 0f, 1.2f, 195f),
                ),
            defaultHeightRange = HeightRange(0.5f, 2.5f),
        ),
        TentPreset(
            id = "sun_sail_3x3_lean_to",
            name = "Sonnensegel 3x3 (Schrägdach/Kröte)",
            shape = TentShapeType.POLYGON,
            widthMeters = 3f,
            heightMeters = 2.5f,
            polygonPoints = listOf(Vec2(-1.5f, -1.25f), Vec2(1.5f, -1.25f), Vec2(1.5f, 1.25f), Vec2(-1.5f, 1.25f)),
            ropeAnchors =
                listOf(
                    // Vorne aufgestellt (V-Abspannung für die Masten)
                    RopeAnchor(-1.5f, -1.25f, 1.5f, 210f),
                    RopeAnchor(-1.5f, -1.25f, 1.5f, 240f),
                    RopeAnchor(1.5f, -1.25f, 1.5f, 300f),
                    RopeAnchor(1.5f, -1.25f, 1.5f, 330f),
                    // Hinten direkt am Boden angenagelt (Einzelhering pro Ecke reicht)
                    RopeAnchor(1.5f, 1.25f, 0.2f, 45f),
                    RopeAnchor(-1.5f, 1.25f, 0.2f, 135f),
                ),
            defaultHeightRange = HeightRange(0f, 2.0f),
        ),
    ).map { preset ->
        if (preset.polygonPoints.isNotEmpty()) {
            preset
        } else {
            val generated =
                GeometryHelper.generatePoints(
                    shape = preset.shape,
                    widthMeters = preset.widthMeters,
                    depthMeters = preset.heightMeters,
                )
            if (generated.isEmpty()) preset else preset.copy(polygonPoints = generated)
        }
    }
