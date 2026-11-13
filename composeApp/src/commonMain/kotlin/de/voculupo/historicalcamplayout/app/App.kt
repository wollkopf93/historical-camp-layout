package de.voculupo.historicalcamplayout.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.voculupo.historicalcamplayout.AutoSetupRequest
import de.voculupo.historicalcamplayout.PlannerToolMode
import de.voculupo.historicalcamplayout.PlannerUiState
import de.voculupo.historicalcamplayout.features.inventory.InventoryPane
import de.voculupo.historicalcamplayout.features.planner.canvas.PlannerCanvas
import de.voculupo.historicalcamplayout.features.planner.state.PlannerViewModel
import de.voculupo.historicalcamplayout.features.properties.PropertiesPane
import de.voculupo.historicalcamplayout.getPlatform
import de.voculupo.historicalcamplayout.platform.PlatformBackHandler
import kotlinx.coroutines.delay
import lagerplaner.composeapp.generated.resources.Res
import lagerplaner.composeapp.generated.resources.close
import lagerplaner.composeapp.generated.resources.delete
import lagerplaner.composeapp.generated.resources.duplicate
import lagerplaner.composeapp.generated.resources.edit_selection
import lagerplaner.composeapp.generated.resources.ic_block
import lagerplaner.composeapp.generated.resources.ic_close
import lagerplaner.composeapp.generated.resources.ic_copy
import lagerplaner.composeapp.generated.resources.ic_dark_mode
import lagerplaner.composeapp.generated.resources.ic_delete
import lagerplaner.composeapp.generated.resources.ic_edit
import lagerplaner.composeapp.generated.resources.ic_light_mode
import lagerplaner.composeapp.generated.resources.ic_lock
import lagerplaner.composeapp.generated.resources.ic_lock_open
import lagerplaner.composeapp.generated.resources.ic_measure
import lagerplaner.composeapp.generated.resources.ic_redo
import lagerplaner.composeapp.generated.resources.ic_rotate_left
import lagerplaner.composeapp.generated.resources.ic_rotate_right
import lagerplaner.composeapp.generated.resources.ic_select
import lagerplaner.composeapp.generated.resources.ic_settings
import lagerplaner.composeapp.generated.resources.ic_tent
import lagerplaner.composeapp.generated.resources.ic_undo
import lagerplaner.composeapp.generated.resources.ic_warning
import lagerplaner.composeapp.generated.resources.inventory
import lagerplaner.composeapp.generated.resources.lock
import lagerplaner.composeapp.generated.resources.more_count
import lagerplaner.composeapp.generated.resources.properties
import lagerplaner.composeapp.generated.resources.redo
import lagerplaner.composeapp.generated.resources.rotate_left
import lagerplaner.composeapp.generated.resources.rotate_right
import lagerplaner.composeapp.generated.resources.toggle_theme
import lagerplaner.composeapp.generated.resources.tool_block_zone
import lagerplaner.composeapp.generated.resources.tool_measure
import lagerplaner.composeapp.generated.resources.tool_select
import lagerplaner.composeapp.generated.resources.undo
import lagerplaner.composeapp.generated.resources.unlock
import lagerplaner.composeapp.generated.resources.warnings
import lagerplaner.composeapp.generated.resources.warnings_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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

// Heraldic palette: madder red, woad blue and ochre gold on parchment; iron-gall ink for text.
private val MedievalLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF8C2F1E),
        onPrimary = Color(0xFFFFF8EC),
        primaryContainer = Color(0xFFF6DACB),
        onPrimaryContainer = Color(0xFF3A0E05),
        secondary = Color(0xFF2F4A6B),
        onSecondary = Color(0xFFF4F7FB),
        secondaryContainer = Color(0xFFD6E1EE),
        onSecondaryContainer = Color(0xFF0F1F33),
        tertiary = Color(0xFF8A6417),
        onTertiary = Color(0xFFFFF8EC),
        tertiaryContainer = Color(0xFFF3DFAE),
        onTertiaryContainer = Color(0xFF2C1E00),
        background = Color(0xFFF1E6CF),
        onBackground = Color(0xFF2B2117),
        surface = Color(0xFFF7EEDC),
        onSurface = Color(0xFF2B2117),
        surfaceVariant = Color(0xFFE8DABF),
        onSurfaceVariant = Color(0xFF4E4232),
        surfaceContainerLowest = Color(0xFFFFF9EE),
        surfaceContainerLow = Color(0xFFF8EEDB),
        surfaceContainer = Color(0xFFF2E6CE),
        surfaceContainerHigh = Color(0xFFECDFC4),
        surfaceContainerHighest = Color(0xFFE6D7BA),
        inverseSurface = Color(0xFF3B3026),
        inverseOnSurface = Color(0xFFF8EEDC),
        outline = Color(0xFF857560),
        outlineVariant = Color(0xFFD3C4A8),
        error = Color(0xFFA3281B),
        onError = Color(0xFFFFF5F3),
    )

// Candle-lit night: warm soot browns, same heraldic accents lifted for contrast.
private val MedievalDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFF0A48F),
        onPrimary = Color(0xFF4F1408),
        primaryContainer = Color(0xFF6E2516),
        onPrimaryContainer = Color(0xFFFFDAD0),
        secondary = Color(0xFFA9C2E0),
        onSecondary = Color(0xFF13283F),
        secondaryContainer = Color(0xFF2C4260),
        onSecondaryContainer = Color(0xFFD5E3F5),
        tertiary = Color(0xFFE5C26B),
        onTertiary = Color(0xFF3E2E00),
        tertiaryContainer = Color(0xFF5A4300),
        onTertiaryContainer = Color(0xFFFFE08E),
        background = Color(0xFF1C1712),
        onBackground = Color(0xFFEDE1CC),
        surface = Color(0xFF1F1A14),
        onSurface = Color(0xFFEDE1CC),
        surfaceVariant = Color(0xFF4C4234),
        onSurfaceVariant = Color(0xFFD3C4A8),
        surfaceContainerLowest = Color(0xFF15110D),
        surfaceContainerLow = Color(0xFF241E17),
        surfaceContainer = Color(0xFF29221A),
        surfaceContainerHigh = Color(0xFF332B22),
        surfaceContainerHighest = Color(0xFF3E352B),
        inverseSurface = Color(0xFFEDE1CC),
        inverseOnSurface = Color(0xFF342B21),
        outline = Color(0xFF9C8D76),
        outlineVariant = Color(0xFF4C4234),
        error = Color(0xFFFFB4A8),
        onError = Color(0xFF690005),
    )

@Composable
fun App(
    modifier: Modifier = Modifier,
    viewModel: PlannerViewModel = viewModel { PlannerViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val rootFocusRequester = remember { FocusRequester() }
    var leftPaneWidthDp by remember { mutableFloatStateOf(340f) }
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
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .focusRequester(rootFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.ShiftLeft || keyEvent.key == Key.ShiftRight) {
                            shiftPressed = keyEvent.type == KeyEventType.KeyDown
                        }
                        false
                    }
                    // Not a preview handler: a focused text field must get Delete first (it deletes a character there).
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val ctrl = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                        val hasSelection = state.selectedAssetIds.isNotEmpty()
                        val key = keyEvent.key
                        val rotateStep = if (keyEvent.isShiftPressed) -15f else 15f
                        when {
                            ctrl && key == Key.Z && keyEvent.isShiftPressed -> viewModel.redo()
                            ctrl && key == Key.Z -> viewModel.undo()
                            ctrl && key == Key.Y -> viewModel.redo()
                            ctrl && key == Key.D && hasSelection -> viewModel.duplicateSelection()
                            ctrl -> return@onKeyEvent false
                            key == Key.Delete && hasSelection -> viewModel.deleteSelection()
                            key == Key.R && hasSelection -> viewModel.rotateSelected(rotateStep).also { viewModel.commitHistory() }
                            key == Key.L && hasSelection -> viewModel.toggleLockSelected()
                            key == Key.Escape -> viewModel.setSelection(emptySet())
                            else -> return@onKeyEvent false
                        }
                        true
                    },
        ) {
            val useCompactShell = AppUiLogic.shouldUseCompactShell(isAndroid = isAndroid, widthDp = maxWidth.value)
            if (useCompactShell) {
                CompactLayout(
                    state = state,
                    darkThemeEnabled = darkThemeEnabled,
                    onToggleTheme = { darkThemeEnabled = !darkThemeEnabled },
                    shiftPressed = false,
                    dropRequest = dropRequest,
                    suppressTapTemporarily = suppressTapTemporarily,
                    onDropHandle = { dropRequest = null },
                    onTool = viewModel::setTool,
                    onPresetSelect = { id ->
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
                    onRopeSelect = viewModel::selectRopeIndex,
                    onRopeStakeDrag = viewModel::updateRopeStakeWorld,
                    onRopeAnchorDrag = viewModel::updateRopeAnchorWorld,
                    onMeasureTap = viewModel::onMeasureTap,
                    onBlockZoneTap = viewModel::onBlockZoneTap,
                    onZoomIn = viewModel::zoomIn,
                    onZoomOut = viewModel::zoomOut,
                    onZoomContinuous = viewModel::setZoomLive,
                    onRotateSelectedBy = viewModel::rotateSelected,
                    onDrag = viewModel::dragSelection,
                    onDragEnd = viewModel::finishInteractionWithGridSnap,
                    onResetProject = viewModel::resetProject,
                    onAutoSetup = viewModel::autoSetup,
                    onParcelUpdate = viewModel::updateParcel,
                    onZoomReset = viewModel::resetZoom,
                    onClearZones = viewModel::clearBlockZones,
                    onProjectNameChange = viewModel::setProjectName,
                    onSave = viewModel::saveProject,
                    onLoad = viewModel::loadProject,
                    onExportProjectFile = viewModel::exportProjectFile,
                    onImportProjectFile = viewModel::importProjectFile,
                    onExportPng = viewModel::exportPng,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onLabelChange = viewModel::setSelectedLabel,
                    onRotate = viewModel::setSelectedRotation,
                    onToggleRopes = viewModel::toggleRopesForSelected,
                    onToggleLock = viewModel::toggleLockSelected,
                    onAddRope = viewModel::addRopeToSelected,
                    onRemoveRope = viewModel::removeRopeFromSelected,
                    onRestoreDefaultRopes = viewModel::restoreDefaultRopesForSelected,
                    onSelectRopeIndex = viewModel::selectRopeIndex,
                    onRopeScaleChange = viewModel::setSelectedRopeLengthScale,
                    onRopeAngleChange = viewModel::setSelectedRopeAngleOffset,
                    onRopeAnchorXChange = viewModel::setSelectedRopeAnchorX,
                    onRopeAnchorYChange = viewModel::setSelectedRopeAnchorY,
                    onSliderChangeFinished = viewModel::commitHistory,
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
                    onLeftPaneWidthDpChange = { leftPaneWidthDp = it },
                    darkThemeEnabled = darkThemeEnabled,
                    onToggleTheme = { darkThemeEnabled = !darkThemeEnabled },
                    shiftPressed = shiftPressed,
                    dropRequest = dropRequest,
                    suppressTapTemporarily = suppressTapTemporarily,
                    onDropHandle = { dropRequest = null },
                    onTool = viewModel::setTool,
                    onPresetSelect = viewModel::selectPreset,
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
                    onRopeSelect = viewModel::selectRopeIndex,
                    onRopeStakeDrag = viewModel::updateRopeStakeWorld,
                    onRopeAnchorDrag = viewModel::updateRopeAnchorWorld,
                    onMeasureTap = viewModel::onMeasureTap,
                    onBlockZoneTap = viewModel::onBlockZoneTap,
                    onZoomIn = viewModel::zoomIn,
                    onZoomOut = viewModel::zoomOut,
                    onZoomContinuous = viewModel::setZoomLive,
                    onRotateSelectedBy = viewModel::rotateSelected,
                    onDrag = viewModel::dragSelection,
                    onDragEnd = viewModel::finishInteractionWithGridSnap,
                    onResetProject = viewModel::resetProject,
                    onAutoSetup = viewModel::autoSetup,
                    onParcelUpdate = viewModel::updateParcel,
                    onZoomReset = viewModel::resetZoom,
                    onClearZones = viewModel::clearBlockZones,
                    onProjectNameChange = viewModel::setProjectName,
                    onSave = viewModel::saveProject,
                    onLoad = viewModel::loadProject,
                    onExportProjectFile = viewModel::exportProjectFile,
                    onImportProjectFile = viewModel::importProjectFile,
                    onExportPng = viewModel::exportPng,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onLabelChange = viewModel::setSelectedLabel,
                    onRotate = viewModel::setSelectedRotation,
                    onToggleRopes = viewModel::toggleRopesForSelected,
                    onToggleLock = viewModel::toggleLockSelected,
                    onAddRope = viewModel::addRopeToSelected,
                    onRemoveRope = viewModel::removeRopeFromSelected,
                    onRestoreDefaultRopes = viewModel::restoreDefaultRopesForSelected,
                    onSelectRopeIndex = viewModel::selectRopeIndex,
                    onRopeScaleChange = viewModel::setSelectedRopeLengthScale,
                    onRopeAngleChange = viewModel::setSelectedRopeAngleOffset,
                    onRopeAnchorXChange = viewModel::setSelectedRopeAnchorX,
                    onRopeAnchorYChange = viewModel::setSelectedRopeAnchorY,
                    onSliderChangeFinished = viewModel::commitHistory,
                    onGroup = viewModel::groupSelection,
                    onUngroup = viewModel::ungroupSelection,
                    onDuplicate = viewModel::duplicateSelection,
                    onDelete = viewModel::deleteSelection,
                )
            }
            dragState?.let { drag ->
                Card(
                    modifier =
                        Modifier
                            .padding(
                                start = with(density) { drag.rootPosition.x.toDp() },
                                top = with(density) { drag.rootPosition.y.toDp() },
                            ).border(1.dp, MaterialTheme.colorScheme.primary),
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
@Preview
private fun AppPreview() {
    App()
}

@Composable
private fun ExpandedLayout(
    state: PlannerUiState,
    density: androidx.compose.ui.unit.Density,
    leftPaneWidthDp: Float,
    onLeftPaneWidthDpChange: (Float) -> Unit,
    darkThemeEnabled: Boolean,
    onToggleTheme: () -> Unit,
    shiftPressed: Boolean,
    dropRequest: PresetDropRequest?,
    suppressTapTemporarily: Boolean,
    onDropHandle: () -> Unit,
    onTool: (PlannerToolMode) -> Unit,
    onPresetSelect: (String) -> Unit,
    onPresetDragStart: (String, String, Offset) -> Unit,
    onPresetDrag: (Offset) -> Unit,
    onPresetDragEnd: (String, Offset) -> Unit,
    onPresetDragCancel: () -> Unit,
    onPlace: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onPlacePresetAt: (String, de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onSelection: (Set<String>) -> Unit,
    onSelectionToggle: (String?) -> Unit,
    onRopeSelect: (Int?) -> Unit,
    onRopeStakeDrag: (Int, de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onRopeAnchorDrag: (Int, de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onMeasureTap: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onBlockZoneTap: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomContinuous: (Float) -> Unit,
    onRotateSelectedBy: (Float) -> Unit,
    onDrag: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onDragEnd: () -> Unit,
    onResetProject: () -> Unit,
    onAutoSetup: (AutoSetupRequest) -> Unit,
    onParcelUpdate: (Float, Float) -> Unit,
    onZoomReset: () -> Unit,
    onClearZones: () -> Unit,
    onProjectNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onExportProjectFile: () -> Unit,
    onImportProjectFile: () -> Unit,
    onExportPng: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
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
    onSliderChangeFinished: () -> Unit,
    onGroup: () -> Unit,
    onUngroup: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var showWarningsPanel by remember { mutableStateOf(true) }
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            InventoryPane(
                modifier = Modifier.width(leftPaneWidthDp.dp),
                enableFullScroll = true,
                state = state,
                onTool = onTool,
                onPresetSelect = onPresetSelect,
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
                modifier =
                    Modifier
                        .width(8.dp)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                val dragDp = with(density) { dragAmount.toDp().value }
                                onLeftPaneWidthDpChange((leftPaneWidthDp + dragDp).coerceIn(280f, 520f))
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
                onDropHandle = onDropHandle,
                suppressTapTemporarily = suppressTapTemporarily,
                onPlace = onPlace,
                onPlacePresetAt = onPlacePresetAt,
                onSelection = onSelection,
                onSelectionToggle = onSelectionToggle,
                onRopeSelect = onRopeSelect,
                onRopeStakeDrag = onRopeStakeDrag,
                onRopeAnchorDrag = onRopeAnchorDrag,
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
                onProjectNameChange = onProjectNameChange,
                onSave = onSave,
                onLoad = onLoad,
                onResetProject = onResetProject,
                onAutoSetup = onAutoSetup,
                onExportProjectFile = onExportProjectFile,
                onImportProjectFile = onImportProjectFile,
                onExportPng = onExportPng,
                onUndo = onUndo,
                onRedo = onRedo,
                onLabelChange = onLabelChange,
                onRotate = onRotate,
                onToggleRopes = onToggleRopes,
                onToggleLock = onToggleLock,
                onAddRope = onAddRope,
                onRemoveRope = onRemoveRope,
                onRestoreDefaultRopes = onRestoreDefaultRopes,
                onSelectRopeIndex = onSelectRopeIndex,
                onRopeScaleChange = onRopeScaleChange,
                onRopeAngleChange = onRopeAngleChange,
                onRopeAnchorXChange = onRopeAnchorXChange,
                onRopeAnchorYChange = onRopeAnchorYChange,
                onSliderChangeFinished = onSliderChangeFinished,
                onGroup = onGroup,
                onUngroup = onUngroup,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
            )
        }

        Box(
            modifier =
                Modifier
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
                        WarningsButton(count = state.warnings.size, onClick = { showWarningsPanel = !showWarningsPanel })
                    }
                    ThemeToggleButton(darkThemeEnabled = darkThemeEnabled, onToggleTheme = onToggleTheme)
                }
            }
        }

        if (state.warnings.isNotEmpty() && showWarningsPanel) {
            WarningsCard(
                warnings = state.warnings,
                maxShown = 8,
                onClick = { showWarningsPanel = false },
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 56.dp)
                        .width(560.dp),
            )
        }
    }
}

private enum class CompactSheet { Inventory, Properties }

@Composable
private fun CompactLayout(
    state: PlannerUiState,
    darkThemeEnabled: Boolean,
    onToggleTheme: () -> Unit,
    shiftPressed: Boolean,
    dropRequest: PresetDropRequest?,
    suppressTapTemporarily: Boolean,
    onDropHandle: () -> Unit,
    onTool: (PlannerToolMode) -> Unit,
    onPresetSelect: (String) -> Unit,
    onPresetDragStart: (String, String, Offset) -> Unit,
    onPresetDrag: (Offset) -> Unit,
    onPresetDragEnd: (String, Offset) -> Unit,
    onPresetDragCancel: () -> Unit,
    onPlace: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onPlacePresetAt: (String, de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onSelection: (Set<String>) -> Unit,
    onSelectionToggle: (String?) -> Unit,
    onRopeSelect: (Int?) -> Unit,
    onRopeStakeDrag: (Int, de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onRopeAnchorDrag: (Int, de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onMeasureTap: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onBlockZoneTap: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomContinuous: (Float) -> Unit,
    onRotateSelectedBy: (Float) -> Unit,
    onDrag: (de.voculupo.historicalcamplayout.Vec2) -> Unit,
    onDragEnd: () -> Unit,
    onResetProject: () -> Unit,
    onAutoSetup: (AutoSetupRequest) -> Unit,
    onParcelUpdate: (Float, Float) -> Unit,
    onZoomReset: () -> Unit,
    onClearZones: () -> Unit,
    onProjectNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onExportProjectFile: () -> Unit,
    onImportProjectFile: () -> Unit,
    onExportPng: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
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
    onSliderChangeFinished: () -> Unit,
    onGroup: () -> Unit,
    onUngroup: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var sheet by remember { mutableStateOf<CompactSheet?>(null) }
    // Keeps the sheet's content visible while it slides out.
    var lastSheet by remember { mutableStateOf(CompactSheet.Inventory) }
    LaunchedEffect(sheet) { sheet?.let { lastSheet = it } }
    // While a preset is dragged out of the sheet, hide it (without disposing the gesture) so the whole plan is a drop target.
    var presetDragging by remember { mutableStateOf(false) }
    var showWarningsPanel by remember { mutableStateOf(false) }
    val hasSelection = state.selectedAssetIds.isNotEmpty()
    val currentTool = state.currentTool

    PlatformBackHandler(enabled = sheet != null || showWarningsPanel || hasSelection) {
        when {
            sheet != null -> sheet = null
            showWarningsPanel -> showWarningsPanel = false
            else -> onSelection(emptySet())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlannerCanvas(
            modifier = Modifier.fillMaxSize(),
            state = state,
            shiftPressed = shiftPressed,
            allowSingleFingerCanvasPan = true,
            allowMultiTouchCanvasPan = true,
            dropRequest = dropRequest,
            onDropHandle = onDropHandle,
            suppressTapTemporarily = suppressTapTemporarily,
            onPlace = onPlace,
            onPlacePresetAt = onPlacePresetAt,
            onSelection = onSelection,
            onSelectionToggle = onSelectionToggle,
            onRopeSelect = onRopeSelect,
            onRopeStakeDrag = onRopeStakeDrag,
            onRopeAnchorDrag = onRopeAnchorDrag,
            onMeasureTap = onMeasureTap,
            onBlockZoneTap = onBlockZoneTap,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            onZoomContinuous = onZoomContinuous,
            onRotateSelectedBy = onRotateSelectedBy,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
        )

        Row(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FloatingBar {
                IconButton(onClick = onUndo, enabled = state.canUndo) {
                    Icon(painterResource(Res.drawable.ic_undo), stringResource(Res.string.undo))
                }
                IconButton(onClick = onRedo, enabled = state.canRedo) {
                    Icon(painterResource(Res.drawable.ic_redo), stringResource(Res.string.redo))
                }
            }
            FloatingBar {
                if (state.warnings.isNotEmpty()) {
                    WarningsButton(count = state.warnings.size, onClick = { showWarningsPanel = !showWarningsPanel })
                }
                ThemeToggleButton(darkThemeEnabled = darkThemeEnabled, onToggleTheme = onToggleTheme)
                IconButton(onClick = { sheet = if (sheet == CompactSheet.Properties) null else CompactSheet.Properties }) {
                    Icon(painterResource(Res.drawable.ic_settings), stringResource(Res.string.properties))
                }
            }
        }

        AnimatedVisibility(
            visible = state.warnings.isNotEmpty() && showWarningsPanel && sheet == null,
            enter = fadeIn() + slideInVertically { -it / 4 },
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 68.dp, start = 12.dp, end = 12.dp)
                    .widthIn(max = 360.dp)
                    .zIndex(1f),
        ) {
            WarningsCard(warnings = state.warnings, maxShown = 6, onClick = { showWarningsPanel = false })
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = hasSelection && sheet == null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                SelectionActionBar(
                    state = state,
                    onRotateBy = { degrees ->
                        onRotateSelectedBy(degrees)
                        onSliderChangeFinished()
                    },
                    onDuplicate = onDuplicate,
                    onToggleLock = onToggleLock,
                    onEdit = { sheet = CompactSheet.Properties },
                    onDelete = onDelete,
                )
            }
            Surface(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
                shadowElevation = 6.dp,
            ) {
                NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                    val toolActive = { tool: PlannerToolMode -> currentTool == tool && sheet != CompactSheet.Inventory }
                    NavigationBarItem(
                        selected = toolActive(PlannerToolMode.Select),
                        onClick = { onTool(PlannerToolMode.Select) },
                        icon = { Icon(painterResource(Res.drawable.ic_select), null) },
                        label = { Text(stringResource(Res.string.tool_select)) },
                    )
                    NavigationBarItem(
                        selected = toolActive(PlannerToolMode.Measure),
                        onClick = { onTool(PlannerToolMode.Measure) },
                        icon = { Icon(painterResource(Res.drawable.ic_measure), null) },
                        label = { Text(stringResource(Res.string.tool_measure)) },
                    )
                    NavigationBarItem(
                        selected = toolActive(PlannerToolMode.BlockZone),
                        onClick = { onTool(PlannerToolMode.BlockZone) },
                        icon = { Icon(painterResource(Res.drawable.ic_block), null) },
                        label = { Text(stringResource(Res.string.tool_block_zone)) },
                    )
                    NavigationBarItem(
                        selected = sheet == CompactSheet.Inventory || toolActive(PlannerToolMode.PlaceTent),
                        onClick = { sheet = if (sheet == CompactSheet.Inventory) null else CompactSheet.Inventory },
                        icon = { Icon(painterResource(Res.drawable.ic_tent), null) },
                        label = { Text(stringResource(Res.string.inventory)) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = sheet != null && !presetDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.zIndex(2f),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .pointerInput(Unit) { detectTapGestures { sheet = null } },
            )
        }

        AnimatedVisibility(
            visible = sheet != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(3f),
        ) {
            val shownSheet = sheet ?: lastSheet
            Surface(
                modifier =
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(0.78f)
                        .graphicsLayer { alpha = if (presetDragging) 0f else 1f },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 12.dp,
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 10.dp)
                                .size(width = 36.dp, height = 4.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(if (shownSheet == CompactSheet.Inventory) Res.string.inventory else Res.string.properties),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        IconButton(onClick = { sheet = null }) {
                            Icon(painterResource(Res.drawable.ic_close), stringResource(Res.string.close))
                        }
                    }
                    if (shownSheet == CompactSheet.Inventory) {
                        InventoryPane(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            enableFullScroll = true,
                            state = state,
                            onTool = onTool,
                            onPresetSelect = { id ->
                                onPresetSelect(id)
                                sheet = null
                            },
                            onPresetDragStart = { id, label, rootPosition ->
                                presetDragging = true
                                onPresetDragStart(id, label, rootPosition)
                            },
                            onPresetDrag = onPresetDrag,
                            onPresetDragEnd = { id, rootPosition ->
                                presetDragging = false
                                sheet = null
                                onPresetDragEnd(id, rootPosition)
                            },
                            onPresetDragCancel = {
                                presetDragging = false
                                onPresetDragCancel()
                            },
                            onParcelUpdate = onParcelUpdate,
                            onZoomIn = onZoomIn,
                            onZoomOut = onZoomOut,
                            onZoomReset = onZoomReset,
                            onClearZones = onClearZones,
                        )
                    } else {
                        PropertiesPane(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            enableFullScroll = true,
                            state = state,
                            onProjectNameChange = onProjectNameChange,
                            onSave = onSave,
                            onLoad = onLoad,
                            onResetProject = onResetProject,
                            onAutoSetup = onAutoSetup,
                            onExportProjectFile = onExportProjectFile,
                            onImportProjectFile = onImportProjectFile,
                            onExportPng = onExportPng,
                            onUndo = onUndo,
                            onRedo = onRedo,
                            onLabelChange = onLabelChange,
                            onRotate = onRotate,
                            onToggleRopes = onToggleRopes,
                            onToggleLock = onToggleLock,
                            onAddRope = onAddRope,
                            onRemoveRope = onRemoveRope,
                            onRestoreDefaultRopes = onRestoreDefaultRopes,
                            onSelectRopeIndex = onSelectRopeIndex,
                            onRopeScaleChange = onRopeScaleChange,
                            onRopeAngleChange = onRopeAngleChange,
                            onRopeAnchorXChange = onRopeAnchorXChange,
                            onRopeAnchorYChange = onRopeAnchorYChange,
                            onSliderChangeFinished = onSliderChangeFinished,
                            onGroup = onGroup,
                            onUngroup = onUngroup,
                            onDuplicate = onDuplicate,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingBar(content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
        // Explicit: contentColorFor() can't match a color whose alpha was changed.
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun SelectionActionBar(
    state: PlannerUiState,
    onRotateBy: (Float) -> Unit,
    onDuplicate: () -> Unit,
    onToggleLock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val allLocked =
        state.project.assets
            .filter { it.id in state.selectedAssetIds }
            .let { selected -> selected.isNotEmpty() && selected.all { it.isLocked } }
    FloatingBar {
        IconButton(onClick = { onRotateBy(-ROTATE_STEP_DEG) }) {
            Icon(painterResource(Res.drawable.ic_rotate_left), stringResource(Res.string.rotate_left))
        }
        IconButton(onClick = { onRotateBy(ROTATE_STEP_DEG) }) {
            Icon(painterResource(Res.drawable.ic_rotate_right), stringResource(Res.string.rotate_right))
        }
        IconButton(onClick = onDuplicate) {
            Icon(painterResource(Res.drawable.ic_copy), stringResource(Res.string.duplicate))
        }
        IconButton(onClick = onToggleLock) {
            Icon(
                painterResource(if (allLocked) Res.drawable.ic_lock else Res.drawable.ic_lock_open),
                stringResource(if (allLocked) Res.string.unlock else Res.string.lock),
                tint = if (allLocked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(painterResource(Res.drawable.ic_edit), stringResource(Res.string.edit_selection))
        }
        IconButton(onClick = onDelete) {
            Icon(painterResource(Res.drawable.ic_delete), stringResource(Res.string.delete), tint = MaterialTheme.colorScheme.error)
        }
    }
}

private const val ROTATE_STEP_DEG = 15f

@Composable
private fun WarningsButton(
    count: Int,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { Badge { Text("$count") } }) {
            Icon(painterResource(Res.drawable.ic_warning), stringResource(Res.string.warnings), tint = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun ThemeToggleButton(
    darkThemeEnabled: Boolean,
    onToggleTheme: () -> Unit,
) {
    IconButton(onClick = onToggleTheme) {
        Icon(
            painterResource(if (darkThemeEnabled) Res.drawable.ic_light_mode else Res.drawable.ic_dark_mode),
            stringResource(Res.string.toggle_theme),
        )
    }
}

@Composable
private fun WarningsCard(
    warnings: List<String>,
    maxShown: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(Res.string.warnings_title, warnings.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            warnings.take(maxShown).forEach { msg ->
                Text("• $msg", style = MaterialTheme.typography.bodySmall)
            }
            if (warnings.size > maxShown) {
                Text(stringResource(Res.string.more_count, warnings.size - maxShown), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
