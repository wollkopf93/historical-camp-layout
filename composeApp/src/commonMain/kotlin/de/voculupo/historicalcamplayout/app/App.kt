package de.voculupo.historicalcamplayout.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.voculupo.historicalcamplayout.PlannerToolMode
import de.voculupo.historicalcamplayout.PlannerUiState
import de.voculupo.historicalcamplayout.getPlatform
import de.voculupo.historicalcamplayout.features.inventory.InventoryPane
import de.voculupo.historicalcamplayout.features.planner.canvas.PlannerCanvas
import de.voculupo.historicalcamplayout.features.planner.state.PlannerViewModel
import de.voculupo.historicalcamplayout.features.properties.PropertiesPane
import kotlinx.coroutines.delay

private data class PresetDragState(
    val presetId: String,
    val label: String,
    val rootPosition: Offset,
)

data class PresetDropRequest(
    val presetId: String,
    val rootPosition: Offset,
    val token: Long = kotlin.random.Random.nextLong(),
)

private val MedievalLightColorScheme = lightColorScheme(
    primary = Color(0xFF6B4A2A),
    onPrimary = Color(0xFFFDF6E8),
    primaryContainer = Color(0xFFE6D5B8),
    onPrimaryContainer = Color(0xFF3C2A19),
    secondary = Color(0xFF8A6B3D),
    onSecondary = Color(0xFFFDF6E8),
    secondaryContainer = Color(0xFFEFE3CF),
    onSecondaryContainer = Color(0xFF3E311E),
    tertiary = Color(0xFF6A5A3C),
    onTertiary = Color(0xFFF6EFE1),
    background = Color(0xFFEAE1CF),
    onBackground = Color(0xFF2F261A),
    surface = Color(0xFFF2EADB),
    onSurface = Color(0xFF2F261A),
    inverseSurface = Color(0xFF5A4832),
    inverseOnSurface = Color(0xFFF8F0E2),
    outline = Color(0xFF8A7A62),
    outlineVariant = Color(0xFFC4B59D),
    error = Color(0xFF8B3A32),
    onError = Color(0xFFFFF5F3),
)

private val MedievalDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0B38A),
    onPrimary = Color(0xFF3A2817),
    primaryContainer = Color(0xFF5A4126),
    onPrimaryContainer = Color(0xFFF0DFC5),
    secondary = Color(0xFFC3A677),
    onSecondary = Color(0xFF3D2E1B),
    secondaryContainer = Color(0xFF564128),
    onSecondaryContainer = Color(0xFFEEDFC3),
    tertiary = Color(0xFFC4B089),
    onTertiary = Color(0xFF362B1B),
    background = Color(0xFF2A241C),
    onBackground = Color(0xFFE9DEC8),
    surface = Color(0xFF332C23),
    onSurface = Color(0xFFE9DEC8),
    inverseSurface = Color(0xFFE7D8BF),
    inverseOnSurface = Color(0xFF342B21),
    outline = Color(0xFF9B8B72),
    outlineVariant = Color(0xFF5D5142),
    error = Color(0xFFCF8A82),
    onError = Color(0xFF3F1613),
)

@Composable
@Preview
fun App() {
    val viewModel = remember { PlannerViewModel() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val rootFocusRequester = remember { FocusRequester() }
    var leftPaneWidthDp by remember { mutableStateOf(340f) }
    var shiftPressed by remember { mutableStateOf(false) }
    var dragState by remember { mutableStateOf<PresetDragState?>(null) }
    var dropRequest by remember { mutableStateOf<PresetDropRequest?>(null) }
    var suppressTapTemporarily by remember { mutableStateOf(false) }

    LaunchedEffect(suppressTapTemporarily) {
        if (suppressTapTemporarily) {
            delay(180)
            suppressTapTemporarily = false
        }
    }
    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    val systemDarkTheme = isSystemInDarkTheme()
    var darkThemeEnabled by remember { mutableStateOf(systemDarkTheme) }
    val colorScheme = if (darkThemeEnabled) MedievalDarkColorScheme else MedievalLightColorScheme
    val isAndroid = remember { getPlatform().name.startsWith("Android") }

    MaterialTheme(
        colorScheme = colorScheme,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    when {
                        keyEvent.key == Key.ShiftLeft || keyEvent.key == Key.ShiftRight -> {
                            shiftPressed = keyEvent.type == KeyEventType.KeyDown
                            false
                        }
                        keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.Delete || keyEvent.key == Key.Escape) -> {
                            if (state.selectedAssetIds.isNotEmpty()) {
                                viewModel.deleteSelection()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                },
        ) {
            val useCompactShell = AppUiLogic.shouldUseCompactShell(isAndroid = isAndroid)
            if (useCompactShell) {
                CompactLayout(
                    state = state,
                    shiftPressed = false,
                    dropRequest = dropRequest,
                    suppressTapTemporarily = suppressTapTemporarily,
                    onDropHandled = { dropRequest = null },
                    onTool = viewModel::setTool,
                    onPresetSelected = { id ->
                        viewModel.selectPreset(id)
                        viewModel.setTool(AppUiLogic.toolForCompactPresetSelection())
                    },
                    onPresetDragStart = { id, label, rootPosition -> dragState = PresetDragState(id, label, rootPosition) },
                    onPresetDrag = { rootPosition -> dragState = dragState?.copy(rootPosition = rootPosition) },
                    onPresetDragEnd = { id, rootPosition ->
                        suppressTapTemporarily = true
                        dropRequest = PresetDropRequest(id, rootPosition)
                        dragState = null
                    },
                    onPresetDragCancel = { dragState = null },
                    onPlace = viewModel::placeAssetAt,
                    onPlacePresetAt = viewModel::placePresetAt,
                    onSelection = viewModel::setSelection,
                    onSelectionToggle = viewModel::toggleSelection,
                    onRopeSelected = viewModel::selectRopeIndex,
                    onRopeStakeDragged = viewModel::updateRopeStakeWorld,
                    onRopeAnchorDragged = viewModel::updateRopeAnchorWorld,
                    onMeasureTap = viewModel::onMeasureTap,
                    onBlockZoneTap = viewModel::onBlockZoneTap,
                    onZoomIn = viewModel::zoomIn,
                    onZoomOut = viewModel::zoomOut,
                    onZoomContinuous = viewModel::setZoomLive,
                    onRotateSelectedBy = viewModel::rotateSelected,
                    onDrag = viewModel::dragSelection,
                    onDragEnd = viewModel::finishInteractionWithGridSnap,
                    onResetProject = viewModel::resetProject,
                    onParcelUpdate = viewModel::updateParcel,
                    onZoomReset = viewModel::resetZoom,
                    onClearZones = viewModel::clearBlockZones,
                    onProjectNameChanged = viewModel::setProjectName,
                    onSave = viewModel::saveProject,
                    onLoad = viewModel::loadProject,
                    onExportProjectFile = viewModel::exportProjectFile,
                    onImportProjectFile = viewModel::importProjectFile,
                    onExportPng = viewModel::exportPng,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onLabelChanged = viewModel::setSelectedLabel,
                    onRotate = viewModel::setSelectedRotation,
                    onToggleRopes = viewModel::toggleRopesForSelected,
                    onAddRope = viewModel::addRopeToSelected,
                    onRemoveRope = viewModel::removeRopeFromSelected,
                    onRestoreDefaultRopes = viewModel::restoreDefaultRopesForSelected,
                    onSelectRopeIndex = viewModel::selectRopeIndex,
                    onRopeScaleChanged = viewModel::setSelectedRopeLengthScale,
                    onRopeAngleChanged = viewModel::setSelectedRopeAngleOffset,
                    onRopeAnchorXChanged = viewModel::setSelectedRopeAnchorX,
                    onRopeAnchorYChanged = viewModel::setSelectedRopeAnchorY,
                    onGroup = viewModel::groupSelection,
                    onUngroup = viewModel::ungroupSelection,
                    onDuplicate = viewModel::duplicateSelection,
                    onDelete = viewModel::deleteSelection,
                )
            } else {
                ExpandedLayout(
                    state = state,
                    density = density,
                    leftPaneWidthDp = leftPaneWidthDp,
                    onLeftPaneWidthDpChanged = { leftPaneWidthDp = it },
                    darkThemeEnabled = darkThemeEnabled,
                    onToggleTheme = { darkThemeEnabled = !darkThemeEnabled },
                    shiftPressed = shiftPressed,
                    dropRequest = dropRequest,
                    suppressTapTemporarily = suppressTapTemporarily,
                    onDropHandled = { dropRequest = null },
                    onTool = viewModel::setTool,
                    onPresetSelected = viewModel::selectPreset,
                    onPresetDragStart = { id, label, rootPosition -> dragState = PresetDragState(id, label, rootPosition) },
                    onPresetDrag = { rootPosition -> dragState = dragState?.copy(rootPosition = rootPosition) },
                    onPresetDragEnd = { id, rootPosition ->
                        suppressTapTemporarily = true
                        dropRequest = PresetDropRequest(id, rootPosition)
                        dragState = null
                    },
                    onPresetDragCancel = { dragState = null },
                    onPlace = viewModel::placeAssetAt,
                    onPlacePresetAt = viewModel::placePresetAt,
                    onSelection = viewModel::setSelection,
                    onSelectionToggle = viewModel::toggleSelection,
                    onRopeSelected = viewModel::selectRopeIndex,
                    onRopeStakeDragged = viewModel::updateRopeStakeWorld,
                    onRopeAnchorDragged = viewModel::updateRopeAnchorWorld,
                    onMeasureTap = viewModel::onMeasureTap,
                    onBlockZoneTap = viewModel::onBlockZoneTap,
                    onZoomIn = viewModel::zoomIn,
                    onZoomOut = viewModel::zoomOut,
                    onZoomContinuous = viewModel::setZoomLive,
                    onRotateSelectedBy = viewModel::rotateSelected,
                    onDrag = viewModel::dragSelection,
                    onDragEnd = viewModel::finishInteractionWithGridSnap,
                    onResetProject = viewModel::resetProject,
                    onParcelUpdate = viewModel::updateParcel,
                    onZoomReset = viewModel::resetZoom,
                    onClearZones = viewModel::clearBlockZones,
                    onProjectNameChanged = viewModel::setProjectName,
                    onSave = viewModel::saveProject,
                    onLoad = viewModel::loadProject,
                    onExportProjectFile = viewModel::exportProjectFile,
                    onImportProjectFile = viewModel::importProjectFile,
                    onExportPng = viewModel::exportPng,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onLabelChanged = viewModel::setSelectedLabel,
                    onRotate = viewModel::setSelectedRotation,
                    onToggleRopes = viewModel::toggleRopesForSelected,
                    onAddRope = viewModel::addRopeToSelected,
                    onRemoveRope = viewModel::removeRopeFromSelected,
                    onRestoreDefaultRopes = viewModel::restoreDefaultRopesForSelected,
                    onSelectRopeIndex = viewModel::selectRopeIndex,
                    onRopeScaleChanged = viewModel::setSelectedRopeLengthScale,
                    onRopeAngleChanged = viewModel::setSelectedRopeAngleOffset,
                    onRopeAnchorXChanged = viewModel::setSelectedRopeAnchorX,
                    onRopeAnchorYChanged = viewModel::setSelectedRopeAnchorY,
                    onGroup = viewModel::groupSelection,
                    onUngroup = viewModel::ungroupSelection,
                    onDuplicate = viewModel::duplicateSelection,
                    onDelete = viewModel::deleteSelection,
                )
            }
            dragState?.let { drag ->
                Card(
                    modifier = Modifier
                        .padding(start = with(density) { drag.rootPosition.x.toDp() }, top = with(density) { drag.rootPosition.y.toDp() })
                        .border(1.dp, MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        "➕ ${drag.label}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedLayout(
    state: PlannerUiState,
    density: androidx.compose.ui.unit.Density,
    leftPaneWidthDp: Float,
    onLeftPaneWidthDpChanged: (Float) -> Unit,
    darkThemeEnabled: Boolean,
    onToggleTheme: () -> Unit,
    shiftPressed: Boolean,
    dropRequest: PresetDropRequest?,
    suppressTapTemporarily: Boolean,
    onDropHandled: () -> Unit,
    onTool: (PlannerToolMode) -> Unit,
    onPresetSelected: (String) -> Unit,
    onPresetDragStart: (String, String, Offset) -> Unit,
    onPresetDrag: (Offset) -> Unit,
    onPresetDragEnd: (String, Offset) -> Unit,
    onPresetDragCancel: () -> Unit,
    onPlace: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onPlacePresetAt: (String, de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onSelection: (Set<String>) -> Unit,
    onSelectionToggle: (String?) -> Unit,
    onRopeSelected: (Int?) -> Unit,
    onRopeStakeDragged: (Int, de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onRopeAnchorDragged: (Int, de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onMeasureTap: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onBlockZoneTap: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomContinuous: (Float) -> Unit,
    onRotateSelectedBy: (Float) -> Unit,
    onDrag: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onDragEnd: () -> Unit,
    onResetProject: () -> Unit,
    onParcelUpdate: (Float, Float) -> Unit,
    onZoomReset: () -> Unit,
    onClearZones: () -> Unit,
    onProjectNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
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
    var showWarningsPanel by remember { mutableStateOf(true) }
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            InventoryPane(
                modifier = Modifier.width(leftPaneWidthDp.dp),
            enableFullScroll = true,
                state = state,
                onTool = onTool,
                onPresetSelected = onPresetSelected,
                onPresetDragStart = onPresetDragStart,
                onPresetDrag = onPresetDrag,
                onPresetDragEnd = onPresetDragEnd,
                onPresetDragCancel = onPresetDragCancel,
                onParcelUpdate = onParcelUpdate,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onZoomReset = onZoomReset,
                onClearZones = onClearZones,
            )
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            val dragDp = with(density) { dragAmount.toDp().value }
                            onLeftPaneWidthDpChanged((leftPaneWidthDp + dragDp).coerceIn(280f, 520f))
                        }
                    },
            )
            PlannerCanvas(
                modifier = Modifier.weight(1f),
                state = state,
                shiftPressed = shiftPressed,
                allowSingleFingerCanvasPan = true,
                allowMultiTouchCanvasPan = true,
                dropRequest = dropRequest,
                onDropHandled = onDropHandled,
                suppressTapTemporarily = suppressTapTemporarily,
                onPlace = onPlace,
                onPlacePresetAt = onPlacePresetAt,
                onSelection = onSelection,
                onSelectionToggle = onSelectionToggle,
                onRopeSelected = onRopeSelected,
                onRopeStakeDragged = onRopeStakeDragged,
                onRopeAnchorDragged = onRopeAnchorDragged,
                onMeasureTap = onMeasureTap,
                onBlockZoneTap = onBlockZoneTap,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onZoomContinuous = onZoomContinuous,
                onRotateSelectedBy = onRotateSelectedBy,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
            )
            PropertiesPane(
                enableFullScroll = true,
                state = state,
                onProjectNameChanged = onProjectNameChanged,
                onSave = onSave,
                onLoad = onLoad,
                onResetProject = onResetProject,
                onExportProjectFile = onExportProjectFile,
                onImportProjectFile = onImportProjectFile,
                onExportPng = onExportPng,
                onUndo = onUndo,
                onRedo = onRedo,
                onLabelChanged = onLabelChanged,
                onRotate = onRotate,
                onToggleRopes = onToggleRopes,
                onAddRope = onAddRope,
                onRemoveRope = onRemoveRope,
                onRestoreDefaultRopes = onRestoreDefaultRopes,
                onSelectRopeIndex = onSelectRopeIndex,
                onRopeScaleChanged = onRopeScaleChanged,
                onRopeAngleChanged = onRopeAngleChanged,
                onRopeAnchorXChanged = onRopeAnchorXChanged,
                onRopeAnchorYChanged = onRopeAnchorYChanged,
                onGroup = onGroup,
                onUngroup = onUngroup,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Card(shape = RoundedCornerShape(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (state.warnings.isNotEmpty()) {
                        IconButton(onClick = { showWarningsPanel = !showWarningsPanel }) {
                            Text("⚠")
                        }
                    }
                    IconButton(onClick = onToggleTheme) {
                        Text(if (darkThemeEnabled) "☀" else "🌙")
                    }
                }
            }
        }

        if (state.warnings.isNotEmpty() && showWarningsPanel) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                    .width(560.dp)
                    .heightIn(max = 220.dp),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Warnungen (${state.warnings.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    state.warnings.take(8).forEach { msg ->
                        Text("• $msg", style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.warnings.size > 8) {
                        Text("… ${state.warnings.size - 8} weitere", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CompactLayout(
    state: PlannerUiState,
    shiftPressed: Boolean,
    dropRequest: PresetDropRequest?,
    suppressTapTemporarily: Boolean,
    onDropHandled: () -> Unit,
    onTool: (PlannerToolMode) -> Unit,
    onPresetSelected: (String) -> Unit,
    onPresetDragStart: (String, String, Offset) -> Unit,
    onPresetDrag: (Offset) -> Unit,
    onPresetDragEnd: (String, Offset) -> Unit,
    onPresetDragCancel: () -> Unit,
    onPlace: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onPlacePresetAt: (String, de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onSelection: (Set<String>) -> Unit,
    onSelectionToggle: (String?) -> Unit,
    onRopeSelected: (Int?) -> Unit,
    onRopeStakeDragged: (Int, de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onRopeAnchorDragged: (Int, de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onMeasureTap: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onBlockZoneTap: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomContinuous: (Float) -> Unit,
    onRotateSelectedBy: (Float) -> Unit,
    onDrag: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onDragEnd: () -> Unit,
    onResetProject: () -> Unit,
    onParcelUpdate: (Float, Float) -> Unit,
    onZoomReset: () -> Unit,
    onClearZones: () -> Unit,
    onProjectNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
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
    var showInventorySidebar by remember { mutableStateOf(false) }
    var showPropertiesSidebar by remember { mutableStateOf(false) }
    var showWarningsPanel by remember { mutableStateOf(true) }
    val currentTool = state.currentTool

    Box(modifier = Modifier.fillMaxSize()) {
        PlannerCanvas(
            modifier = Modifier.fillMaxSize().padding(bottom = 64.dp),
            state = state,
            shiftPressed = shiftPressed,
            allowSingleFingerCanvasPan = true,
            allowMultiTouchCanvasPan = true,
            dropRequest = dropRequest,
            onDropHandled = onDropHandled,
            suppressTapTemporarily = suppressTapTemporarily,
            onPlace = onPlace,
            onPlacePresetAt = onPlacePresetAt,
            onSelection = onSelection,
            onSelectionToggle = onSelectionToggle,
            onRopeSelected = onRopeSelected,
            onRopeStakeDragged = onRopeStakeDragged,
            onRopeAnchorDragged = onRopeAnchorDragged,
            onMeasureTap = onMeasureTap,
            onBlockZoneTap = onBlockZoneTap,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            onZoomContinuous = onZoomContinuous,
            onRotateSelectedBy = onRotateSelectedBy,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (state.warnings.isNotEmpty()) {
                        IconButton(
                            onClick = { showWarningsPanel = !showWarningsPanel },
                        ) {
                            Text("⚠")
                        }
                    }
                    IconButton(
                        onClick = {
                            showPropertiesSidebar = !showPropertiesSidebar
                            if (showPropertiesSidebar) showInventorySidebar = false
                        },
                    ) {
                        Text("⚙")
                    }
                }
            }
        }

        if (state.warnings.isNotEmpty() && showWarningsPanel && !showPropertiesSidebar) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 56.dp, end = 8.dp)
                    .width(340.dp)
                    .heightIn(max = 220.dp)
                    .zIndex(2.5f),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Warnungen (${state.warnings.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF8A6B00),
                    )
                    state.warnings.take(6).forEach { msg ->
                        Text("• $msg", style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.warnings.size > 6) {
                        Text("… ${state.warnings.size - 6} weitere", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (showInventorySidebar || showPropertiesSidebar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
                    .pointerInput(showInventorySidebar, showPropertiesSidebar) {
                        detectTapGestures {
                            showInventorySidebar = false
                            showPropertiesSidebar = false
                        }
                    },
            )
        }

        if (showInventorySidebar) {
            Card(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .statusBarsPadding()
                    .fillMaxHeight()
                    .width(320.dp)
                    .zIndex(3f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Inventory", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showInventorySidebar = false }) { Text("Close") }
                }
                InventoryPane(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    enableFullScroll = true,
                    state = state,
                    onTool = onTool,
                    onPresetSelected = onPresetSelected,
                    onPresetDragStart = onPresetDragStart,
                    onPresetDrag = onPresetDrag,
                    onPresetDragEnd = { id, rootPosition ->
                        showInventorySidebar = false
                        onPresetDragEnd(id, rootPosition)
                    },
                    onPresetDragCancel = {
                        showInventorySidebar = false
                        onPresetDragCancel()
                    },
                    onParcelUpdate = onParcelUpdate,
                    onZoomIn = onZoomIn,
                    onZoomOut = onZoomOut,
                    onZoomReset = onZoomReset,
                    onClearZones = onClearZones,
                )
            }
        }

        if (showPropertiesSidebar) {
            Card(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .statusBarsPadding()
                    .fillMaxHeight()
                    .width(320.dp)
                    .zIndex(3f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Properties", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showPropertiesSidebar = false }) { Text("Close") }
                }
                PropertiesPane(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    enableFullScroll = true,
                    state = state,
                    onProjectNameChanged = onProjectNameChanged,
                    onSave = onSave,
                    onLoad = onLoad,
                    onResetProject = onResetProject,
                    onExportProjectFile = onExportProjectFile,
                    onImportProjectFile = onImportProjectFile,
                    onExportPng = onExportPng,
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onLabelChanged = onLabelChanged,
                    onRotate = onRotate,
                    onToggleRopes = onToggleRopes,
                    onAddRope = onAddRope,
                    onRemoveRope = onRemoveRope,
                    onRestoreDefaultRopes = onRestoreDefaultRopes,
                    onSelectRopeIndex = onSelectRopeIndex,
                    onRopeScaleChanged = onRopeScaleChanged,
                    onRopeAngleChanged = onRopeAngleChanged,
                    onRopeAnchorXChanged = onRopeAnchorXChanged,
                    onRopeAnchorYChanged = onRopeAnchorYChanged,
                    onGroup = onGroup,
                    onUngroup = onUngroup,
                    onDuplicate = onDuplicate,
                    onDelete = onDelete,
                )
            }
        }

        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 104.dp),
            text = { Text("Inventory") },
            icon = { Text("📦") },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = {
                showInventorySidebar = !showInventorySidebar
                if (showInventorySidebar) showPropertiesSidebar = false
            },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 400.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    NavigationBarItem(
                        selected = currentTool == PlannerToolMode.Select,
                        onClick = { onTool(PlannerToolMode.Select) },
                        icon = { Text("S") },
                        label = { Text("Select") },
                    )
                    NavigationBarItem(
                        selected = currentTool == PlannerToolMode.Measure,
                        onClick = { onTool(PlannerToolMode.Measure) },
                        icon = { Text("M") },
                        label = { Text("Measure") },
                    )
                    NavigationBarItem(
                        selected = currentTool == PlannerToolMode.BlockZone,
                        onClick = { onTool(PlannerToolMode.BlockZone) },
                        icon = { Text("B") },
                        label = { Text("Block") },
                    )
                }
            }
        }
    }
}


