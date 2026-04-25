package de.voculupo.historicalcamplayout.domain.model

import de.voculupo.historicalcamplayout.GeometryHelper
import kotlinx.serialization.Serializable

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
)

@Serializable
data class PlannerProject(
    val name: String = "Neues Lager",
    val parcel: Parcel = Parcel(),
    val assets: List<PlacedAsset> = emptyList(),
    val blockZones: List<BlockZone> = emptyList(),
    val pixelsPerMeter: Float = 50f,
    val gridMeters: Float = 0.5f,
)

data class PlannerUiState(
    val project: PlannerProject = PlannerProject(),
    val presets: List<TentPreset> = defaultPresets(),
    val selectedAssetIds: Set<String> = emptySet(),
    val selectedRopeIndex: Int? = null,
    val currentTool: PlannerToolMode = PlannerToolMode.Select,
    val activePresetId: String? = null,
    val recentPresetIds: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val warningAssetIds: Set<String> = emptySet(),
    val hardCollisionAssetIds: Set<String> = emptySet(),
    val measurementStart: Vec2? = null,
    val measurementEnd: Vec2? = null,
    val pendingBlockZoneStart: Vec2? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

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
            polygonPoints =
                listOf(
                    Vec2(-1.0f, 1.0f),
                    Vec2(1.0f, 1.0f),
                    Vec2(1.5f, 0.5f),
                    Vec2(2.0f, 0.0f),
                    Vec2(1.5f, -0.5f),
                    Vec2(1.0f, -1.0f),
                    Vec2(0.5f, -1.0f),
                    Vec2(0.5f, -2.0f),
                    Vec2(-0.5f, -2.0f),
                    Vec2(-0.5f, -1.0f),
                    Vec2(-1.0f, -1.0f),
                    Vec2(-1.5f, -0.5f),
                    Vec2(-2.0f, 0.0f),
                    Vec2(-1.5f, 0.5f),
                ),
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
            polygonPoints =
                listOf(
                    Vec2(-1.5f, 1.5f),
                    Vec2(1.5f, 1.5f),
                    Vec2(2.0f, 0.8f),
                    Vec2(2.5f, 0.0f),
                    Vec2(2.0f, -0.8f),
                    Vec2(1.5f, -1.5f),
                    Vec2(0.75f, -1.5f),
                    Vec2(0.75f, -2.7f),
                    Vec2(-0.75f, -2.7f),
                    Vec2(-0.75f, -1.5f),
                    Vec2(-1.5f, -1.5f),
                    Vec2(-2.0f, -0.8f),
                    Vec2(-2.5f, 0.0f),
                    Vec2(-2.0f, 0.8f),
                ),
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
            polygonPoints =
                listOf(
                    Vec2(-1.5f, 2.0f),
                    Vec2(1.5f, 2.0f),
                    Vec2(2.5f, 1.0f),
                    Vec2(3.0f, 0.0f),
                    Vec2(2.5f, -1.0f),
                    Vec2(1.5f, -2.0f),
                    Vec2(1.0f, -2.0f),
                    Vec2(1.0f, -3.5f),
                    Vec2(-1.0f, -3.5f),
                    Vec2(-1.0f, -2.0f),
                    Vec2(-1.5f, -2.0f),
                    Vec2(-2.5f, -1.0f),
                    Vec2(-3.0f, 0.0f),
                    Vec2(-2.5f, 1.0f),
                ),
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
        TentPreset(
            id = "saxon_niord_6",
            name = "Sachsenzelt Niord 6m",
            shape = TentShapeType.SAXON,
            widthMeters = 6f,
            heightMeters = 4f,
            polygonPoints =
                listOf(
                    Vec2(-1.5f, 1.25f),
                    Vec2(1.5f, 1.25f),
                    Vec2(2.5f, 0.8f),
                    Vec2(3.0f, 0.0f),
                    Vec2(2.5f, -0.8f),
                    Vec2(1.5f, -1.25f),
                    Vec2(0.75f, -1.25f),
                    Vec2(0.75f, -2.75f),
                    Vec2(-0.75f, -2.75f),
                    Vec2(-0.75f, -1.25f),
                    Vec2(-1.5f, -1.25f),
                    Vec2(-2.5f, -0.8f),
                    Vec2(-3.0f, 0.0f),
                    Vec2(-2.5f, 0.8f),
                ),
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
        // KEILZELTE (A-FRAME)
        // ==========================================
        // 1. Kleines Keilzelt (2x3m) - Nur Hauptstangen
        TentPreset(
            id = "a_tent_2x3_basic",
            name = "Keilzelt 2x3 (Minimal)",
            shape = TentShapeType.A_FRAME,
            widthMeters = 2.0f,
            heightMeters = 3.0f,
            ropeAnchors =
                listOf(
                    // Zug nach vorne und hinten
                    RopeAnchor(localX = 0f, localY = -1.5f, lengthMeters = 1.8f, outwardAngleDeg = -90f),
                    RopeAnchor(localX = 0f, localY = 1.5f, lengthMeters = 1.8f, outwardAngleDeg = 90f),
                ),
            defaultHeightRange = HeightRange(0f, 1.8f),
            defaultSafetyMarginMeters = 0.3f,
        ),
        // 2. Standard Keilzelt (2.5x3m) - Mit Seitenabspannung
        // (Viele Keilzelte haben kleine Schlaufen an den Seiten, um die Plane zu straffen)
        TentPreset(
            id = "a_tent_2_5x3_full",
            name = "Keilzelt 2.5x3 (Sturm-Setup)",
            shape = TentShapeType.A_FRAME,
            widthMeters = 2.5f,
            heightMeters = 3.0f,
            ropeAnchors =
                listOf(
                    // Hauptmasten
                    RopeAnchor(localX = 0f, localY = -1.5f, lengthMeters = 2.0f, outwardAngleDeg = -90f),
                    RopeAnchor(localX = 0f, localY = 1.5f, lengthMeters = 2.0f, outwardAngleDeg = 90f),
                    // Seiten-Heringe (kurze Seile direkt am Boden)
                    RopeAnchor(localX = -1.25f, localY = 0f, lengthMeters = 0.6f, outwardAngleDeg = 180f),
                    RopeAnchor(localX = 1.25f, localY = 0f, lengthMeters = 0.6f, outwardAngleDeg = 0f),
                ),
            defaultHeightRange = HeightRange(0f, 2.0f),
            defaultSafetyMarginMeters = 0.5f,
        ),
        // 3. Großes Keilzelt (3x4m) - Ohne Seile (Self-Standing / Holzgestell)
        // Manche Keilzelte haben ein komplettes Außen- oder Innengestell aus Holz
        // und brauchen theoretisch gar keine Seile nach außen.
        TentPreset(
            id = "a_tent_3x4_frame",
            name = "Keilzelt 3x4 (Holzgestell/Keine Seile)",
            shape = TentShapeType.A_FRAME,
            widthMeters = 3.0f,
            heightMeters = 4.0f,
            ropeAnchors = emptyList(), // Komplett ohne Seile
            defaultHeightRange = HeightRange(0f, 2.3f),
            defaultSafetyMarginMeters = 0.2f,
        ),
        // 4. XL Keilzelt (4x5m) - V-Abspannung an den Masten
        // Bei sehr großen Keilen nutzt man oft 2 Seile pro Mast für die Stabilität
        TentPreset(
            id = "a_tent_4x5_pro",
            name = "Keilzelt 4x5 (V-Abspannung)",
            shape = TentShapeType.A_FRAME,
            widthMeters = 4.0f,
            heightMeters = 5.0f,
            ropeAnchors =
                listOf(
                    // V-Zug Vorne
                    RopeAnchor(localX = 0f, localY = -2.5f, lengthMeters = 2.5f, outwardAngleDeg = -75f),
                    RopeAnchor(localX = 0f, localY = -2.5f, lengthMeters = 2.5f, outwardAngleDeg = -105f),
                    // V-Zug Hinten
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
