package de.voculupo.historicalcamplayout.features.planner.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import de.voculupo.historicalcamplayout.BlockZone
import de.voculupo.historicalcamplayout.FreeAreaKind
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.PlannerToolMode
import de.voculupo.historicalcamplayout.PlannerUiState
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.TentShapeType
import de.voculupo.historicalcamplayout.Vec2
import de.voculupo.historicalcamplayout.app.PresetDropRequest
import de.voculupo.historicalcamplayout.assetDisplayLabel
import de.voculupo.historicalcamplayout.buildPngExportScene
import de.voculupo.historicalcamplayout.platform.platformScrollZoom
import de.voculupo.historicalcamplayout.rememberPresetNames
import kotlin.math.atan2
import kotlin.math.sqrt

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun PlannerCanvas(
    state: PlannerUiState,
    shiftPressed: Boolean,
    dropRequest: PresetDropRequest?,
    onDropHandle: () -> Unit,
    suppressTapTemporarily: Boolean,
    onPlace: (Vec2) -> Unit,
    onPlacePresetAt: (String, Vec2) -> Unit,
    onSelection: (Set<String>) -> Unit,
    onSelectionToggle: (String?) -> Unit,
    onRopeSelect: (Int?) -> Unit,
    onRopeStakeDrag: (Int, Vec2) -> Unit,
    onRopeAnchorDrag: (Int, Vec2) -> Unit,
    onMeasureTap: (Vec2) -> Unit,
    onBlockZoneTap: (Vec2) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomContinuous: (Float) -> Unit,
    onRotateSelectedBy: (Float) -> Unit,
    onDrag: (Vec2) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    allowSingleFingerCanvasPan: Boolean = true,
    allowMultiTouchCanvasPan: Boolean = true,
) {
    val presetsById = remember(state.presets) { state.presets.associateBy { it.id } }
    val presetNames = rememberPresetNames(state.presets)
    val currentTool by rememberUpdatedState(state.currentTool)
    val currentParcel by rememberUpdatedState(state.project.parcel)
    val currentHiddenRopes by rememberUpdatedState(state.hiddenRopeIndices)
    val currentDropHandle by rememberUpdatedState(onDropHandle)
    val currentPlacePresetAt by rememberUpdatedState(onPlacePresetAt)
    // Gesture coroutines outlive recompositions; read everything through these so hit tests use the current plan and zoom.
    val currentState by rememberUpdatedState(state)
    val currentPresetsById by rememberUpdatedState(presetsById)
    val currentShiftPressed by rememberUpdatedState(shiftPressed)
    val currentSuppressTap by rememberUpdatedState(suppressTapTemporarily)
    val isDarkTheme = false
    val grassBrush = remember { ShaderBrush(ImageShader(grassTexture(), TileMode.Repeated, TileMode.Repeated)) }
    val textMeasurer = rememberTextMeasurer()
    var canvasRootRect by remember { mutableStateOf(Rect.Zero) }
    var activeRopeDrag by remember { mutableStateOf<Pair<Int, RopeDragPart>?>(null) }
    var activeAssetDrag by remember { mutableStateOf(false) }
    var activeCanvasPan by remember { mutableStateOf(false) }
    var canvasPanOffsetPx by remember { mutableStateOf(Offset.Zero) }
    var multiTouchActive by remember { mutableStateOf(false) }
    var pinchZoomPpm by remember { mutableStateOf(state.project.pixelsPerMeter) }
    var lastCommittedZoomPpm by remember { mutableStateOf(state.project.pixelsPerMeter) }
    val ropeUiScale = (state.project.pixelsPerMeter / 50f).coerceIn(1f, 2.4f)
    val density = LocalDensity.current.density
    val ropeHandleGrabRadiusPx = 28f * ropeUiScale * density
    val ropeHandleGuardRadiusPx = 40f * ropeUiScale * density
    val touchAssetHitSlopPx = 36f * density
    Box(
        modifier =
            modifier
                .clipToBounds(),
    ) {
        LaunchedEffect(state.project.pixelsPerMeter) {
            if (kotlin.math.abs(pinchZoomPpm - state.project.pixelsPerMeter) > 1f) {
                pinchZoomPpm = state.project.pixelsPerMeter
                lastCommittedZoomPpm = state.project.pixelsPerMeter
            }
        }
        LaunchedEffect(dropRequest, canvasRootRect, state.project.parcel, state.project.pixelsPerMeter) {
            val request = dropRequest ?: return@LaunchedEffect
            if (!canvasRootRect.contains(request.rootPosition)) {
                currentDropHandle()
                return@LaunchedEffect
            }
            val local = request.rootPosition - canvasRootRect.topLeft
            val parcelSizePx = parcelSizePx(state.project)
            val parcelOrigin =
                centeredParcelOriginPx(
                    Size(canvasRootRect.width, canvasRootRect.height),
                    parcelSizePx,
                ) + canvasPanOffsetPx
            val meters = GeometryEngine.offsetToMeters(local - parcelOrigin, state.project.pixelsPerMeter)
            currentPlacePresetAt(request.presetId, meters)
            currentDropHandle()
        }
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag(PLANNER_CANVAS_TAG)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInRoot()
                        canvasRootRect =
                            Rect(
                                left = p.x,
                                top = p.y,
                                right = p.x + coords.size.width,
                                bottom = p.y + coords.size.height,
                            )
                    }.background(Color(0xFFE9DCBF))
                    .platformScrollZoom(onZoomIn = onZoomIn, onZoomOut = onZoomOut)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            var prevDistance: Float? = null
                            var prevCentroid: Offset? = null
                            var prevAngleDeg: Float? = null
                            // Decided when the second finger lands: fingers on the selected tent rotate it,
                            // anywhere else zoom/pan the map. Never both at once.
                            var rotateMode = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size < 2) {
                                    if (multiTouchActive && rotateMode) onDragEnd()
                                    multiTouchActive = false
                                    rotateMode = false
                                    prevDistance = null
                                    prevCentroid = null
                                    prevAngleDeg = null
                                    continue
                                }
                                val live = currentState
                                val canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                                val p1 = pressed[0].position
                                val p2 = pressed[1].position
                                val centroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                                if (!multiTouchActive) {
                                    val origin = centeredParcelOriginPx(canvasSize, parcelSizePx(live.project)) + canvasPanOffsetPx
                                    rotateMode = live.currentTool == PlannerToolMode.Select &&
                                        listOf(p1, p2, centroid).any { point ->
                                            val hit = hitAssetAtPx(point, origin, live, currentPresetsById, touchAssetHitSlopPx)
                                            hit != null && hit.id in live.selectedAssetIds
                                        }
                                }
                                multiTouchActive = true

                                val distance = (p1 - p2).getDistance().coerceAtLeast(0.001f)
                                val angleDeg = GeometryEngine.radToDeg(atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble()))
                                val lastDistance = prevDistance
                                val lastCentroid = prevCentroid
                                val lastAngleDeg = prevAngleDeg
                                if (lastDistance != null && lastCentroid != null && lastAngleDeg != null) {
                                    if (rotateMode) {
                                        val rawDelta = angleDeg - lastAngleDeg
                                        val normalizedDelta =
                                            when {
                                                rawDelta > 180f -> rawDelta - 360f
                                                rawDelta < -180f -> rawDelta + 360f
                                                else -> rawDelta
                                            }
                                        if (kotlin.math.abs(normalizedDelta) > 0.05f) onRotateSelectedBy(normalizedDelta)
                                    } else {
                                        val pan = centroid - lastCentroid
                                        val zoom = (distance / lastDistance).coerceIn(0.9f, 1.1f)
                                        val oldPpm = pinchZoomPpm
                                        val newPpm = (oldPpm * zoom).coerceIn(20f, 180f)

                                        // Keep the world point under fingers stable while zooming to avoid jumpy map motion.
                                        val oldParcelSize =
                                            Size(
                                                width = currentParcel.widthMeters * oldPpm,
                                                height = currentParcel.heightMeters * oldPpm,
                                            )
                                        val oldOrigin = centeredParcelOriginPx(canvasSize, oldParcelSize) + canvasPanOffsetPx
                                        val worldAtCentroid = GeometryEngine.offsetToMeters(centroid - oldOrigin, oldPpm)
                                        val newParcelSize =
                                            Size(
                                                width = currentParcel.widthMeters * newPpm,
                                                height = currentParcel.heightMeters * newPpm,
                                            )
                                        val newCenteredOrigin = centeredParcelOriginPx(canvasSize, newParcelSize)
                                        val anchoredPan =
                                            centroid - GeometryEngine.metersToOffset(worldAtCentroid, newPpm) - newCenteredOrigin

                                        canvasPanOffsetPx = if (allowMultiTouchCanvasPan) anchoredPan + pan else anchoredPan

                                        if (kotlin.math.abs(zoom - 1f) > 0.001f) {
                                            pinchZoomPpm = newPpm
                                            if (kotlin.math.abs(newPpm - lastCommittedZoomPpm) > 0.08f) {
                                                onZoomContinuous(newPpm)
                                                lastCommittedZoomPpm = newPpm
                                            }
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                                prevDistance = distance
                                prevCentroid = centroid
                                prevAngleDeg = angleDeg
                            }
                        }
                    }.pointerInput(Unit) {
                        fun currentCanvasSize() = Size(size.width.toFloat(), size.height.toFloat())

                        fun currentParcelSizePx() = parcelSizePx(currentState.project)

                        fun parcelOriginPx() = centeredParcelOriginPx(currentCanvasSize(), currentParcelSizePx()) + canvasPanOffsetPx

                        fun metersAt(point: Offset): Vec2 =
                            GeometryEngine.offsetToMeters(
                                point - parcelOriginPx(),
                                currentState.project.pixelsPerMeter,
                            )

                        fun activeRopeHandlesForSelectedAsset(selectedAsset: PlacedAsset): List<RopeHandleHit> =
                            ropeHandleHits(
                                asset = selectedAsset,
                                pixelsPerMeter = currentState.project.pixelsPerMeter,
                                parcelOriginPx = parcelOriginPx(),
                                hiddenRopeIndices = currentHiddenRopes[selectedAsset.id].orEmpty(),
                            )

                        fun distancePointToSegmentPx(
                            point: Offset,
                            a: Offset,
                            b: Offset,
                        ): Float {
                            val ab = b - a
                            val ap = point - a
                            val abLen2 = ab.x * ab.x + ab.y * ab.y
                            if (abLen2 <= 0.001f) return (point - a).getDistance()
                            val t = ((ap.x * ab.x + ap.y * ab.y) / abLen2).coerceIn(0f, 1f)
                            val projection = Offset(a.x + ab.x * t, a.y + ab.y * t)
                            return (point - projection).getDistance()
                        }

                        fun pickRopeByLineHit(
                            selectedAsset: PlacedAsset,
                            tapPx: Offset,
                        ): Int? {
                            val lineHitRadiusPx = ropeHandleGuardRadiusPx.coerceAtLeast(30f)
                            val parcelOrigin = parcelOriginPx()
                            val nearest =
                                GeometryEngine
                                    .ropeSegments(selectedAsset)
                                    .mapIndexed { index, seg ->
                                        val startPx = seg.start.toOffsetPx(currentState.project.pixelsPerMeter, parcelOrigin)
                                        val endPx = seg.end.toOffsetPx(currentState.project.pixelsPerMeter, parcelOrigin)
                                        index to distancePointToSegmentPx(tapPx, startPx, endPx)
                                    }.minByOrNull { it.second }
                            val (index, distance) = nearest ?: return null
                            return if (distance <= lineHitRadiusPx) index else null
                        }

                        fun hitAssetAt(pointMeters: Vec2): PlacedAsset? =
                            hitAssetAtMeters(pointMeters, currentState, currentPresetsById, touchAssetHitSlopPx)
                        detectTapGestures(
                            onTap = { tap ->
                                if (currentSuppressTap) return@detectTapGestures
                                val tapMeters = metersAt(tap)
                                when (currentState.currentTool) {
                                    PlannerToolMode.PlaceTent -> {
                                        onPlace(tapMeters)
                                    }

                                    PlannerToolMode.Select -> {
                                        val selectedAsset =
                                            currentState.project.assets.firstOrNull {
                                                it.id in currentState.selectedAssetIds
                                            }
                                        if (selectedAsset != null) {
                                            val preset = currentPresetsById[selectedAsset.presetId]
                                            if (preset != null) {
                                                val ropeHandles = activeRopeHandlesForSelectedAsset(selectedAsset)
                                                // Tap selection should be more forgiving than drag grab,
                                                // otherwise near-misses clear selection on desktop.
                                                val ropeHit = pickRopeHandleHit(ropeHandles, tap, ropeHandleGuardRadiusPx)
                                                if (ropeHit != null) {
                                                    onRopeSelect(ropeHit.ropeIndex)
                                                    return@detectTapGestures
                                                }
                                                val lineHitRopeIndex = pickRopeByLineHit(selectedAsset, tap)
                                                if (lineHitRopeIndex != null) {
                                                    onRopeSelect(lineHitRopeIndex)
                                                    return@detectTapGestures
                                                }
                                            }
                                        }
                                        val hit = hitAssetAt(tapMeters)
                                        if (currentShiftPressed) {
                                            onSelectionToggle(hit?.id)
                                        } else {
                                            onSelection(
                                                hit?.let { setOf(it.id) } ?: emptySet(),
                                            )
                                        }
                                    }

                                    PlannerToolMode.Measure -> {
                                        onMeasureTap(tapMeters)
                                    }

                                    PlannerToolMode.BlockZone -> {
                                        onBlockZoneTap(tapMeters)
                                    }
                                }
                            },
                            onDoubleTap = { tap ->
                                if (currentState.currentTool == PlannerToolMode.Select) {
                                    val tapMeters = metersAt(tap)
                                    val hit = hitAssetAt(tapMeters)
                                    onSelectionToggle(hit?.id)
                                }
                            },
                        )
                    }.pointerInput(Unit) {
                        fun currentCanvasSize() = Size(size.width.toFloat(), size.height.toFloat())

                        fun parcelOriginPx() =
                            centeredParcelOriginPx(currentCanvasSize(), parcelSizePx(currentState.project)) + canvasPanOffsetPx

                        fun activeRopeHandlesForSelectedAsset(selectedAsset: PlacedAsset): List<RopeHandleHit> =
                            ropeHandleHits(
                                selectedAsset,
                                currentState.project.pixelsPerMeter,
                                parcelOriginPx(),
                                currentHiddenRopes[selectedAsset.id].orEmpty(),
                            )

                        fun hitAssetAt(pointMeters: Vec2): PlacedAsset? =
                            hitAssetAtMeters(pointMeters, currentState, currentPresetsById, touchAssetHitSlopPx)
                        detectDragGestures(
                            onDragStart = { start ->
                                if (currentState.currentTool != PlannerToolMode.Select) return@detectDragGestures
                                if (multiTouchActive) return@detectDragGestures
                                activeAssetDrag = false
                                activeCanvasPan = false
                                val startMeters =
                                    GeometryEngine.offsetToMeters(
                                        start - parcelOriginPx(),
                                        currentState.project.pixelsPerMeter,
                                    )
                                val selectedAsset = currentState.project.assets.firstOrNull { it.id in currentState.selectedAssetIds }
                                if (selectedAsset != null) {
                                    val selectedPreset = currentPresetsById[selectedAsset.presetId]
                                    if (selectedPreset != null) {
                                        val ropeHandles = activeRopeHandlesForSelectedAsset(selectedAsset)
                                        val ropeHit = pickRopeHandleHit(ropeHandles, start, ropeHandleGrabRadiusPx)
                                        if (ropeHit != null) {
                                            activeRopeDrag = ropeHit.ropeIndex to ropeHit.part
                                            onRopeSelect(ropeHit.ropeIndex)
                                            return@detectDragGestures
                                        }
                                    }
                                }
                                val hitAsset = hitAssetAt(startMeters)
                                if (hitAsset != null) {
                                    if (hitAsset.id !in currentState.selectedAssetIds) onSelection(setOf(hitAsset.id))
                                    activeAssetDrag = true
                                } else {
                                    activeCanvasPan = allowSingleFingerCanvasPan
                                }
                            },
                            onDrag = { change, dragAmount ->
                                // Two fingers down: rotate/pinch owns the gesture, the first finger must not also move things.
                                if (multiTouchActive) return@detectDragGestures
                                val ropeDrag = activeRopeDrag
                                if (ropeDrag != null && currentState.currentTool == PlannerToolMode.Select) {
                                    val meters =
                                        GeometryEngine.offsetToMeters(
                                            change.position - parcelOriginPx(),
                                            currentState.project.pixelsPerMeter,
                                        )
                                    if (ropeDrag.second ==
                                        RopeDragPart.Stake
                                    ) {
                                        onRopeStakeDrag(ropeDrag.first, meters)
                                    } else {
                                        onRopeAnchorDrag(ropeDrag.first, meters)
                                    }
                                    change.consume()
                                } else if (currentState.currentTool == PlannerToolMode.Select && activeAssetDrag) {
                                    onDrag(GeometryEngine.offsetToMeters(dragAmount, currentState.project.pixelsPerMeter))
                                    change.consume()
                                } else if (currentState.currentTool == PlannerToolMode.Select && activeCanvasPan) {
                                    canvasPanOffsetPx += dragAmount
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                activeRopeDrag = null
                                activeAssetDrag = false
                                activeCanvasPan = false
                                onDragEnd()
                            },
                            onDragCancel = {
                                activeRopeDrag = null
                                activeAssetDrag = false
                                activeCanvasPan = false
                            },
                        )
                    },
        ) {
            val parcelSizePx = parcelSizePx(state.project)
            val parcelOrigin = centeredParcelOriginPx(Size(size.width, size.height), parcelSizePx) + canvasPanOffsetPx
            drawCampPlan(
                project = state.project,
                presetsById = presetsById,
                presetNames = presetNames,
                hiddenRopeIndices = state.hiddenRopeIndices,
                origin = parcelOrigin,
                grassBrush = grassBrush,
                textMeasurer = textMeasurer,
                darkMode = isDarkTheme,
                selectedAssetIds = state.selectedAssetIds,
                selectedRopeIndex = state.selectedRopeIndex,
                hardCollisionAssetIds = state.hardCollisionAssetIds,
                warningAssetIds = state.warningAssetIds,
            )
            drawMeasurement(
                pixelsPerMeter = state.project.pixelsPerMeter,
                start = state.measurementStart,
                end = state.measurementEnd,
                origin = parcelOrigin,
                textMeasurer = textMeasurer,
                darkMode = isDarkTheme,
            )
            state.pendingBlockZoneStart?.let { start ->
                drawCircle(color = Color(0xFFAA3333), radius = 6f, center = start.toOffsetPx(state.project.pixelsPerMeter, parcelOrigin))
            }
        }
    }
}

private fun pickRopeHandleHit(
    handles: List<RopeHandleHit>,
    pointer: Offset,
    grabRadiusPx: Float,
): RopeHandleHit? {
    val inRange = handles.filter { (it.pointPx - pointer).getDistance() <= grabRadiusPx }
    if (inRange.isEmpty()) return null
    return inRange.minWithOrNull(
        compareBy<RopeHandleHit> {
            if (it.part ==
                RopeDragPart.Stake
            ) {
                0
            } else {
                1
            }
        }.thenBy { (it.pointPx - pointer).getDistance() },
    )
}

/** The plan as the canvas shows it; the PNG export draws the same thing without selection or warnings. */
private fun DrawScope.drawCampPlan(
    project: PlannerProject,
    presetsById: Map<String, TentPreset>,
    presetNames: Map<String, String>,
    hiddenRopeIndices: Map<String, Set<Int>>,
    origin: Offset,
    grassBrush: Brush,
    textMeasurer: TextMeasurer,
    darkMode: Boolean = false,
    selectedAssetIds: Set<String> = emptySet(),
    selectedRopeIndex: Int? = null,
    hardCollisionAssetIds: Set<String> = emptySet(),
    warningAssetIds: Set<String> = emptySet(),
) {
    val ppm = project.pixelsPerMeter
    drawGround(grassBrush, ppm, origin, darkMode)
    drawGrid(project, origin, size)
    drawParcelBounds(project, origin)
    project.blockZones.forEach { drawBlockZone(it, ppm, origin, textMeasurer) }
    val selectedGroupIds =
        project.assets
            .filter { it.id in selectedAssetIds }
            .mapNotNull { it.groupId }
            .toSet()
    project.assets.sortedBy { it.heightRange.maxMeters }.forEach { asset ->
        val preset = presetsById[asset.presetId] ?: return@forEach
        val highlighted = asset.id in selectedAssetIds || (asset.groupId != null && asset.groupId in selectedGroupIds)
        drawAsset(
            asset,
            preset,
            ppm,
            highlighted,
            asset.id in warningAssetIds,
            asset.id in hardCollisionAssetIds,
            origin,
            selectedRopeIndex,
            hiddenRopeIndices[asset.id].orEmpty(),
        )
        if (asset.id in selectedAssetIds) drawAnchorSlots(asset, preset, ppm, origin)
        drawEntryArrow(asset, preset, ppm, origin)
        drawAssetLabel(asset, preset, presetNames.getValue(preset.id), ppm, origin, highlighted, textMeasurer, darkMode)
    }
    drawParcelDimensionGuides(project, origin, textMeasurer, darkMode)
}

/** Renders the whole plan off-screen in the canvas look, sized to fit every tent and rope. */
internal fun renderCampPng(
    project: PlannerProject,
    presets: List<TentPreset>,
    presetNames: Map<String, String>,
    textMeasurer: TextMeasurer,
    density: Density,
): ImageBitmap {
    val frame = buildPngExportScene(project, presets)
    val image = ImageBitmap(frame.widthPx, frame.heightPx)
    val scaled = project.copy(pixelsPerMeter = frame.pixelsPerMeter)
    val size = Size(frame.widthPx.toFloat(), frame.heightPx.toFloat())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), size) {
        drawCampPlan(
            project = scaled,
            presetsById = presets.associateBy { it.id },
            presetNames = presetNames,
            hiddenRopeIndices = GeometryEngine.joinHiddenRopeIndices(scaled, presets),
            origin = frame.parcel.topLeft,
            grassBrush = ShaderBrush(ImageShader(grassTexture(), TileMode.Repeated, TileMode.Repeated)),
            textMeasurer = textMeasurer,
        )
    }
    return image
}

private fun DrawScope.drawAssetLabel(
    asset: PlacedAsset,
    preset: TentPreset,
    presetName: String,
    pixelsPerMeter: Float,
    origin: Offset,
    selected: Boolean,
    textMeasurer: TextMeasurer,
    darkMode: Boolean,
) {
    val raw = assetDisplayLabel(asset, presetName).trim()
    if (raw.isEmpty()) return
    val label = if (raw.length > 30) raw.take(29) + "…" else raw
    val meta = "${preset.shape.name} • ${"%.1f".format(preset.widthMeters)} x ${"%.1f".format(preset.heightMeters)} m"
    val center = GeometryEngine.metersToOffset(asset.position, pixelsPerMeter) + origin
    val normalText = if (darkMode) Color(0xFFE6DCC9) else Color(0xFF1F2933)
    val selectedText = if (darkMode) Color(0xFFCFE5FF) else Color(0xFF3A0E05)
    val metaText = if (darkMode) Color(0xBFD8CCB8) else Color(0xAA1F2933)
    val labelBg =
        when {
            selected && darkMode -> Color(0xCC253447)
            selected -> Color(0xFFF6DACB)
            darkMode -> Color(0xCC3A3329)
            else -> Color(0xDDEFE7D2)
        }
    val titleLayout =
        textMeasurer.measure(
            AnnotatedString(label),
            style = TextStyle(color = if (selected) selectedText else normalText, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
    val metaLayout =
        textMeasurer.measure(
            AnnotatedString(meta),
            style = TextStyle(color = metaText, fontSize = 10.sp, fontWeight = FontWeight.Normal),
        )
    val textWidth = maxOf(titleLayout.size.width, metaLayout.size.width)
    val totalTextHeight = titleLayout.size.height + 2f + metaLayout.size.height
    val topLeft = Offset(center.x - textWidth / 2f, center.y - totalTextHeight / 2f)
    drawRoundRect(
        color = labelBg,
        topLeft = Offset(topLeft.x - 5f, topLeft.y - 3f),
        size = Size(textWidth + 10f, totalTextHeight + 6f),
        cornerRadius = CornerRadius(6f, 6f),
    )
    val metaTop = Offset(topLeft.x, topLeft.y + titleLayout.size.height + 2f)
    drawText(titleLayout, topLeft = topLeft)
    drawText(metaLayout, topLeft = Offset(metaTop.x + 1f, metaTop.y + 1f), color = Color(0x66000000))
    drawText(metaLayout, topLeft = metaTop)
}

/** Small arrow just outside the entry, pointing out of the tent. */
private fun DrawScope.drawEntryArrow(
    asset: PlacedAsset,
    preset: TentPreset,
    pixelsPerMeter: Float,
    origin: Offset,
) {
    val (entry, out) = GeometryEngine.entryWorld(asset, preset) ?: return
    val side = Vec2(-out.y, out.x)

    fun px(
        along: Float,
        across: Float,
    ) = Offset(
        origin.x + (entry.x + out.x * along + side.x * across) * pixelsPerMeter,
        origin.y + (entry.y + out.y * along + side.y * across) * pixelsPerMeter,
    )
    val arrow =
        Path().apply {
            moveTo(px(0.75f, 0f).x, px(0.75f, 0f).y)
            lineTo(px(0.2f, 0.35f).x, px(0.2f, 0.35f).y)
            lineTo(px(0.2f, -0.35f).x, px(0.2f, -0.35f).y)
            close()
        }
    drawPath(arrow, color = Color(0xDD3A0E05), style = Fill)
}

private fun DrawScope.drawAnchorSlots(
    asset: PlacedAsset,
    preset: TentPreset,
    pixelsPerMeter: Float,
    origin: Offset,
) {
    val center = GeometryEngine.metersToOffset(asset.position, pixelsPerMeter) + origin
    rotate(asset.rotationDeg, center) {
        GeometryEngine.anchorSlotsForPreset(preset).forEach { slot ->
            drawCircle(
                color = Color(0xFF8C2F1E).copy(alpha = 0.55f),
                center =
                    Offset(
                        center.x + slot.x * pixelsPerMeter,
                        center.y + slot.y * pixelsPerMeter,
                    ),
                radius = 3f,
            )
        }
    }
}

private fun DrawScope.drawGrid(
    project: PlannerProject,
    origin: Offset,
    canvasSizePx: Size,
) {
    val spacing = project.gridMeters * project.pixelsPerMeter
    if (spacing < 8f) return
    var x = ((origin.x % spacing) + spacing) % spacing
    while (x <= canvasSizePx.width) {
        drawLine(GRID_INK, Offset(x, 0f), Offset(x, canvasSizePx.height), 1f)
        x += spacing
    }
    var y = ((origin.y % spacing) + spacing) % spacing
    while (y <= canvasSizePx.height) {
        drawLine(GRID_INK, Offset(0f, y), Offset(canvasSizePx.width, y), 1f)
        y += spacing
    }
}

private fun DrawScope.drawParcelBounds(
    project: PlannerProject,
    origin: Offset,
) {
    val w = project.parcel.widthMeters * project.pixelsPerMeter
    val h = project.parcel.heightMeters * project.pixelsPerMeter
    // Fresh meadow inside, marked out with a taut light string and corner stakes.
    drawRect(color = Color(0x1AFFF8DC), topLeft = origin, size = Size(w, h))
    drawRect(color = Color(0x55000000), topLeft = origin + Offset(1f, 1.5f), size = Size(w, h), style = Stroke(width = 2f))
    drawRect(color = PARCEL_STRING, topLeft = origin, size = Size(w, h), style = Stroke(width = 2f))
    listOf(origin, origin + Offset(w, 0f), origin + Offset(0f, h), origin + Offset(w, h)).forEach {
        drawCircle(STAKE, radius = 4f, center = it)
    }
}

private val GRID_INK = Color(0x1CFFFFFF)
private val PARCEL_STRING = Color(0xEEF4EBD0)
private val ROPE = Color(0xFFD9C8A0)
private val STAKE = Color(0xFF3B2A1A)

/** Grass that moves and zooms with the plan, darker towards the screen edges, dimmed at night (dark theme). */
private fun DrawScope.drawGround(
    brush: Brush,
    pixelsPerMeter: Float,
    origin: Offset,
    darkMode: Boolean,
) {
    val k = (pixelsPerMeter * GRASS_TILE_METERS / GRASS_TILE_PX).coerceIn(0.6f, 2f)
    val tile = GRASS_TILE_PX * k
    val shift = Offset(((origin.x % tile) + tile) % tile, ((origin.y % tile) + tile) % tile)
    translate(shift.x - tile, shift.y - tile) {
        scale(k, pivot = Offset.Zero) {
            drawRect(brush, size = Size((size.width + tile * 2) / k, (size.height + tile * 2) / k))
        }
    }
    drawRect(
        Brush.radialGradient(
            0.5f to Color.Transparent,
            1f to Color(0x55142008),
            center = center,
            radius = maxOf(size.width, size.height) * 0.75f,
        ),
    )
    if (darkMode) drawRect(Color(0x66000000))
}

private fun DrawScope.drawBlockZone(
    zone: BlockZone,
    pixelsPerMeter: Float,
    origin: Offset,
    textMeasurer: TextMeasurer,
) {
    if (zone.points.size < 3) return
    if (zone.kind == FreeAreaKind.FIRE) {
        val c =
            Offset(
                origin.x + zone.points
                    .map { it.x }
                    .average()
                    .toFloat() * pixelsPerMeter,
                origin.y + zone.points
                    .map { it.y }
                    .average()
                    .toFloat() * pixelsPerMeter,
            )
        val r = zone.points.maxOf { (it.toOffsetPx(pixelsPerMeter, origin) - c).getDistance() }
        drawCampfire(c, r)
        if (zone.label.isNotBlank()) {
            val layout = textMeasurer.measure(AnnotatedString(zone.label), style = TextStyle(color = Color(0xFFF4EBD0), fontSize = 12.sp))
            drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y + r + 2f))
        }
        return
    }
    val path =
        Path().apply {
            moveTo(origin.x + zone.points.first().x * pixelsPerMeter, origin.y + zone.points.first().y * pixelsPerMeter)
            zone.points.drop(1).forEach { lineTo(origin.x + it.x * pixelsPerMeter, origin.y + it.y * pixelsPerMeter) }
            close()
        }
    drawPath(path, color = Color(0x66AA0000), style = Fill)
    if (zone.label.isBlank()) return
    val style = TextStyle(color = Color(0xFF3A0E05), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val layout = textMeasurer.measure(AnnotatedString(zone.label), style = style)
    val cx = origin.x + zone.points.sumOf { it.x.toDouble() }.toFloat() / zone.points.size * pixelsPerMeter
    val cy = origin.y + zone.points.sumOf { it.y.toDouble() }.toFloat() / zone.points.size * pixelsPerMeter
    drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
}

/** Extra outline so selection stays visible on warning/collision fills and on POLYGON presets. */
private fun DrawScope.drawSelectionHighlightRing(
    preset: TentPreset,
    center: Offset,
    pixelsPerMeter: Float,
) {
    val ring = Color(0xFFC9962B)
    val sw = 3.5f
    when (preset.shape) {
        TentShapeType.CIRCLE -> {
            val r = preset.widthMeters * pixelsPerMeter / 2f
            drawCircle(color = ring, radius = r, center = center, style = Stroke(width = sw))
        }

        TentShapeType.RECTANGLE -> {
            val rect =
                Rect(
                    center.x - preset.widthMeters * pixelsPerMeter / 2f,
                    center.y - preset.heightMeters * pixelsPerMeter / 2f,
                    center.x + preset.widthMeters * pixelsPerMeter / 2f,
                    center.y + preset.heightMeters * pixelsPerMeter / 2f,
                )
            drawRect(color = ring, topLeft = rect.topLeft, size = rect.size, style = Stroke(width = sw))
        }

        TentShapeType.CAPSULE -> {
            val radius = preset.heightMeters * pixelsPerMeter / 2f
            val halfBody = (preset.widthMeters * pixelsPerMeter / 2f) - radius
            drawRect(
                color = ring,
                topLeft = Offset(center.x - halfBody, center.y - radius),
                size = Size(halfBody * 2f, radius * 2f),
                style = Stroke(width = sw),
            )
            drawCircle(color = ring, radius = radius, center = Offset(center.x - halfBody, center.y), style = Stroke(width = sw))
            drawCircle(color = ring, radius = radius, center = Offset(center.x + halfBody, center.y), style = Stroke(width = sw))
        }

        TentShapeType.SAXON -> {
            if (preset.polygonPoints.isNotEmpty()) {
                val points = preset.polygonPoints
                val path =
                    Path().apply {
                        moveTo(center.x + points.first().x * pixelsPerMeter, center.y + points.first().y * pixelsPerMeter)
                        points.drop(1).forEach { lineTo(center.x + it.x * pixelsPerMeter, center.y + it.y * pixelsPerMeter) }
                        close()
                    }
                drawPath(path, color = ring, style = Stroke(width = sw))
            } else {
                val radius = preset.heightMeters * pixelsPerMeter / 2f
                val halfBody = (preset.widthMeters * pixelsPerMeter / 2f) - radius
                drawRect(
                    color = ring,
                    topLeft = Offset(center.x - halfBody, center.y - radius),
                    size = Size(halfBody * 2f, radius * 2f),
                    style = Stroke(width = sw),
                )
                drawCircle(color = ring, radius = radius, center = Offset(center.x - halfBody, center.y), style = Stroke(width = sw))
                drawCircle(color = ring, radius = radius, center = Offset(center.x + halfBody, center.y), style = Stroke(width = sw))
            }
        }

        TentShapeType.A_FRAME -> {
            val halfW = preset.widthMeters * pixelsPerMeter / 2f
            val halfH = preset.heightMeters * pixelsPerMeter / 2f
            val path =
                Path().apply {
                    moveTo(center.x - halfW, center.y - halfH * 0.9f)
                    lineTo(center.x + halfW, center.y - halfH * 0.55f)
                    lineTo(center.x + halfW, center.y + halfH * 0.55f)
                    lineTo(center.x - halfW, center.y + halfH * 0.9f)
                    close()
                }
            drawPath(path, color = ring, style = Stroke(width = sw))
        }

        TentShapeType.POLYGON -> {
            val points = preset.polygonPoints.ifEmpty { listOf(Vec2(-2f, -2f), Vec2(2f, -2f), Vec2(2f, 2f), Vec2(-2f, 2f)) }
            val path =
                Path().apply {
                    moveTo(center.x + points.first().x * pixelsPerMeter, center.y + points.first().y * pixelsPerMeter)
                    points.drop(1).forEach { lineTo(center.x + it.x * pixelsPerMeter, center.y + it.y * pixelsPerMeter) }
                    close()
                }
            drawPath(path, color = ring, style = Stroke(width = sw))
        }
    }
}

private fun DrawScope.drawAsset(
    asset: PlacedAsset,
    preset: TentPreset,
    pixelsPerMeter: Float,
    selectionHighlighted: Boolean,
    warning: Boolean,
    hardCollision: Boolean,
    origin: Offset,
    selectedRopeIndex: Int?,
    hiddenRopeIndices: Set<Int>,
) {
    val tint =
        when {
            hardCollision -> Color(0x99DD2C2C)
            warning -> Color(0x88E0A030)
            selectionHighlighted -> Color(0x33C9962B)
            else -> null
        }
    val outlineColor =
        when {
            hardCollision -> Color(0xFF8A0000)
            warning -> Color(0xFF8A6B00)
            selectionHighlighted -> Color(0xFF3A0E05)
            else -> CANVAS_EDGE
        }
    val center = GeometryEngine.metersToOffset(asset.position, pixelsPerMeter) + origin
    drawSafetyBuffer(asset, preset, center, pixelsPerMeter)
    rotate(asset.rotationDeg, center) {
        drawTentArt(
            preset = preset,
            outline = GeometryEngine.localOutlinePoints(preset),
            center = center,
            ppm = pixelsPerMeter,
            rotationDeg = asset.rotationDeg,
            heightMeters = asset.heightRange.maxMeters,
            tint = tint,
            edge = outlineColor,
        )
        if (selectionHighlighted) {
            drawSelectionHighlightRing(
                preset = preset,
                center = center,
                pixelsPerMeter = pixelsPerMeter,
            )
        }
    }
    if (asset.showRopes) {
        val ropeUiScale = (pixelsPerMeter / 50f).coerceIn(1f, 2.4f)
        GeometryEngine.visibleRopeSegments(asset, hiddenRopeIndices).forEach { (idx, rope) ->
            val isSelectedRope = selectedRopeIndex == idx
            drawLine(
                color = if (isSelectedRope) Color(0xFF1B4D9B) else ROPE,
                start = rope.start.toOffsetPx(pixelsPerMeter, origin),
                end = rope.end.toOffsetPx(pixelsPerMeter, origin),
                strokeWidth = if (isSelectedRope) 2.8f * ropeUiScale else 1.5f * ropeUiScale,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = if (isSelectedRope) Color(0xFF1B4D9B) else ROPE,
                center = rope.start.toOffsetPx(pixelsPerMeter, origin),
                radius = if (isSelectedRope) 5.5f * ropeUiScale else 2.5f * ropeUiScale,
            )
            drawCircle(
                color = if (isSelectedRope) Color(0xFF1B4D9B) else STAKE,
                center = rope.end.toOffsetPx(pixelsPerMeter, origin),
                radius = if (isSelectedRope) 6.5f * ropeUiScale else 4.5f * ropeUiScale,
            )
        }
    }
}

private fun DrawScope.drawSafetyBuffer(
    asset: PlacedAsset,
    preset: TentPreset,
    center: Offset,
    pixelsPerMeter: Float,
) {
    val bufferColor = Color(0x24FFFFFF)
    val marginPx = asset.safetyMarginMeters * pixelsPerMeter
    rotate(asset.rotationDeg, center) {
        when (preset.shape) {
            TentShapeType.CIRCLE -> {
                drawCircle(
                    color = bufferColor,
                    radius = preset.widthMeters * pixelsPerMeter / 2f + marginPx,
                    center = center,
                )
            }

            TentShapeType.RECTANGLE, TentShapeType.A_FRAME -> {
                val halfW = preset.widthMeters * pixelsPerMeter / 2f + marginPx
                val halfH = preset.heightMeters * pixelsPerMeter / 2f + marginPx
                drawRect(
                    color = bufferColor,
                    topLeft = Offset(center.x - halfW, center.y - halfH),
                    size = Size(halfW * 2f, halfH * 2f),
                    style = Fill,
                )
            }

            TentShapeType.CAPSULE -> {
                val radius = preset.heightMeters * pixelsPerMeter / 2f + marginPx
                val halfBody = (preset.widthMeters * pixelsPerMeter / 2f) - (preset.heightMeters * pixelsPerMeter / 2f)
                val expandedHalfBody = halfBody + marginPx
                drawRect(
                    color = bufferColor,
                    topLeft = Offset(center.x - expandedHalfBody, center.y - radius),
                    size =
                        Size(
                            expandedHalfBody * 2f,
                            radius * 2f,
                        ),
                    style = Fill,
                )
                drawCircle(bufferColor, radius = radius, center = Offset(center.x - expandedHalfBody, center.y))
                drawCircle(bufferColor, radius = radius, center = Offset(center.x + expandedHalfBody, center.y))
            }

            TentShapeType.SAXON -> {
                if (preset.polygonPoints.isNotEmpty()) {
                    val points = preset.polygonPoints
                    val avgRadius =
                        points
                            .map { sqrt(it.x * it.x + it.y * it.y) }
                            .average()
                            .toFloat()
                            .coerceAtLeast(0.001f)
                    val scale = (avgRadius + asset.safetyMarginMeters) / avgRadius
                    val path =
                        Path().apply {
                            moveTo(
                                center.x + points.first().x * pixelsPerMeter * scale,
                                center.y + points.first().y * pixelsPerMeter * scale,
                            )
                            points
                                .drop(
                                    1,
                                ).forEach { lineTo(center.x + it.x * pixelsPerMeter * scale, center.y + it.y * pixelsPerMeter * scale) }
                            close()
                        }
                    drawPath(path, color = bufferColor, style = Fill)
                } else {
                    val radius = preset.heightMeters * pixelsPerMeter / 2f + marginPx
                    val halfBody = (preset.widthMeters * pixelsPerMeter / 2f) - (preset.heightMeters * pixelsPerMeter / 2f)
                    val expandedHalfBody = halfBody + marginPx
                    drawRect(
                        color = bufferColor,
                        topLeft = Offset(center.x - expandedHalfBody, center.y - radius),
                        size =
                            Size(
                                expandedHalfBody * 2f,
                                radius * 2f,
                            ),
                        style = Fill,
                    )
                    drawCircle(bufferColor, radius = radius, center = Offset(center.x - expandedHalfBody, center.y))
                    drawCircle(bufferColor, radius = radius, center = Offset(center.x + expandedHalfBody, center.y))
                }
            }

            TentShapeType.POLYGON -> {
                val points =
                    if (preset.polygonPoints.isEmpty()) {
                        listOf(
                            Vec2(-2f, -2f),
                            Vec2(2f, -2f),
                            Vec2(2f, 2f),
                            Vec2(-2f, 2f),
                        )
                    } else {
                        preset.polygonPoints
                    }
                val avgRadius =
                    points
                        .map { sqrt(it.x * it.x + it.y * it.y) }
                        .average()
                        .toFloat()
                        .coerceAtLeast(0.001f)
                val scale = (avgRadius + asset.safetyMarginMeters) / avgRadius
                val path =
                    Path().apply {
                        moveTo(center.x + points.first().x * pixelsPerMeter * scale, center.y + points.first().y * pixelsPerMeter * scale)
                        points
                            .drop(
                                1,
                            ).forEach { lineTo(center.x + it.x * pixelsPerMeter * scale, center.y + it.y * pixelsPerMeter * scale) }
                        close()
                    }
                drawPath(path, color = bufferColor, style = Fill)
            }
        }
    }
}

private fun DrawScope.drawParcelDimensionGuides(
    project: PlannerProject,
    origin: Offset,
    textMeasurer: TextMeasurer,
    darkMode: Boolean,
) {
    val widthPx = project.parcel.widthMeters * project.pixelsPerMeter
    val heightPx = project.parcel.heightMeters * project.pixelsPerMeter
    val leftX = (origin.x - 20f).coerceAtLeast(8f)

    val guideColor = if (darkMode) Color(0xBFAFA087) else Color(0xAA4A5A4A)
    val guideTextColor = if (darkMode) Color(0xFFE6DCC9) else Color(0xFF2F261A)
    val guideBgColor = if (darkMode) Color(0xCC3A3329) else Color(0xDDF2EADB)
    val widthLabel =
        textMeasurer.measure(
            AnnotatedString("${"%.1f".format(project.parcel.widthMeters)} m"),
            style = TextStyle(color = guideTextColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
    // The width label sits above the guide line; keep it inside the image when the parcel touches the top edge.
    val topY = (origin.y - 20f).coerceAtLeast(widthLabel.size.height + 8f)

    drawLine(
        color = guideColor,
        start = Offset(origin.x, topY),
        end = Offset(origin.x + widthPx, topY),
        strokeWidth = 2f,
    )
    drawLine(guideColor, Offset(origin.x, topY - 6f), Offset(origin.x, topY + 6f), 2f)
    drawLine(guideColor, Offset(origin.x + widthPx, topY - 6f), Offset(origin.x + widthPx, topY + 6f), 2f)

    drawRoundRect(
        color = guideBgColor,
        topLeft =
            Offset(
                x = origin.x + widthPx / 2f - widthLabel.size.width / 2f - 6f,
                y = topY - widthLabel.size.height - 4f,
            ),
        size = Size(widthLabel.size.width + 12f, widthLabel.size.height + 6f),
        cornerRadius = CornerRadius(6f, 6f),
    )
    drawText(
        widthLabel,
        topLeft =
            Offset(
                x = origin.x + widthPx / 2f - widthLabel.size.width / 2f,
                y = topY - widthLabel.size.height - 1f,
            ),
    )

    drawLine(
        color = guideColor,
        start = Offset(leftX, origin.y),
        end = Offset(leftX, origin.y + heightPx),
        strokeWidth = 2f,
    )
    drawLine(guideColor, Offset(leftX - 6f, origin.y), Offset(leftX + 6f, origin.y), 2f)
    drawLine(guideColor, Offset(leftX - 6f, origin.y + heightPx), Offset(leftX + 6f, origin.y + heightPx), 2f)

    val heightLabel =
        textMeasurer.measure(
            AnnotatedString("${"%.1f".format(project.parcel.heightMeters)} m"),
            style = TextStyle(color = guideTextColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
    drawRoundRect(
        color = guideBgColor,
        topLeft =
            Offset(
                x = leftX + 6f,
                y = origin.y + heightPx / 2f - heightLabel.size.height / 2f - 3f,
            ),
        size = Size(heightLabel.size.width + 12f, heightLabel.size.height + 6f),
        cornerRadius = CornerRadius(6f, 6f),
    )
    drawText(
        heightLabel,
        topLeft =
            Offset(
                x = leftX + 12f,
                y = origin.y + heightPx / 2f - heightLabel.size.height / 2f,
            ),
    )
}

private fun DrawScope.drawMeasurement(
    pixelsPerMeter: Float,
    start: Vec2?,
    end: Vec2?,
    origin: Offset,
    textMeasurer: TextMeasurer,
    darkMode: Boolean,
) {
    if (start == null || end == null) return
    val s = start.toOffsetPx(pixelsPerMeter, origin)
    val e = end.toOffsetPx(pixelsPerMeter, origin)
    drawLine(Color(0xFF0F4C81), s, e, strokeWidth = 3f)
    drawCircle(Color(0xFF0F4C81), center = s, radius = 5f)
    drawCircle(Color(0xFF0F4C81), center = e, radius = 5f)

    val distance = GeometryEngine.distance(start, end)
    val mid = Offset((s.x + e.x) / 2f, (s.y + e.y) / 2f)
    val measureTextColor = if (darkMode) Color(0xFFCFE5FF) else Color(0xFF0F4C81)
    val measureBgColor = if (darkMode) Color(0xCC253447) else Color(0xE7EAF4FF)
    val measureLabel =
        textMeasurer.measure(
            AnnotatedString("${"%.2f".format(distance)} m"),
            style = TextStyle(color = measureTextColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
    drawRoundRect(
        color = measureBgColor,
        topLeft = Offset(mid.x - measureLabel.size.width / 2f - 6f, mid.y - measureLabel.size.height - 8f),
        size = Size(measureLabel.size.width + 12f, measureLabel.size.height + 6f),
        cornerRadius = CornerRadius(6f, 6f),
    )
    drawText(
        measureLabel,
        topLeft = Offset(mid.x - measureLabel.size.width / 2f, mid.y - measureLabel.size.height - 5f),
    )
}

private fun hitAssetAtMeters(
    pointMeters: Vec2,
    state: PlannerUiState,
    presetsById: Map<String, TentPreset>,
    slopPx: Float,
): PlacedAsset? {
    val marginMeters = slopPx / state.project.pixelsPerMeter
    return state.project.assets.lastOrNull { asset ->
        val preset = presetsById[asset.presetId] ?: return@lastOrNull false
        if (GeometryEngine.isPointInAsset(pointMeters, asset, preset)) return@lastOrNull true
        val body = GeometryEngine.assetBodyAabb(asset, preset)
        pointMeters.x in (body.left - marginMeters)..(body.right + marginMeters) &&
            pointMeters.y in (body.top - marginMeters)..(body.bottom + marginMeters)
    }
}

private fun hitAssetAtPx(
    pointPx: Offset,
    parcelOriginPx: Offset,
    state: PlannerUiState,
    presetsById: Map<String, TentPreset>,
    slopPx: Float,
): PlacedAsset? =
    hitAssetAtMeters(
        GeometryEngine.offsetToMeters(pointPx - parcelOriginPx, state.project.pixelsPerMeter),
        state,
        presetsById,
        slopPx,
    )
