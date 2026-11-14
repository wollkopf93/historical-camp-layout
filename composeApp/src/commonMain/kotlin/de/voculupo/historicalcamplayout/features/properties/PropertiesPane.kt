package de.voculupo.historicalcamplayout.features.properties

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.PlannerUiState
import de.voculupo.historicalcamplayout.assetDisplayLabel
import de.voculupo.historicalcamplayout.features.inventory.CollapsibleHeader
import de.voculupo.historicalcamplayout.presetDisplayName
import lagerplaner.composeapp.generated.resources.Res
import lagerplaner.composeapp.generated.resources.active_rope
import lagerplaner.composeapp.generated.resources.add_rope
import lagerplaner.composeapp.generated.resources.anchor_x
import lagerplaner.composeapp.generated.resources.anchor_y
import lagerplaner.composeapp.generated.resources.apply_area
import lagerplaner.composeapp.generated.resources.apply_project_name
import lagerplaner.composeapp.generated.resources.auto_setup
import lagerplaner.composeapp.generated.resources.delete
import lagerplaner.composeapp.generated.resources.duplicate
import lagerplaner.composeapp.generated.resources.export_file
import lagerplaner.composeapp.generated.resources.export_png
import lagerplaner.composeapp.generated.resources.group
import lagerplaner.composeapp.generated.resources.height_m
import lagerplaner.composeapp.generated.resources.height_range
import lagerplaner.composeapp.generated.resources.import_file
import lagerplaner.composeapp.generated.resources.load
import lagerplaner.composeapp.generated.resources.lock
import lagerplaner.composeapp.generated.resources.measurement
import lagerplaner.composeapp.generated.resources.no_selection
import lagerplaner.composeapp.generated.resources.project
import lagerplaner.composeapp.generated.resources.project_name
import lagerplaner.composeapp.generated.resources.properties
import lagerplaner.composeapp.generated.resources.redo
import lagerplaner.composeapp.generated.resources.remove_rope
import lagerplaner.composeapp.generated.resources.reset_project
import lagerplaner.composeapp.generated.resources.restore_default_ropes
import lagerplaner.composeapp.generated.resources.rope_angle
import lagerplaner.composeapp.generated.resources.rope_length
import lagerplaner.composeapp.generated.resources.ropes_total
import lagerplaner.composeapp.generated.resources.rotation_deg
import lagerplaner.composeapp.generated.resources.save
import lagerplaner.composeapp.generated.resources.selection_title
import lagerplaner.composeapp.generated.resources.tent_name
import lagerplaner.composeapp.generated.resources.tent_name_placeholder
import lagerplaner.composeapp.generated.resources.toggle_ropes
import lagerplaner.composeapp.generated.resources.undo
import lagerplaner.composeapp.generated.resources.ungroup
import lagerplaner.composeapp.generated.resources.unlock
import lagerplaner.composeapp.generated.resources.warnings_title
import lagerplaner.composeapp.generated.resources.width_m
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PropertiesPane(
    state: PlannerUiState,
    onProjectNameChange: (String) -> Unit,
    onParcelUpdate: (Float, Float) -> Unit,
    onResetProject: () -> Unit,
    onExportProjectFile: () -> Unit,
    onImportProjectFile: () -> Unit,
    onExportPng: () -> Unit,
    onLabelChange: (String) -> Unit,
    onRotate: (Float) -> Unit,
    onToggleRopes: () -> Unit,
    onToggleLock: () -> Unit,
    onAddRope: () -> Unit,
    onRemoveRope: () -> Unit,
    onRestoreDefaultRopes: () -> Unit,
    onSelectRopeIndex: (Int?) -> Unit,
    onRopeScaleChange: (Float) -> Unit,
    onRopeAngleChange: (Float) -> Unit,
    onRopeAnchorXChange: (Float) -> Unit,
    onRopeAnchorYChange: (Float) -> Unit,
    onSliderChangeFinish: () -> Unit,
    onGroup: () -> Unit,
    onUngroup: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enableFullScroll: Boolean = false,
    /** Phones have no top bar with a file menu: export/import/reset live here. */
    showFileActions: Boolean = true,
    fillWidth: Boolean = false,
) {
    val paneTextColor = MaterialTheme.colorScheme.onSurface
    val paneMutedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    var projectNameInput by remember(state.project.name) { mutableStateOf(state.project.name) }
    var widthInput by remember(state.project.parcel.widthMeters) {
        mutableStateOf(
            state.project.parcel.widthMeters
                .toString(),
        )
    }
    var heightInput by remember(state.project.parcel.heightMeters) {
        mutableStateOf(
            state.project.parcel.heightMeters
                .toString(),
        )
    }
    var showProjectSection by remember { mutableStateOf(true) }
    var showSelectionSection by remember { mutableStateOf(true) }
    var showWarningsSection by remember { mutableStateOf(false) }
    val selectedAsset = state.project.assets.firstOrNull { it.id in state.selectedAssetIds }
    val selectedPreset = state.presets.firstOrNull { it.id == selectedAsset?.presetId }
    val selectedPresetName = selectedPreset?.let { presetDisplayName(it) }
    val selectedRope = selectedAsset?.ropes?.getOrNull(state.selectedRopeIndex ?: -1)
    val currentSelectRopeIndex by rememberUpdatedState(onSelectRopeIndex)
    val paneModifier =
        if (enableFullScroll) {
            modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(320.dp))
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        } else {
            modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(320.dp))
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
            if (!fillWidth) Text(stringResource(Res.string.properties), style = MaterialTheme.typography.titleMedium)
            CollapsibleHeader(
                title = stringResource(Res.string.selection_title, state.selectedAssetIds.size),
                expanded = showSelectionSection,
                onToggle = { showSelectionSection = !showSelectionSection },
            )
            if (showSelectionSection && selectedAsset != null) {
                OutlinedTextField(
                    value =
                        if (selectedPreset != null && selectedPresetName != null) {
                            assetDisplayLabel(selectedAsset, selectedPreset, selectedPresetName)
                        } else {
                            selectedAsset.label
                        },
                    onValueChange = onLabelChange,
                    singleLine = true,
                    label = { Text(stringResource(Res.string.tent_name)) },
                    placeholder = { Text(stringResource(Res.string.tent_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(Res.string.rotation_deg, selectedAsset.rotationDeg.toInt()), color = paneTextColor)
                Slider(
                    value = selectedAsset.rotationDeg,
                    valueRange = 0f..360f,
                    onValueChange = onRotate,
                    onValueChangeFinished = onSliderChangeFinish,
                )
                val heights = selectedAsset.heightRange
                Text(
                    stringResource(Res.string.height_range, heights.minMeters.toString(), heights.maxMeters.toString()),
                    color = paneTextColor,
                )
                Button(onClick = onToggleRopes) { Text(stringResource(Res.string.toggle_ropes)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = onAddRope) { Text(stringResource(Res.string.add_rope)) }
                    Button(onClick = onRemoveRope, enabled = selectedAsset.ropes.isNotEmpty()) {
                        Text(stringResource(Res.string.remove_rope))
                    }
                }
                Button(onClick = onRestoreDefaultRopes) { Text(stringResource(Res.string.restore_default_ropes)) }
                Text(
                    stringResource(Res.string.ropes_total, selectedAsset.ropes.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = paneMutedTextColor,
                )
                if (selectedAsset.ropes.isNotEmpty()) {
                    val ropeIndex = (state.selectedRopeIndex ?: 0).coerceIn(0, selectedAsset.ropes.lastIndex)
                    if (state.selectedRopeIndex == null) {
                        LaunchedEffect(selectedAsset.id) {
                            currentSelectRopeIndex(ropeIndex)
                        }
                    }
                    Text(stringResource(Res.string.active_rope, ropeIndex + 1), color = paneTextColor)
                    Slider(
                        value = ropeIndex.toFloat(),
                        valueRange = 0f..selectedAsset.ropes.lastIndex.toFloat(),
                        onValueChange = { onSelectRopeIndex(it.toInt()) },
                    )
                }
                if (selectedRope != null) {
                    Text(
                        stringResource(Res.string.rope_length, "%.2f".format(selectedRope.lengthMeters)),
                        style = MaterialTheme.typography.bodySmall,
                        color = paneMutedTextColor,
                    )
                    Slider(
                        value = selectedRope.lengthMeters,
                        valueRange = 0.3f..6.0f,
                        onValueChange = onRopeScaleChange,
                        onValueChangeFinished = onSliderChangeFinish,
                    )
                    Text(
                        stringResource(Res.string.rope_angle, selectedRope.outwardAngleDeg.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = paneMutedTextColor,
                    )
                    Slider(
                        value = selectedRope.outwardAngleDeg,
                        valueRange = -180f..180f,
                        onValueChange = onRopeAngleChange,
                        onValueChangeFinished = onSliderChangeFinish,
                    )
                    Text(
                        stringResource(Res.string.anchor_x, "%.2f".format(selectedRope.localX)),
                        style = MaterialTheme.typography.bodySmall,
                        color = paneMutedTextColor,
                    )
                    Slider(
                        value = selectedRope.localX,
                        valueRange = -6f..6f,
                        onValueChange = onRopeAnchorXChange,
                        onValueChangeFinished = onSliderChangeFinish,
                    )
                    Text(
                        stringResource(Res.string.anchor_y, "%.2f".format(selectedRope.localY)),
                        style = MaterialTheme.typography.bodySmall,
                        color = paneMutedTextColor,
                    )
                    Slider(
                        value = selectedRope.localY,
                        valueRange = -6f..6f,
                        onValueChange = onRopeAnchorYChange,
                        onValueChangeFinished = onSliderChangeFinish,
                    )
                }
            } else if (showSelectionSection) {
                Text(
                    stringResource(Res.string.no_selection),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onGroup, enabled = state.selectedAssetIds.size > 1) { Text(stringResource(Res.string.group)) }
                Button(onClick = onUngroup, enabled = state.selectedAssetIds.isNotEmpty()) { Text(stringResource(Res.string.ungroup)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onDuplicate, enabled = state.selectedAssetIds.isNotEmpty()) {
                    Text(stringResource(Res.string.duplicate))
                }
                val selectedAssets = state.project.assets.filter { it.id in state.selectedAssetIds }
                val allLocked = selectedAssets.isNotEmpty() && selectedAssets.all { it.isLocked }
                Button(onClick = onToggleLock, enabled = state.selectedAssetIds.isNotEmpty()) {
                    Text(stringResource(if (allLocked) Res.string.unlock else Res.string.lock))
                }
            }
            Button(onClick = onDelete, enabled = state.selectedAssetIds.isNotEmpty()) {
                Text(stringResource(Res.string.delete))
            }
            if (state.measurementStart != null && state.measurementEnd != null) {
                val dist = GeometryEngine.distance(state.measurementStart, state.measurementEnd)
                Text(stringResource(Res.string.measurement, "%.2f".format(dist)), color = paneTextColor)
            }
            HorizontalDivider()
            CollapsibleHeader(
                title = stringResource(Res.string.project),
                expanded = showProjectSection,
                onToggle = { showProjectSection = !showProjectSection },
            )
            if (showProjectSection) {
                // Applied on Enter or when leaving the field: no extra button, one undo step per rename.
                OutlinedTextField(
                    value = projectNameInput,
                    onValueChange = { projectNameInput = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.project_name)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onProjectNameChange(projectNameInput) }),
                    modifier =
                        Modifier.fillMaxWidth().onFocusChanged {
                            if (!it.isFocused && projectNameInput != state.project.name) onProjectNameChange(projectNameInput)
                        },
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = widthInput,
                        onValueChange = { widthInput = it },
                        singleLine = true,
                        label = { Text(stringResource(Res.string.width_m)) },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { heightInput = it },
                        singleLine = true,
                        label = { Text(stringResource(Res.string.height_m)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val w = widthInput.replace(',', '.').toFloatOrNull()
                        val h = heightInput.replace(',', '.').toFloatOrNull()
                        if (w != null && h != null) onParcelUpdate(w, h)
                    },
                ) { Text(stringResource(Res.string.apply_area)) }
                if (showFileActions) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onExportProjectFile,
                        ) { Text(stringResource(Res.string.export_file)) }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onImportProjectFile,
                        ) { Text(stringResource(Res.string.import_file)) }
                    }
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onExportPng) { Text(stringResource(Res.string.export_png)) }
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onResetProject) { Text(stringResource(Res.string.reset_project)) }
                }
            }
            if (state.warnings.isNotEmpty()) {
                HorizontalDivider()
                CollapsibleHeader(
                    title = stringResource(Res.string.warnings_title, state.warnings.size),
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
