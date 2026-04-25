package de.voculupo.historicalcamplayout.features.planner.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import de.voculupo.historicalcamplayout.*
import de.voculupo.historicalcamplayout.data.storage.ProjectStorage
import de.voculupo.historicalcamplayout.data.storage.StorageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

class PlannerViewModel(
    private val storage: ProjectStorage = StorageProvider.create(),
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val autoSaveEnabled = getPlatform().name.startsWith("Android")
    private val autoSaveDelayMs = 1200L
    private val undoStack = ArrayDeque<PlannerProject>()
    private val redoStack = ArrayDeque<PlannerProject>()
    private val historyLimit = 100

    private val _uiState = MutableStateFlow(
        PlannerUiState(
            canUndo = false,
            canRedo = false,
        ),
    )
    val uiState: StateFlow<PlannerUiState> = _uiState.asStateFlow()

    init {
        val loadedPresets = mergePresets(storage.loadInventory(), defaultPresets())
        val loadedProject = storage.loadProject()
        _uiState.update { state ->
            val hydratedProject = if (loadedProject != null) {
                val presetsById = loadedPresets.associateBy { it.id }
                val hydrated = hydrateProjectRopes(loadedProject, loadedPresets)
                clampAssetsToParcel(hydrated, presetsById)
            } else {
                hydrateProjectRopes(state.project, loadedPresets)
            }
            state.copy(
                presets = loadedPresets,
                project = hydratedProject,
            )
        }
        pushHistory(_uiState.value.project)
        recomputeWarnings()

        if (autoSaveEnabled) {
            viewModelScope.launch {
                uiState
                    .map { it.project }
                    .distinctUntilChanged()
                    .drop(1)
                    .debounce(autoSaveDelayMs)
                    .collect { project ->
                        storage.saveProject(project)
                    }
            }
        }
    }

    fun setTool(tool: PlannerToolMode) = _uiState.update { it.copy(currentTool = tool, pendingBlockZoneStart = null) }

    fun selectPreset(id: String) = _uiState.update {
        it.copy(
            activePresetId = id,
            recentPresetIds = rememberRecentPreset(it.recentPresetIds, id),
        )
    }

    fun placeAssetAt(position: Vec2) {
        val state = _uiState.value
        val preset = state.presets.firstOrNull { it.id == state.activePresetId } ?: return
        placeFromPreset(preset, position)
    }

    fun placePresetAt(presetId: String, position: Vec2) {
        val state = _uiState.value
        val preset = state.presets.firstOrNull { it.id == presetId } ?: return
        placeFromPreset(preset, position)
        _uiState.update {
            it.copy(
                activePresetId = presetId,
                recentPresetIds = rememberRecentPreset(it.recentPresetIds, presetId),
            )
        }
    }

    private fun placeFromPreset(preset: TentPreset, position: Vec2) {
        val state = _uiState.value
        val snapped = GeometryEngine.snapToGrid(position, state.project.gridMeters)
        val constrained = constrainAssetCenterToParcel(
            position = snapped,
            preset = preset,
            parcel = state.project.parcel,
        )
        val newAsset = PlacedAsset(
            id = Random.nextLong().toString(),
            presetId = preset.id,
            label = preset.name,
            position = constrained,
            safetyMarginMeters = preset.defaultSafetyMarginMeters,
            heightRange = preset.defaultHeightRange,
            ropes = preset.ropeAnchors.map {
                AssetRope(
                    localX = it.localX,
                    localY = it.localY,
                    lengthMeters = it.lengthMeters,
                    outwardAngleDeg = it.outwardAngleDeg ?: 0f,
                )
            },
        )
        mutateProject {
            copy(assets = assets + newAsset)
        }
        _uiState.update {
            it.copy(
                selectedAssetIds = setOf(newAsset.id),
                selectedRopeIndex = null,
                currentTool = PlannerToolMode.Select,
            )
        }
    }

    fun setSelection(selection: Set<String>) {
        _uiState.update { it.copy(selectedAssetIds = selection, selectedRopeIndex = null, currentTool = PlannerToolMode.Select) }
    }

    fun toggleSelection(assetId: String?) {
        if (assetId == null) {
            _uiState.update { it.copy(selectedAssetIds = emptySet()) }
            return
        }
        _uiState.update { state ->
            if (assetId in state.selectedAssetIds) {
                state.copy(selectedAssetIds = state.selectedAssetIds - assetId, selectedRopeIndex = null, currentTool = PlannerToolMode.Select)
            } else {
                state.copy(selectedAssetIds = state.selectedAssetIds + assetId, selectedRopeIndex = null, currentTool = PlannerToolMode.Select)
            }
        }
    }

    fun onMeasureTap(point: Vec2) {
        _uiState.update { state ->
            when {
                state.measurementStart == null -> state.copy(measurementStart = point, measurementEnd = null)
                state.measurementEnd == null -> state.copy(measurementEnd = point)
                else -> state.copy(measurementStart = point, measurementEnd = null)
            }
        }
    }

    fun onBlockZoneTap(point: Vec2) {
        val start = _uiState.value.pendingBlockZoneStart
        if (start == null) {
            _uiState.update { it.copy(pendingBlockZoneStart = point) }
            return
        }

        val minX = minOf(start.x, point.x)
        val maxX = maxOf(start.x, point.x)
        val minY = minOf(start.y, point.y)
        val maxY = maxOf(start.y, point.y)

        if (abs(maxX - minX) < 0.1f || abs(maxY - minY) < 0.1f) {
            _uiState.update { it.copy(pendingBlockZoneStart = null) }
            return
        }

        val zone = BlockZone(
            id = "zone-${Random.nextInt(100000)}",
            points = listOf(
                Vec2(minX, minY),
                Vec2(maxX, minY),
                Vec2(maxX, maxY),
                Vec2(minX, maxY),
            ),
        )
        mutateProject { copy(blockZones = blockZones + zone) }
        _uiState.update { it.copy(pendingBlockZoneStart = null, currentTool = PlannerToolMode.Select) }
    }

    fun clearBlockZones() {
        mutateProject { copy(blockZones = emptyList()) }
    }

    fun updateParcel(widthMeters: Float, heightMeters: Float) {
        if (widthMeters <= 0.5f || heightMeters <= 0.5f) return
        val presetsById = _uiState.value.presets.associateBy { it.id }
        mutateProject {
            val resized = copy(parcel = parcel.copy(widthMeters = widthMeters, heightMeters = heightMeters))
            clampAssetsToParcel(resized, presetsById)
        }
    }

    fun zoomIn() {
        adjustZoom(1.15f)
    }

    fun zoomOut() {
        adjustZoom(1f / 1.15f)
    }

    fun resetZoom() {
        mutateProject { copy(pixelsPerMeter = 50f) }
    }

    fun resetProject() {
        val fresh = PlannerProject()
        _uiState.update {
            it.copy(
                project = fresh,
                selectedAssetIds = emptySet(),
                selectedRopeIndex = null,
                currentTool = PlannerToolMode.Select,
                activePresetId = null,
                recentPresetIds = emptyList(),
                measurementStart = null,
                measurementEnd = null,
                pendingBlockZoneStart = null,
            )
        }
        undoStack.clear()
        redoStack.clear()
        pushHistory(fresh)
        recomputeWarnings()
    }

    fun setZoomLive(pixelsPerMeter: Float) {
        val target = pixelsPerMeter.coerceIn(20f, 180f)
        val current = _uiState.value.project.pixelsPerMeter
        if (abs(target - current) < 0.01f) return
        _uiState.update { state ->
            state.copy(project = state.project.copy(pixelsPerMeter = target))
        }
    }

    fun setProjectName(name: String) {
        val sanitized = name.trim().take(80)
        if (sanitized.isBlank()) return
        mutateProject {
            if (this.name == sanitized) this else copy(name = sanitized)
        }
    }

    fun dragSelection(deltaMeters: Vec2) {
        val snapshot = _uiState.value
        val selected = snapshot.selectedAssetIds
        if (selected.isEmpty()) return
        val presetsById = snapshot.presets.associateBy { it.id }
        val selectedGroups = snapshot.project.assets.filter { it.id in selected }.mapNotNull { it.groupId }.toSet()
        val movableIds = snapshot.project.assets
            .filter { asset ->
                val isGroupedMove = asset.groupId != null && asset.groupId in selectedGroups
                (asset.id in selected || isGroupedMove) && !asset.isLocked
            }
            .map { it.id }
            .toSet()
        val clampedDelta = clampDeltaForAssetsInParcel(
            project = snapshot.project,
            presetsById = presetsById,
            assetIds = movableIds,
            requestedDelta = deltaMeters,
        )
        _uiState.update { state ->
            state.copy(
                project = state.project.copy(
                    assets = state.project.assets.map { asset ->
                        val isGroupedMove = asset.groupId != null && asset.groupId in selectedGroups
                        if ((asset.id !in selected && !isGroupedMove) || asset.isLocked) asset else {
                            val target = Vec2(asset.position.x + clampedDelta.x, asset.position.y + clampedDelta.y)
                            val preset = state.presets.firstOrNull { it.id == asset.presetId } ?: return@map asset
                            val constrained = constrainAssetCenterToParcel(target, preset, state.project.parcel)
                            asset.copy(position = constrained)
                        }
                    },
                ),
            )
        }
        recomputeWarnings()
    }

    fun finishInteractionWithGridSnap() {
        val state = _uiState.value
        val selected = state.selectedAssetIds
        val presetsById = state.presets.associateBy { it.id }
        mutateProject {
            copy(
                assets = assets.map { asset ->
                    if (asset.id !in selected) asset else {
                        val snapped = GeometryEngine.snapToGrid(asset.position, gridMeters)
                        val preset = presetsById[asset.presetId] ?: return@map asset
                        val constrained = constrainAssetCenterToParcel(snapped, preset, parcel)
                        asset.copy(position = constrained)
                    }
                },
            )
        }
    }

    fun rotateSelected(deltaDeg: Float) {
        val selected = _uiState.value.selectedAssetIds
        if (selected.isEmpty()) return
        mutateProject {
            copy(
                assets = assets.map { asset ->
                    if (asset.id !in selected || asset.isLocked) asset
                    else asset.copy(rotationDeg = (asset.rotationDeg + deltaDeg + 360f) % 360f)
                },
            )
        }
    }

    fun setSelectedRotation(value: Float) {
        val selected = _uiState.value.selectedAssetIds
        if (selected.isEmpty()) return
        mutateProject {
            copy(
                assets = assets.map { asset ->
                    if (asset.id !in selected || asset.isLocked) asset
                    else asset.copy(rotationDeg = value)
                },
            )
        }
    }

    fun setSelectedLabel(value: String) {
        val selected = _uiState.value.selectedAssetIds
        if (selected.isEmpty()) return
        val sanitized = value.trim().take(60)
        mutateProject {
            copy(
                assets = assets.map { asset ->
                    if (asset.id !in selected || asset.isLocked) asset
                    else asset.copy(label = sanitized)
                },
            )
        }
    }

    fun toggleRopesForSelected() {
        val selected = _uiState.value.selectedAssetIds
        mutateProject {
            copy(
                assets = assets.map { asset ->
                    if (asset.id !in selected) asset else asset.copy(showRopes = !asset.showRopes)
                },
            )
        }
    }

    fun addRopeToSelected() {
        val selected = _uiState.value.selectedAssetIds
        val presetsById = _uiState.value.presets.associateBy { it.id }
        mutateProject {
            copy(
                assets = assets.map { asset ->
                    if (asset.id !in selected) asset
                    else {
                        val preset = presetsById[asset.presetId]
                        val slots = preset?.let { GeometryEngine.anchorSlotsForPreset(it) }.orEmpty()
                        val next = asset.ropes.size
                        val slot = if (slots.isNotEmpty()) slots[next % slots.size] else Vec2(0f, 0f)
                        val rope = AssetRope(
                            localX = slot.x,
                            localY = slot.y,
                            lengthMeters = 1.8f,
                            outwardAngleDeg = 0f,
                        )
                        asset.copy(ropes = asset.ropes + rope, showRopes = true)
                    }
                },
            )
        }
        val selectedAsset = _uiState.value.project.assets.firstOrNull { it.id in selected }
        _uiState.update { it.copy(selectedRopeIndex = selectedAsset?.ropes?.lastIndex) }
    }

    fun removeRopeFromSelected() {
        val selected = _uiState.value.selectedAssetIds
        mutateProject {
            copy(
                assets = assets.map { asset ->
                    if (asset.id !in selected || asset.ropes.isEmpty()) asset
                    else asset.copy(ropes = asset.ropes.dropLast(1))
                },
            )
        }
        _uiState.update { it.copy(selectedRopeIndex = null) }
    }

    fun restoreDefaultRopesForSelected() {
        val selected = _uiState.value.selectedAssetIds
        if (selected.isEmpty()) return
        val presetsById = _uiState.value.presets.associateBy { it.id }
        mutateProject {
            copy(
                assets = assets.map { asset ->
                    if (asset.id !in selected) asset
                    else {
                        val preset = presetsById[asset.presetId]
                        if (preset == null) asset
                        else {
                            val restored = preset.ropeAnchors.map { anchor ->
                                AssetRope(
                                    localX = anchor.localX,
                                    localY = anchor.localY,
                                    lengthMeters = anchor.lengthMeters,
                                    outwardAngleDeg = anchor.outwardAngleDeg ?: 0f,
                                )
                            }
                            asset.copy(ropes = restored, showRopes = true)
                        }
                    }
                },
            )
        }
        _uiState.update { state ->
            val selectedAsset = state.project.assets.firstOrNull { it.id in selected }
            state.copy(
                selectedRopeIndex = selectedAsset?.ropes?.indices?.firstOrNull(),
            )
        }
    }

    fun selectRopeIndex(index: Int?) {
        _uiState.update { it.copy(selectedRopeIndex = index) }
    }

    fun setSelectedRopeLengthScale(value: Float) {
        val selected = _uiState.value.selectedAssetIds
        val ropeIndex = _uiState.value.selectedRopeIndex ?: return
        mutateProject {
            copy(
                assets = assets.map { asset ->
                    if (asset.id !in selected) asset
                    else {
                        val ropes = asset.ropes.toMutableList()
                        if (ropeIndex in ropes.indices) {
                            ropes[ropeIndex] = ropes[ropeIndex].copy(lengthMeters = value.coerceIn(0.3f, 6.0f))
                        }
                        asset.copy(ropes = ropes)
                    }
                },
            )
        }
    }

    fun setSelectedRopeAngleOffset(value: Float) {
        val selected = _uiState.value.selectedAssetIds
        val ropeIndex = _uiState.value.selectedRopeIndex ?: return
        mutateProject {
            copy(
                assets = assets.map { asset ->
                    if (asset.id !in selected) asset
                    else {
                        val ropes = asset.ropes.toMutableList()
                        if (ropeIndex in ropes.indices) {
                            ropes[ropeIndex] = ropes[ropeIndex].copy(outwardAngleDeg = value.coerceIn(-180f, 180f))
                        }
                        asset.copy(ropes = ropes)
                    }
                },
            )
        }
    }

    fun setSelectedRopeAnchorX(value: Float) {
        val selected = _uiState.value.selectedAssetIds
        val ropeIndex = _uiState.value.selectedRopeIndex ?: return
        mutateProject {
            copy(
                assets = assets.map { asset ->
                    if (asset.id !in selected) asset else {
                        val ropes = asset.ropes.toMutableList()
                        if (ropeIndex in ropes.indices) {
                            ropes[ropeIndex] = ropes[ropeIndex].copy(localX = value)
                        }
                        asset.copy(ropes = ropes)
                    }
                },
            )
        }
    }

    fun setSelectedRopeAnchorY(value: Float) {
        val selected = _uiState.value.selectedAssetIds
        val ropeIndex = _uiState.value.selectedRopeIndex ?: return
        mutateProject {
            copy(
                assets = assets.map { asset ->
                    if (asset.id !in selected) asset else {
                        val ropes = asset.ropes.toMutableList()
                        if (ropeIndex in ropes.indices) {
                            ropes[ropeIndex] = ropes[ropeIndex].copy(localY = value)
                        }
                        asset.copy(ropes = ropes)
                    }
                },
            )
        }
    }

    fun updateRopeStakeWorld(ropeIndex: Int, world: Vec2) {
        val selected = _uiState.value.selectedAssetIds
        mutateProject {
            copy(
                assets = assets.map { asset ->
                    if (asset.id !in selected) asset else {
                        val ropes = asset.ropes.toMutableList()
                        if (ropeIndex in ropes.indices) {
                            val rope = ropes[ropeIndex]
                            val anchorWorld = GeometryEngine.worldPoint(asset, Vec2(rope.localX, rope.localY))
                            val dx = world.x - anchorWorld.x
                            val dy = world.y - anchorWorld.y
                            val len = sqrt(dx * dx + dy * dy).coerceIn(0.3f, 8.0f)
                            val absoluteDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            val relativeDeg = normalizeDegrees(absoluteDeg - asset.rotationDeg)
                            ropes[ropeIndex] = rope.copy(
                                lengthMeters = len,
                                outwardAngleDeg = relativeDeg,
                            )
                        }
                        asset.copy(ropes = ropes)
                    }
                },
            )
        }
        _uiState.update { it.copy(selectedRopeIndex = ropeIndex) }
    }

    fun updateRopeAnchorWorld(ropeIndex: Int, world: Vec2) {
        val selected = _uiState.value.selectedAssetIds
        val presetsById = _uiState.value.presets.associateBy { it.id }
        mutateProject {
            copy(
                assets = assets.map { asset ->
                    if (asset.id !in selected) asset else {
                        val ropes = asset.ropes.toMutableList()
                        if (ropeIndex in ropes.indices) {
                            val rope = ropes[ropeIndex]
                            // Convert world anchor position into asset-local coordinates
                            val dx = world.x - asset.position.x
                            val dy = world.y - asset.position.y
                            val rad = Math.toRadians((-asset.rotationDeg).toDouble())
                            val cos = kotlin.math.cos(rad).toFloat()
                            val sin = kotlin.math.sin(rad).toFloat()
                            val localX = dx * cos - dy * sin
                            val localY = dx * sin + dy * cos
                            val preset = presetsById[asset.presetId]
                            val snapped = if (preset != null) {
                                GeometryEngine.snapLocalAnchorToPreset(Vec2(localX, localY), preset)
                            } else Vec2(localX, localY)
                            ropes[ropeIndex] = rope.copy(
                                localX = snapped.x,
                                localY = snapped.y,
                            )
                        }
                        asset.copy(ropes = ropes)
                    }
                },
            )
        }
        _uiState.update { it.copy(selectedRopeIndex = ropeIndex) }
    }

    fun groupSelection() {
        val selected = _uiState.value.selectedAssetIds
        if (selected.size < 2) return
        val group = "group-${Random.nextInt(100000)}"
        mutateProject {
            copy(assets = assets.map { if (it.id in selected) it.copy(groupId = group) else it })
        }
    }

    fun ungroupSelection() {
        val selected = _uiState.value.selectedAssetIds
        mutateProject {
            copy(assets = assets.map { if (it.id in selected) it.copy(groupId = null) else it })
        }
    }

    fun deleteSelection() {
        val selected = _uiState.value.selectedAssetIds
        mutateProject { copy(assets = assets.filterNot { it.id in selected }) }
        _uiState.update { it.copy(selectedAssetIds = emptySet()) }
    }

    fun duplicateSelection() {
        val state = _uiState.value
        val selected = state.project.assets.filter { it.id in state.selectedAssetIds }
        if (selected.isEmpty()) return
        val step = maxOf(state.project.gridMeters, 0.5f)
        val duplicates = selected.map { asset ->
            val preset = state.presets.firstOrNull { it.id == asset.presetId }
            val target = Vec2(asset.position.x + step, asset.position.y + step)
            val constrained = if (preset != null) {
                constrainAssetCenterToParcel(target, preset, state.project.parcel)
            } else target
            asset.copy(
                id = Random.nextLong().toString(),
                label = if (asset.label.isBlank()) asset.label else "${asset.label} (Kopie)",
                position = constrained,
            )
        }
        mutateProject { copy(assets = assets + duplicates) }
        _uiState.update { it.copy(selectedAssetIds = duplicates.map { asset -> asset.id }.toSet()) }
    }

    fun undo() {
        if (undoStack.size <= 1) return
        val current = undoStack.removeLast()
        redoStack.addLast(current)
        val restored = undoStack.last()
        _uiState.update {
            it.copy(
                project = restored,
                canUndo = undoStack.size > 1,
                canRedo = redoStack.isNotEmpty(),
                selectedAssetIds = emptySet(),
                selectedRopeIndex = null,
            )
        }
        recomputeWarnings()
    }

    fun redo() {
        val state = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(state)
        _uiState.update {
            it.copy(
                project = state,
                canUndo = undoStack.size > 1,
                canRedo = redoStack.isNotEmpty(),
                selectedAssetIds = emptySet(),
                selectedRopeIndex = null,
            )
        }
        recomputeWarnings()
    }

    fun saveProject() {
        storage.saveProject(_uiState.value.project)
    }

    fun loadProject() {
        val loaded = storage.loadProject() ?: return
        val presets = _uiState.value.presets
        val presetsById = presets.associateBy { it.id }
        val hydrated = hydrateProjectRopes(loaded, presets)
        val clamped = clampAssetsToParcel(hydrated, presetsById)
        _uiState.update {
            it.copy(
                project = clamped,
                selectedAssetIds = emptySet(),
                selectedRopeIndex = null,
            )
        }
        undoStack.clear()
        redoStack.clear()
        pushHistory(_uiState.value.project)
        recomputeWarnings()
    }

    fun exportProjectFile() {
        storage.exportProjectFile(_uiState.value.project)
    }

    fun importProjectFile() {
        val imported = storage.importProjectFile() ?: return
        val presets = _uiState.value.presets
        val presetsById = presets.associateBy { it.id }
        val hydrated = hydrateProjectRopes(imported, presets)
        val clamped = clampAssetsToParcel(hydrated, presetsById)
        _uiState.update {
            it.copy(
                project = clamped,
                selectedAssetIds = emptySet(),
                selectedRopeIndex = null,
            )
        }
        undoStack.clear()
        redoStack.clear()
        pushHistory(_uiState.value.project)
        recomputeWarnings()
    }

    fun exportPng() {
        storage.exportProjectPng(_uiState.value.project, _uiState.value.presets)
    }

    fun saveInventory() {
        storage.saveInventory(_uiState.value.presets)
    }

    private fun mutateProject(transform: PlannerProject.() -> PlannerProject) {
        val next = _uiState.value.project.transform()
        _uiState.update { it.copy(project = next) }
        pushHistory(next)
        recomputeWarnings()
    }

    private fun pushHistory(project: PlannerProject) {
        undoStack.addLast(project)
        while (undoStack.size > historyLimit) undoStack.removeFirst()
        redoStack.clear()
        _uiState.update { it.copy(canUndo = undoStack.size > 1, canRedo = redoStack.isNotEmpty()) }
    }

    private fun recomputeWarnings() {
        val state = _uiState.value
        val report = GeometryEngine.evaluate(state.project, state.presets)
        _uiState.update {
            it.copy(
                warnings = report.messages,
                warningAssetIds = report.warningCollisions,
                hardCollisionAssetIds = report.hardCollisions,
            )
        }
    }

    private fun rememberRecentPreset(current: List<String>, id: String): List<String> {
        return PlannerDomainOps.rememberRecentPreset(current, id)
    }

    private fun mergePresets(stored: List<TentPreset>?, defaults: List<TentPreset>): List<TentPreset> {
        return PlannerDomainOps.mergePresets(stored, defaults)
    }

    private fun normalizeDegrees(value: Float): Float {
        return PlannerDomainOps.normalizeDegrees(value)
    }

    private fun hydrateProjectRopes(project: PlannerProject, presets: List<TentPreset>): PlannerProject {
        return PlannerDomainOps.hydrateProjectRopes(project, presets)
    }

    private fun clampAssetsToParcel(project: PlannerProject, presetsById: Map<String, TentPreset>): PlannerProject {
        return PlannerDomainOps.clampAssetsToParcel(project, presetsById)
    }

    private fun constrainAssetCenterToParcel(position: Vec2, preset: TentPreset, parcel: Parcel): Vec2 {
        return PlannerDomainOps.constrainAssetCenterToParcel(position, preset, parcel)
    }

    private fun adjustZoom(factor: Float) {
        val current = _uiState.value.project.pixelsPerMeter
        val target = (current * factor).coerceIn(20f, 180f)
        if (abs(target - current) < 0.01f) return
        mutateProject { copy(pixelsPerMeter = target) }
    }

    private fun clampDeltaForAssetsInParcel(
        project: PlannerProject,
        presetsById: Map<String, TentPreset>,
        assetIds: Set<String>,
        requestedDelta: Vec2,
    ): Vec2 {
        return PlannerDomainOps.clampDeltaForAssetsInParcel(project, presetsById, assetIds, requestedDelta)
    }
}

