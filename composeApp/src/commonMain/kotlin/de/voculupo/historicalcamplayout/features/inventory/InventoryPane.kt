package de.voculupo.historicalcamplayout.features.inventory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import de.voculupo.historicalcamplayout.PlannerToolMode
import de.voculupo.historicalcamplayout.PlannerUiState
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.TentShapeType
import de.voculupo.historicalcamplayout.GeometryHelper
import de.voculupo.historicalcamplayout.Vec2
import de.voculupo.historicalcamplayout.getPlatform

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun InventoryPane(
    modifier: Modifier = Modifier,
    enableFullScroll: Boolean = false,
    state: PlannerUiState,
    onTool: (PlannerToolMode) -> Unit,
    onPresetSelected: (String) -> Unit,
    onPresetDragStart: (presetId: String, label: String, rootPosition: Offset) -> Unit,
    onPresetDrag: (rootPosition: Offset) -> Unit,
    onPresetDragEnd: (presetId: String, rootPosition: Offset) -> Unit,
    onPresetDragCancel: () -> Unit = {},
    onParcelUpdate: (Float, Float) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    onClearZones: () -> Unit,
) {
    val useLongPressDrag = enableFullScroll && getPlatform().name.startsWith("Android")
    var widthInput by remember(state.project.parcel.widthMeters) { mutableStateOf(state.project.parcel.widthMeters.toString()) }
    var heightInput by remember(state.project.parcel.heightMeters) { mutableStateOf(state.project.parcel.heightMeters.toString()) }
    var presetFilter by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("Alle") }
    var showParcelZoom by remember { mutableStateOf(false) }
    var showPresetsSection by remember { mutableStateOf(true) }
    val visiblePresets = remember(state.presets, presetFilter, categoryFilter) {
        val query = presetFilter.trim().lowercase()
        val queryFiltered = if (query.isEmpty()) state.presets else state.presets.filter { preset ->
            preset.name.lowercase().contains(query) ||
                preset.id.lowercase().contains(query) ||
                preset.shape.name.lowercase().contains(query)
        }
        queryFiltered.filter { preset -> categoryFilter == "Alle" || mapCategory(preset) == categoryFilter }
    }
    val recentPresets = remember(state.recentPresetIds, state.presets) {
        state.recentPresetIds.mapNotNull { id -> state.presets.firstOrNull { it.id == id } }
    }
    val paneModifier = if (enableFullScroll) {
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    } else {
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(12.dp)
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = paneModifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Inventar", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ToolChip("Auswahl", state.currentTool == PlannerToolMode.Select) { onTool(PlannerToolMode.Select) }
                ToolChip("Messen", state.currentTool == PlannerToolMode.Measure) { onTool(PlannerToolMode.Measure) }
                ToolChip("Sperrzone", state.currentTool == PlannerToolMode.BlockZone) { onTool(PlannerToolMode.BlockZone) }
            }
            HorizontalDivider()

        CollapsibleHeader(
            title = "Parzelle & Zoom",
            expanded = showParcelZoom,
            onToggle = { showParcelZoom = !showParcelZoom },
        )
        if (showParcelZoom) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = widthInput,
                    onValueChange = { widthInput = it },
                    singleLine = true,
                    label = { Text("Breite (m)") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = heightInput,
                    onValueChange = { heightInput = it },
                    singleLine = true,
                    label = { Text("Höhe (m)") },
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = {
                    val w = widthInput.replace(',', '.').toFloatOrNull()
                    val h = heightInput.replace(',', '.').toFloatOrNull()
                    if (w != null && h != null) onParcelUpdate(w, h)
                },
            ) {
                Text("Fläche übernehmen")
            }
            Text("Zoom: ${(state.project.pixelsPerMeter / 50f * 100f).toInt()}%", style = MaterialTheme.typography.bodySmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = onZoomOut) { Text("Zoom -") }
                Button(modifier = Modifier.weight(1f), onClick = onZoomReset) { Text("Reset") }
                Button(modifier = Modifier.weight(1f), onClick = onZoomIn) { Text("Zoom +") }
            }
            Text("Zeltvorlage per Drag&Drop auf die Fläche ziehen.", color = MaterialTheme.colorScheme.primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(modifier = Modifier.fillMaxWidth().weight(1f), onClick = onClearZones, enabled = state.project.blockZones.isNotEmpty()) {
                    Text("Sperrzonen leeren")
                }
            }
            if (state.currentTool == PlannerToolMode.BlockZone) {
                Text("Blockzone: Ecke 1 klicken, dann Ecke 2 klicken.", color = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider()

        CollapsibleHeader(
            title = "Zeltvorlagen & Filter (${visiblePresets.size})",
            expanded = showPresetsSection,
            onToggle = { showPresetsSection = !showPresetsSection },
        )
        if (showPresetsSection) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CategoryChip("Alle", categoryFilter == "Alle") { categoryFilter = "Alle" }
                CategoryChip("Sonnensegel", categoryFilter == "Sonnensegel") { categoryFilter = "Sonnensegel" }
                CategoryChip("Sachsenzelt", categoryFilter == "Sachsenzelt") { categoryFilter = "Sachsenzelt" }
                CategoryChip("Rundzelt", categoryFilter == "Rundzelt") { categoryFilter = "Rundzelt" }
                CategoryChip("Keilzelt", categoryFilter == "Keilzelt") { categoryFilter = "Keilzelt" }
                CategoryChip("Andere", categoryFilter == "Andere") { categoryFilter = "Andere" }
            }
            if (recentPresets.isNotEmpty()) {
                HorizontalDivider()
                Text("Zuletzt verwendet", style = MaterialTheme.typography.titleSmall)
                if (enableFullScroll) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        recentPresets.take(4).forEach { preset ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(preset.id) { detectTapGestures { onPresetSelected(preset.id) } },
                            ) {
                                Text(
                                    preset.name,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(0.22f, fill = false)) {
                        items(recentPresets.take(4)) { preset ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(preset.id) { detectTapGestures { onPresetSelected(preset.id) } },
                            ) {
                                Text(
                                    preset.name,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = presetFilter,
                onValueChange = { presetFilter = it },
                singleLine = true,
                label = { Text("Preset suchen") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (useLongPressDrag) "Tipp: Scrollen mit Wisch. Zelt per Long-Press ziehen."
                else "Tipp: Doppelklick auf Zelt = Mehrfachauswahl umschalten",
                style = MaterialTheme.typography.bodySmall,
            )
            if (enableFullScroll) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    visiblePresets.forEach { preset ->
                        var cardRootOrigin by remember(preset.id) { mutableStateOf(Offset.Zero) }
                        var dragCurrent by remember(preset.id) { mutableStateOf(Offset.Zero) }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    val p = coords.positionInRoot()
                                    cardRootOrigin = Offset(p.x, p.y)
                                }
                                .border(
                                    width = if (preset.id == state.activePresetId) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                .pointerInput(preset.id) { detectTapGestures { onPresetSelected(preset.id) } }
                                .pointerInput(preset.id) {
                                    if (useLongPressDrag) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { localStart ->
                                                dragCurrent = cardRootOrigin + localStart
                                                onPresetDragStart(preset.id, preset.name, dragCurrent)
                                            },
                                            onDragEnd = {
                                                onPresetDragEnd(preset.id, dragCurrent)
                                            },
                                            onDragCancel = onPresetDragCancel,
                                            onDrag = { change, dragAmount ->
                                                dragCurrent += dragAmount
                                                onPresetDrag(dragCurrent)
                                                change.consume()
                                            },
                                        )
                                    } else {
                                        detectDragGestures(
                                            onDragStart = { localStart ->
                                                dragCurrent = cardRootOrigin + localStart
                                                onPresetDragStart(preset.id, preset.name, dragCurrent)
                                            },
                                            onDragEnd = {
                                                onPresetDragEnd(preset.id, dragCurrent)
                                            },
                                            onDragCancel = onPresetDragCancel,
                                            onDrag = { change, dragAmount ->
                                                dragCurrent += dragAmount
                                                onPresetDrag(dragCurrent)
                                                change.consume()
                                            },
                                        )
                                    }
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PresetPreviewIcon(preset = preset)
                                Column {
                                    Text(preset.name, style = MaterialTheme.typography.titleSmall)
                                    Text("${preset.widthMeters}m x ${preset.heightMeters}m", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(visiblePresets) { preset ->
                        var cardRootOrigin by remember(preset.id) { mutableStateOf(Offset.Zero) }
                        var dragCurrent by remember(preset.id) { mutableStateOf(Offset.Zero) }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    val p = coords.positionInRoot()
                                    cardRootOrigin = Offset(p.x, p.y)
                                }
                                .border(
                                    width = if (preset.id == state.activePresetId) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                .pointerInput(preset.id) { detectTapGestures { onPresetSelected(preset.id) } }
                                .pointerInput(preset.id) {
                                    detectDragGestures(
                                        onDragStart = { localStart ->
                                            dragCurrent = cardRootOrigin + localStart
                                            onPresetDragStart(preset.id, preset.name, dragCurrent)
                                        },
                                        onDragEnd = {
                                            onPresetDragEnd(preset.id, dragCurrent)
                                        },
                                        onDragCancel = onPresetDragCancel,
                                        onDrag = { change, dragAmount ->
                                            dragCurrent += dragAmount
                                            onPresetDrag(dragCurrent)
                                            change.consume()
                                        },
                                    )
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PresetPreviewIcon(preset = preset)
                                Column {
                                    Text(preset.name, style = MaterialTheme.typography.titleSmall)
                                    Text("${preset.widthMeters}m x ${preset.heightMeters}m", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun CollapsibleHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    AssistChip(
        onClick = onToggle,
        label = { Text(if (expanded) "▼ $title" else "▶ $title") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(if (selected) "• $label" else label) },
    )
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(if (selected) "• $label" else label) },
    )
}

@Composable
private fun PresetPreviewIcon(preset: TentPreset) {
    val bg = Color(0xFFEFE8D8)
    val fg = Color(0xFF6F5F4A)
    val stroke = Color(0xFF3E3E3E)
    BoxWithConstraints(
        modifier = Modifier
            .size(width = 56.dp, height = 42.dp)
            .border(1.dp, Color(0x22000000)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(bg)
            val points = (if (preset.polygonPoints.isNotEmpty()) {
                preset.polygonPoints
            } else {
                GeometryHelper.generatePoints(
                    shape = preset.shape,
                    widthMeters = preset.widthMeters,
                    depthMeters = preset.heightMeters,
                )
            }).ifEmpty {
                val hw = preset.widthMeters / 2f
                val hh = preset.heightMeters / 2f
                listOf(Vec2(-hw, -hh), Vec2(hw, -hh), Vec2(hw, hh), Vec2(-hw, hh))
            }

            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }
            val shapeW = (maxX - minX).coerceAtLeast(0.001f)
            val shapeH = (maxY - minY).coerceAtLeast(0.001f)
            val pad = 5f
            val scale = minOf((size.width - pad * 2f) / shapeW, (size.height - pad * 2f) / shapeH)
            val offsetX = (size.width - shapeW * scale) / 2f
            val offsetY = (size.height - shapeH * scale) / 2f

            val path = Path().apply {
                moveTo(offsetX + (points.first().x - minX) * scale, offsetY + (points.first().y - minY) * scale)
                points.drop(1).forEach { point ->
                    lineTo(offsetX + (point.x - minX) * scale, offsetY + (point.y - minY) * scale)
                }
                close()
            }
            drawPath(path, fg)
            drawPath(path, stroke, style = Stroke(width = 1.4f))
            drawRect(stroke, style = Stroke(1f))
        }
    }
}

private fun mapCategory(preset: TentPreset): String {
    val lower = preset.name.lowercase()
    return when {
        "sonnensegel" in lower -> "Sonnensegel"
        "sachsen" in lower -> "Sachsenzelt"
        "rund" in lower -> "Rundzelt"
        "keil" in lower || preset.shape == TentShapeType.A_FRAME -> "Keilzelt"
        else -> "Andere"
    }
}

