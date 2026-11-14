package de.voculupo.historicalcamplayout.features.planner.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.voculupo.historicalcamplayout.AssetRope
import de.voculupo.historicalcamplayout.AutoSetupRequest
import de.voculupo.historicalcamplayout.BlockZone
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.Parcel
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.PlannerToolMode
import de.voculupo.historicalcamplayout.PlannerUiState
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.Vec2
import de.voculupo.historicalcamplayout.data.storage.ProjectStorage
import de.voculupo.historicalcamplayout.data.storage.StorageProvider
import de.voculupo.historicalcamplayout.defaultPresets
import de.voculupo.historicalcamplayout.features.planner.state.PlannerDomainOps.constrainAssetCenterToParcel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Undo model:
 * - [mutateProject]: discrete edit, becomes its own undo step.
 * - [updateProjectLive]: continuous edit (slider, drag, pinch); no undo step yet.
 *   Pending live edits become one step on [commitHistory], before the next discrete edit, or before undo.
 * - Zoom ([PlannerProject.pixelsPerMeter]) is a view setting: never an undo step and never restored by undo.
 */
class PlannerViewModel(
    private val storage: ProjectStorage = StorageProvider.create(),
) : ViewModel() {
    private val autoSaveDelayMs = 1200L
    private val undoStack = ArrayDeque<PlannerProject>()
    private val redoStack = ArrayDeque<PlannerProject>()
    private val historyLimit = 100

    private val _uiState =
        MutableStateFlow(
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
            val hydratedProject =
                if (loadedProject != null) {
                    val presetsById = loadedPresets.associateBy { it.id }
                    clampAssetsToParcel(loadedProject, presetsById)
                } else {
                    state.project
                }
            state.copy(
                presets = loadedPresets,
                project = hydratedProject,
            )
        }
        pushHistory(_uiState.value.project)
        recomputeWarnings()

        viewModelScope.launch(Dispatchers.Default) {
            uiState
                .map { it.project }
                // Zooming alone is not worth a disk write; it is saved with the next real edit.
                .distinctUntilChanged { old, new -> sameIgnoringZoom(old, new) }
                .drop(1)
                .debounce(autoSaveDelayMs)
                .collect { project ->
                    storage.saveProject(project)
                }
        }
    }

    fun setTool(tool: PlannerToolMode) = _uiState.update { it.copy(currentTool = tool, pendingBlockZoneStart = null) }

    fun selectPreset(id: String) =
        _uiState.update {
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

    fun placePresetAt(
        presetId: String,
        position: Vec2,
    ) {
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

    private fun placeFromPreset(
        preset: TentPreset,
        position: Vec2,
    ) {
        val state = _uiState.value
        val snapped = GeometryEngine.snapToGrid(position, state.project.gridMeters)
        val constrained =
            constrainAssetCenterToParcel(
                position = snapped,
                preset = preset,
                parcel = state.project.parcel,
            )
        val newAsset =
            PlacedAsset(
                id = newId(),
                presetId = preset.id,
                label = preset.name,
                position = constrained,
                safetyMarginMeters = preset.defaultSafetyMarginMeters,
                heightRange = preset.defaultHeightRange,
                ropes = defaultRopes(preset),
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
                state.copy(
                    selectedAssetIds = state.selectedAssetIds - assetId,
                    selectedRopeIndex = null,
                    currentTool = PlannerToolMode.Select,
                )
            } else {
                state.copy(
                    selectedAssetIds = state.selectedAssetIds + assetId,
                    selectedRopeIndex = null,
                    currentTool = PlannerToolMode.Select,
                )
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

        val zone =
            BlockZone(
                id = newId("zone-"),
                points =
                    listOf(
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

    fun updateParcel(
        widthMeters: Float,
        heightMeters: Float,
    ) {
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
        setZoomLive(DEFAULT_PIXELS_PER_METER)
    }

    /** Generates a camp draft (see [AutoLayout]); one undo step, block zones are kept. */
    fun autoSetup(request: AutoSetupRequest) {
        val state = _uiState.value
        val presetsById = state.presets.associateBy { it.id }
        // Hand-drawn zones stay and are avoided; zones from an earlier auto setup are replaced.
        val userZones = state.project.blockZones.filterNot { it.id.startsWith(AutoLayout.AUTO_PREFIX) }
        val kept = if (request.replaceExisting) emptyList() else state.project.assets
        val result = AutoLayout.generate(request, presetsById, userZones, kept) { newId(it) }
        mutateProject {
            // Placed tents stay loose so each can be moved on its own.
            copy(
                parcel = request.parcel,
                assets = kept + result.assets.map { it.copy(groupId = null) },
                blockZones = userZones + result.zones,
                autoSetup = request,
            )
        }
        _uiState.update { it.copy(selectedAssetIds = emptySet(), selectedRopeIndex = null) }
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
        val target = pixelsPerMeter.coerceIn(MIN_PIXELS_PER_METER, MAX_PIXELS_PER_METER)
        val current = _uiState.value.project.pixelsPerMeter
        if (abs(target - current) < 0.01f) return
        _uiState.update { state ->
            state.copy(project = state.project.copy(pixelsPerMeter = target))
        }
    }

    fun setProjectName(name: String) {
        val sanitized = name.trim().take(80)
        if (sanitized.isBlank()) return
        mutateProject { copy(name = sanitized) }
    }

    fun dragSelection(deltaMeters: Vec2) {
        val snapshot = _uiState.value
        val movableIds = movableAssetIds(snapshot)
        if (movableIds.isEmpty()) return
        val presetsById = snapshot.presets.associateBy { it.id }
        val clampedDelta =
            clampDeltaForAssetsInParcel(
                project = snapshot.project,
                presetsById = presetsById,
                assetIds = movableIds,
                requestedDelta = deltaMeters,
            )
        updateProjectLive {
            copy(
                assets =
                    assets.map { asset ->
                        if (asset.id !in movableIds) return@map asset
                        val preset = presetsById[asset.presetId] ?: return@map asset
                        val target = Vec2(asset.position.x + clampedDelta.x, asset.position.y + clampedDelta.y)
                        asset.copy(position = constrainAssetCenterToParcel(target, preset, parcel, asset.rotationDeg))
                    },
            )
        }
    }

    /** End of a canvas drag (asset move or rope handle): snap moved assets and commit one undo step. */
    fun finishInteractionWithGridSnap() {
        val state = _uiState.value
        val movedIds = movableAssetIds(state)
        val presetsById = state.presets.associateBy { it.id }
        // The drag and its snap are one undo step; otherwise the first undo only "unsnaps" by millimetres.
        mutateProject(foldLiveEdits = true) {
            val snappedAssets =
                assets.map { asset ->
                    if (asset.id !in movedIds) {
                        asset
                    } else {
                        val snapped = GeometryEngine.snapToGrid(asset.position, gridMeters)
                        val preset = presetsById[asset.presetId] ?: return@map asset
                        val constrained = constrainAssetCenterToParcel(snapped, preset, parcel, asset.rotationDeg)
                        asset.copy(position = constrained)
                    }
                }
            val joinedAssets =
                applySunSailJoinSnapping(
                    assets = snappedAssets,
                    selectedIds = state.selectedAssetIds,
                    presetsById = presetsById,
                    parcel = parcel,
                )
            copy(assets = joinedAssets)
        }
    }

    private fun applySunSailJoinSnapping(
        assets: List<PlacedAsset>,
        selectedIds: Set<String>,
        presetsById: Map<String, TentPreset>,
        parcel: Parcel,
    ): List<PlacedAsset> {
        if (selectedIds.isEmpty()) return assets
        val updated = assets.toMutableList()
        val indexById = updated.mapIndexed { idx, asset -> asset.id to idx }.toMap()
        val selectedSunSails =
            updated.filter { asset ->
                asset.id in selectedIds &&
                    !asset.isLocked &&
                    presetsById[asset.presetId]?.let(GeometryEngine::isSunSailPreset) == true
            }

        selectedSunSails.forEach { moving ->
            val movingIdx = indexById[moving.id] ?: return@forEach
            val movingPreset = presetsById[moving.presetId] ?: return@forEach
            val movingAssetCurrent = updated[movingIdx]
            val bestSnap =
                updated
                    .asSequence()
                    .filter { candidate ->
                        candidate.id != moving.id &&
                            presetsById[candidate.presetId]?.let(GeometryEngine::isSunSailPreset) == true
                    }.mapNotNull { target ->
                        val targetPreset = presetsById[target.presetId] ?: return@mapNotNull null
                        GeometryEngine.snapSunSailToNeighbor(
                            movingAsset = movingAssetCurrent,
                            movingPreset = movingPreset,
                            targetAsset = target,
                            targetPreset = targetPreset,
                        )
                    }.minByOrNull { snapped -> GeometryEngine.distance(movingAssetCurrent.position, snapped) }
                    ?: return@forEach
            // Hidden join ropes are derived from geometry in recomputeWarnings(), so only the position changes here.
            updated[movingIdx] =
                movingAssetCurrent.copy(
                    position = constrainAssetCenterToParcel(bestSnap, movingPreset, parcel, movingAssetCurrent.rotationDeg),
                )
        }
        return updated
    }

    /** Two-finger rotate on the canvas; called per gesture frame, so it is a live edit. */
    fun rotateSelected(deltaDeg: Float) {
        updateSelectedUnlocked(live = true) { asset ->
            asset.copy(rotationDeg = (asset.rotationDeg + deltaDeg + 360f) % 360f)
        }
    }

    /** Rotation slider; live until [commitHistory]. */
    fun setSelectedRotation(value: Float) {
        updateSelectedUnlocked(live = true) { it.copy(rotationDeg = value) }
    }

    /** Label text field (called per keystroke); live until the next commit. */
    fun setSelectedLabel(value: String) {
        val sanitized = value.trim().take(60)
        updateSelectedUnlocked(live = true) { it.copy(label = sanitized) }
    }

    /** Locks all selected assets, or unlocks them if every one is already locked. */
    fun toggleLockSelected() {
        val state = _uiState.value
        val selected = state.project.assets.filter { it.id in state.selectedAssetIds }
        if (selected.isEmpty()) return
        val lock = !selected.all { it.isLocked }
        mutateProject { copy(assets = assets.map { if (it.id in state.selectedAssetIds) it.copy(isLocked = lock) else it }) }
    }

    fun toggleRopesForSelected() {
        updateSelectedUnlocked { it.copy(showRopes = !it.showRopes) }
    }

    fun addRopeToSelected() {
        val presetsById = _uiState.value.presets.associateBy { it.id }
        updateSelectedUnlocked { asset ->
            val slots = presetsById[asset.presetId]?.let { GeometryEngine.anchorSlotsForPreset(it) }.orEmpty()
            val slot = if (slots.isNotEmpty()) slots[asset.ropes.size % slots.size] else Vec2(0f, 0f)
            val rope =
                AssetRope(
                    localX = slot.x,
                    localY = slot.y,
                    lengthMeters = 1.8f,
                    outwardAngleDeg = 0f,
                )
            asset.copy(ropes = asset.ropes + rope, showRopes = true)
        }
        _uiState.update { it.copy(selectedRopeIndex = firstSelectedAsset(it)?.ropes?.lastIndex) }
    }

    fun removeRopeFromSelected() {
        updateSelectedUnlocked { asset ->
            if (asset.ropes.isEmpty()) asset else asset.copy(ropes = asset.ropes.dropLast(1))
        }
        _uiState.update { it.copy(selectedRopeIndex = null) }
    }

    fun restoreDefaultRopesForSelected() {
        val presetsById = _uiState.value.presets.associateBy { it.id }
        updateSelectedUnlocked { asset ->
            val preset = presetsById[asset.presetId] ?: return@updateSelectedUnlocked asset
            asset.copy(ropes = defaultRopes(preset), showRopes = true)
        }
        _uiState.update { state ->
            state.copy(selectedRopeIndex = firstSelectedAsset(state)?.ropes?.indices?.firstOrNull())
        }
    }

    fun selectRopeIndex(index: Int?) {
        _uiState.update { it.copy(selectedRopeIndex = index) }
    }

    fun setSelectedRopeLengthScale(value: Float) = updateSelectedRopeValue { it.copy(lengthMeters = value.coerceIn(0.3f, 6.0f)) }

    fun setSelectedRopeAngleOffset(value: Float) = updateSelectedRopeValue { it.copy(outwardAngleDeg = value.coerceIn(-180f, 180f)) }

    fun setSelectedRopeAnchorX(value: Float) = updateSelectedRopeValue { it.copy(localX = value) }

    fun setSelectedRopeAnchorY(value: Float) = updateSelectedRopeValue { it.copy(localY = value) }

    /** Canvas drag of a rope's stake; committed by [finishInteractionWithGridSnap] at drag end. */
    fun updateRopeStakeWorld(
        ropeIndex: Int,
        world: Vec2,
    ) {
        updateSelectedRope(ropeIndex) { asset, rope ->
            val anchorWorld = GeometryEngine.worldPoint(asset, Vec2(rope.localX, rope.localY))
            val dx = world.x - anchorWorld.x
            val dy = world.y - anchorWorld.y
            val len = sqrt(dx * dx + dy * dy).coerceIn(0.3f, 8.0f)
            val absoluteDeg = GeometryEngine.radToDeg(atan2(dy.toDouble(), dx.toDouble()))
            rope.copy(
                lengthMeters = len,
                outwardAngleDeg = normalizeDegrees(absoluteDeg - asset.rotationDeg),
            )
        }
        _uiState.update { it.copy(selectedRopeIndex = ropeIndex) }
    }

    /** Canvas drag of a rope's anchor; snaps to the preset's anchor slots. Committed at drag end. */
    fun updateRopeAnchorWorld(
        ropeIndex: Int,
        world: Vec2,
    ) {
        val presetsById = _uiState.value.presets.associateBy { it.id }
        updateSelectedRope(ropeIndex) { asset, rope ->
            val local = GeometryEngine.localPoint(asset, world)
            val snapped = presetsById[asset.presetId]?.let { GeometryEngine.snapLocalAnchorToPreset(local, it) } ?: local
            rope.copy(localX = snapped.x, localY = snapped.y)
        }
        _uiState.update { it.copy(selectedRopeIndex = ropeIndex) }
    }

    fun groupSelection() {
        val selected = _uiState.value.selectedAssetIds
        if (selected.size < 2) return
        val group = newId("group-")
        mutateProject {
            copy(assets = assets.map { if (it.id in selected) it.copy(groupId = group) else it })
        }
    }

    fun ungroupSelection() {
        updateSelectedUnlocked { it.copy(groupId = null) }
    }

    /** Deletes the selected assets except locked ones, which stay selected. */
    fun deleteSelection() {
        val state = _uiState.value
        val deletable =
            state.project.assets
                .filter { it.id in state.selectedAssetIds && !it.isLocked }
                .map { it.id }
                .toSet()
        if (deletable.isEmpty()) return
        mutateProject { copy(assets = assets.filterNot { it.id in deletable }) }
        _uiState.update { it.copy(selectedAssetIds = it.selectedAssetIds - deletable, selectedRopeIndex = null) }
    }

    fun duplicateSelection() {
        val state = _uiState.value
        val selected = state.project.assets.filter { it.id in state.selectedAssetIds }
        if (selected.isEmpty()) return
        val step = maxOf(state.project.gridMeters, 0.5f)
        val duplicates =
            selected.map { asset ->
                val preset = state.presets.firstOrNull { it.id == asset.presetId }
                val target = Vec2(asset.position.x + step, asset.position.y + step)
                val constrained =
                    if (preset != null) {
                        constrainAssetCenterToParcel(target, preset, state.project.parcel, asset.rotationDeg)
                    } else {
                        target
                    }
                asset.copy(
                    id = newId(),
                    label = if (asset.label.isBlank()) asset.label else "${asset.label} (Kopie)",
                    position = constrained,
                    // A copy starts unlocked so it can be moved away from the original.
                    isLocked = false,
                )
            }
        mutateProject { copy(assets = assets + duplicates) }
        _uiState.update { it.copy(selectedAssetIds = duplicates.map { asset -> asset.id }.toSet()) }
    }

    fun undo() {
        commitHistory()
        if (undoStack.size <= 1) return
        val current = undoStack.removeLast()
        redoStack.addLast(current)
        restoreFromHistory(undoStack.last())
    }

    fun redo() {
        commitHistory()
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(next)
        restoreFromHistory(next)
    }

    private fun restoreFromHistory(snapshot: PlannerProject) {
        _uiState.update {
            it.copy(
                // Undo restores content, not the view: keep the current zoom.
                project = snapshot.copy(pixelsPerMeter = it.project.pixelsPerMeter),
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
        replaceProject(loaded)
    }

    fun exportProjectFile() {
        storage.exportProjectFile(_uiState.value.project)
    }

    fun importProjectFile() {
        val imported = storage.importProjectFile() ?: return
        replaceProject(imported)
    }

    private fun replaceProject(project: PlannerProject) {
        val presets = _uiState.value.presets
        val presetsById = presets.associateBy { it.id }
        val clamped = clampAssetsToParcel(project, presetsById)
        _uiState.update {
            it.copy(
                project = clamped,
                selectedAssetIds = emptySet(),
                selectedRopeIndex = null,
            )
        }
        undoStack.clear()
        redoStack.clear()
        pushHistory(clamped)
        recomputeWarnings()
    }

    fun exportPng() {
        storage.exportProjectPng(_uiState.value.project, _uiState.value.presets)
    }

    fun saveInventory() {
        storage.saveInventory(_uiState.value.presets)
    }

    /**
     * Turns pending live edits into one undo step. Called when a slider is released, and implicitly
     * before every discrete edit and before undo/redo. No-op if nothing changed (zoom is ignored).
     */
    fun commitHistory() {
        val current = _uiState.value.project
        val lastCommitted = undoStack.lastOrNull()
        if (lastCommitted != null && sameIgnoringZoom(lastCommitted, current)) return
        pushHistory(current)
    }

    /**
     * @param foldLiveEdits true when [transform] finishes the pending live edit (drag end + grid snap),
     * so both become one undo step. Otherwise pending live edits stay a separate step.
     */
    private fun mutateProject(
        foldLiveEdits: Boolean = false,
        transform: PlannerProject.() -> PlannerProject,
    ) {
        if (!foldLiveEdits) commitHistory()
        updateProjectLive(transform)
        commitHistory()
    }

    private fun updateProjectLive(transform: PlannerProject.() -> PlannerProject) {
        val current = _uiState.value.project
        val next = current.transform()
        if (next == current) return
        _uiState.update { it.copy(project = next) }
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
                hiddenRopeIndices = report.hiddenRopeIndices,
            )
        }
    }

    /** Applies [transform] to every selected asset that isn't locked. */
    private fun updateSelectedUnlocked(
        live: Boolean = false,
        transform: (PlacedAsset) -> PlacedAsset,
    ) {
        val selected = _uiState.value.selectedAssetIds
        if (selected.isEmpty()) return
        val apply: PlannerProject.() -> PlannerProject = {
            copy(assets = assets.map { if (it.id in selected && !it.isLocked) transform(it) else it })
        }
        if (live) updateProjectLive(apply) else mutateProject(transform = apply)
    }

    /** Live edit of rope [ropeIndex] (default: the selected rope) on every selected, unlocked asset. */
    private fun updateSelectedRope(
        ropeIndex: Int? = _uiState.value.selectedRopeIndex,
        transform: (PlacedAsset, AssetRope) -> AssetRope,
    ) {
        val index = ropeIndex ?: return
        updateSelectedUnlocked(live = true) { asset ->
            if (index !in asset.ropes.indices) return@updateSelectedUnlocked asset
            asset.copy(ropes = asset.ropes.toMutableList().also { it[index] = transform(asset, it[index]) })
        }
    }

    private fun updateSelectedRopeValue(transform: (AssetRope) -> AssetRope) = updateSelectedRope { _, rope -> transform(rope) }

    /** Selected assets plus everything in their groups, minus locked assets: what a drag moves. */
    private fun movableAssetIds(state: PlannerUiState): Set<String> {
        val selected = state.selectedAssetIds
        if (selected.isEmpty()) return emptySet()
        val selectedGroups =
            state.project.assets
                .filter { it.id in selected }
                .mapNotNull { it.groupId }
                .toSet()
        return state.project.assets
            .filter { asset ->
                val inSelectedGroup = asset.groupId != null && asset.groupId in selectedGroups
                (asset.id in selected || inSelectedGroup) && !asset.isLocked
            }.map { it.id }
            .toSet()
    }

    private fun firstSelectedAsset(state: PlannerUiState): PlacedAsset? =
        state.project.assets.firstOrNull { it.id in state.selectedAssetIds }

    private fun defaultRopes(preset: TentPreset): List<AssetRope> = PlannerDomainOps.defaultRopes(preset)

    private fun newId(prefix: String = ""): String = prefix + Random.nextLong().toULong().toString(36)

    private fun sameIgnoringZoom(
        a: PlannerProject,
        b: PlannerProject,
    ): Boolean = a == b || a.copy(pixelsPerMeter = 0f) == b.copy(pixelsPerMeter = 0f)

    private fun rememberRecentPreset(
        current: List<String>,
        id: String,
    ): List<String> = PlannerDomainOps.rememberRecentPreset(current, id)

    private fun mergePresets(
        stored: List<TentPreset>?,
        defaults: List<TentPreset>,
    ): List<TentPreset> = PlannerDomainOps.mergePresets(stored, defaults)

    private fun normalizeDegrees(value: Float): Float = PlannerDomainOps.normalizeDegrees(value)

    private fun clampAssetsToParcel(
        project: PlannerProject,
        presetsById: Map<String, TentPreset>,
    ): PlannerProject = PlannerDomainOps.clampAssetsToParcel(project, presetsById)

    private fun adjustZoom(factor: Float) {
        setZoomLive(_uiState.value.project.pixelsPerMeter * factor)
    }

    private fun clampDeltaForAssetsInParcel(
        project: PlannerProject,
        presetsById: Map<String, TentPreset>,
        assetIds: Set<String>,
        requestedDelta: Vec2,
    ): Vec2 = PlannerDomainOps.clampDeltaForAssetsInParcel(project, presetsById, assetIds, requestedDelta)

    private companion object {
        const val DEFAULT_PIXELS_PER_METER = 50f
        const val MIN_PIXELS_PER_METER = 20f
        const val MAX_PIXELS_PER_METER = 180f
    }
}
