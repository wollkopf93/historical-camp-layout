package de.voculupo.historicalcamplayout.features.properties

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.PlannerUiState
import de.voculupo.historicalcamplayout.features.inventory.CollapsibleHeader

@Composable
internal fun PropertiesPane(
    modifier: Modifier = Modifier,
    enableFullScroll: Boolean = false,
    state: PlannerUiState,
    onProjectNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onResetProject: () -> Unit,
    onExportProjectFile: () -> Unit,
    onImportProjectFile: () -> Unit,
    onExportPng: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onLabelChanged: (String) -> Unit,
    onRotate: (Float) -> Unit,
    onToggleRopes: () -> Unit,
    onAddRope: () -> Unit,
    onRemoveRope: () -> Unit,
    onRestoreDefaultRopes: () -> Unit,
    onSelectRopeIndex: (Int?) -> Unit,
    onRopeScaleChanged: (Float) -> Unit,
    onRopeAngleChanged: (Float) -> Unit,
    onRopeAnchorXChanged: (Float) -> Unit,
    onRopeAnchorYChanged: (Float) -> Unit,
    onGroup: () -> Unit,
    onUngroup: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val paneTextColor = MaterialTheme.colorScheme.onSurface
    val paneMutedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    var projectNameInput by remember(state.project.name) { mutableStateOf(state.project.name) }
    var showProjectSection by remember { mutableStateOf(true) }
    var showSelectionSection by remember { mutableStateOf(true) }
    var showWarningsSection by remember { mutableStateOf(false) }
    val selectedAsset = state.project.assets.firstOrNull { it.id in state.selectedAssetIds }
    val selectedRope = selectedAsset?.ropes?.getOrNull(state.selectedRopeIndex ?: -1)
    val paneModifier = if (enableFullScroll) {
        modifier
            .width(320.dp)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    } else {
        modifier
            .width(320.dp)
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
        Text("Eigenschaften", style = MaterialTheme.typography.titleMedium)
        CollapsibleHeader(
            title = "Projekt",
            expanded = showProjectSection,
            onToggle = { showProjectSection = !showProjectSection },
        )
        if (showProjectSection) {
            OutlinedTextField(
                value = projectNameInput,
                onValueChange = { projectNameInput = it },
                singleLine = true,
                label = { Text("Projektname") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onProjectNameChanged(projectNameInput) }, modifier = Modifier.fillMaxWidth()) {
                Text("Projektname übernehmen")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = onUndo, enabled = state.canUndo) { Text("Rückgängig") }
                Button(modifier = Modifier.weight(1f), onClick = onRedo, enabled = state.canRedo) { Text("Wiederholen") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = onSave) { Text("Speichern") }
                Button(modifier = Modifier.weight(1f), onClick = onLoad) { Text("Laden") }
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onResetProject) { Text("Projekt zurücksetzen") }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = onExportProjectFile) { Text("Export Datei") }
                Button(modifier = Modifier.weight(1f), onClick = onImportProjectFile) { Text("Import Datei") }
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onExportPng) { Text("Export PNG") }
        }
        HorizontalDivider()
        CollapsibleHeader(
            title = "Auswahl (${state.selectedAssetIds.size})",
            expanded = showSelectionSection,
            onToggle = { showSelectionSection = !showSelectionSection },
        )
        if (showSelectionSection && selectedAsset != null) {
            OutlinedTextField(
                value = selectedAsset.label,
                onValueChange = onLabelChanged,
                singleLine = true,
                label = { Text("Zeltname") },
                placeholder = { Text("z.B. Küchenzelt") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Rotation: ${selectedAsset.rotationDeg.toInt()}°", color = paneTextColor)
            Slider(
                value = selectedAsset.rotationDeg,
                valueRange = 0f..360f,
                onValueChange = onRotate,
            )
            Text("Höhe: ${selectedAsset.heightRange.minMeters}m - ${selectedAsset.heightRange.maxMeters}m", color = paneTextColor)
            Button(onClick = onToggleRopes) { Text("Seile an/aus") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onAddRope) { Text("Seil +") }
                Button(onClick = onRemoveRope, enabled = selectedAsset.ropes.isNotEmpty()) { Text("Seil -") }
            }
            Button(onClick = onRestoreDefaultRopes) { Text("Standard-Seile wiederherstellen") }
            Text("Seile gesamt: ${selectedAsset.ropes.size}", style = MaterialTheme.typography.bodySmall, color = paneMutedTextColor)
            if (selectedAsset.ropes.isNotEmpty()) {
                val ropeIndex = (state.selectedRopeIndex ?: 0).coerceIn(0, selectedAsset.ropes.lastIndex)
                if (state.selectedRopeIndex == null) {
                    LaunchedEffect(selectedAsset.id) {
                        onSelectRopeIndex(ropeIndex)
                    }
                }
                Text("Aktives Seil: #${ropeIndex + 1}", color = paneTextColor)
                Slider(
                    value = ropeIndex.toFloat(),
                    valueRange = 0f..selectedAsset.ropes.lastIndex.toFloat(),
                    onValueChange = { onSelectRopeIndex(it.toInt()) },
                )
            }
            if (selectedRope != null) {
                Text("Länge: ${"%.2f".format(selectedRope.lengthMeters)} m", style = MaterialTheme.typography.bodySmall, color = paneMutedTextColor)
                Slider(
                    value = selectedRope.lengthMeters,
                    valueRange = 0.3f..6.0f,
                    onValueChange = onRopeScaleChanged,
                )
                Text("Winkel: ${selectedRope.outwardAngleDeg.toInt()}°", style = MaterialTheme.typography.bodySmall, color = paneMutedTextColor)
                Slider(
                    value = selectedRope.outwardAngleDeg,
                    valueRange = -180f..180f,
                    onValueChange = onRopeAngleChanged,
                )
                Text("Anker X: ${"%.2f".format(selectedRope.localX)} m", style = MaterialTheme.typography.bodySmall, color = paneMutedTextColor)
                Slider(
                    value = selectedRope.localX,
                    valueRange = -6f..6f,
                    onValueChange = onRopeAnchorXChanged,
                )
                Text("Anker Y: ${"%.2f".format(selectedRope.localY)} m", style = MaterialTheme.typography.bodySmall, color = paneMutedTextColor)
                Slider(
                    value = selectedRope.localY,
                    valueRange = -6f..6f,
                    onValueChange = onRopeAnchorYChanged,
                )
            }
        } else if (showSelectionSection) {
            Text(
                "Kein Zelt ausgewählt.\nKlicke ein Zelt an oder wähle im Place-Modus ein Preset und setze es auf die Fläche.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onGroup, enabled = state.selectedAssetIds.size > 1) { Text("Gruppieren") }
            Button(onClick = onUngroup, enabled = state.selectedAssetIds.isNotEmpty()) { Text("Ungruppieren") }
        }
        Button(onClick = onDuplicate, enabled = state.selectedAssetIds.isNotEmpty()) {
            Text("Duplizieren")
        }
        Button(onClick = onDelete, enabled = state.selectedAssetIds.isNotEmpty()) {
            Text("Löschen")
        }
        if (state.measurementStart != null && state.measurementEnd != null) {
            val dist = GeometryEngine.distance(state.measurementStart, state.measurementEnd)
            Text("Messung: ${"%.2f".format(dist)} m", color = paneTextColor)
        }
        if (state.warnings.isNotEmpty()) {
            HorizontalDivider()
            CollapsibleHeader(
                title = "Warnungen (${state.warnings.size})",
                expanded = showWarningsSection,
                onToggle = { showWarningsSection = !showWarningsSection },
            )
            if (showWarningsSection) {
                if (enableFullScroll) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.warnings.take(8).forEach { warning ->
                            Text("• $warning", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    LazyColumn {
                        items(state.warnings.take(8)) { warning ->
                            Text("• $warning", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        }
    }
}

