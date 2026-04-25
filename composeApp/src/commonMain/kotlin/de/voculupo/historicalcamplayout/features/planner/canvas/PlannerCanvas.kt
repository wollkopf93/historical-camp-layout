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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import de.voculupo.historicalcamplayout.BlockZone
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.PlacedAsset
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.PlannerToolMode
import de.voculupo.historicalcamplayout.PlannerUiState
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.TentShapeType
import de.voculupo.historicalcamplayout.Vec2
import de.voculupo.historicalcamplayout.app.PresetDropRequest
import de.voculupo.historicalcamplayout.platform.platformScrollZoom
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
    val currentTool by rememberUpdatedState(state.currentTool)
    val currentSelectedAssetIds by rememberUpdatedState(state.selectedAssetIds)
    val currentParcel by rememberUpdatedState(state.project.parcel)
    val currentDropHandle by rememberUpdatedState(onDropHandle)
    val currentPlacePresetAt by rememberUpdatedState(onPlacePresetAt)
    val isDarkTheme = false
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
    val ropeHandleGrabRadiusPx = 28f * ropeUiScale
    val ropeHandleGuardRadiusPx = 40f * ropeUiScale
    val touchAssetHitSlopPx = 36f
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
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInRoot()
                        canvasRootRect =
                            Rect(
                                left = p.x,
                                top = p.y,
                                right = p.x + coords.size.width,
                                bottom = p.y + coords.size.height,
                            )
                    }.background(Color(0xFFEAE1CF))
                    .platformScrollZoom(onZoomIn = onZoomIn, onZoomOut = onZoomOut)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            var prevDistance: Float? = null
                            var prevCentroid: Offset? = null
                            var prevAngleDeg: Float? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size < 2) {
                                    multiTouchActive = false
                                    prevDistance = null
                                    prevCentroid = null
                                    prevAngleDeg = null
                                    continue
                                }
                                multiTouchActive = true

                                val p1 = pressed[0].position
                                val p2 = pressed[1].position
                                val centroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                                val distance = (p1 - p2).getDistance().coerceAtLeast(0.001f)
                                val angleDeg = Math.toDegrees(atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())).toFloat()
                                val lastDistance = prevDistance
                                val lastCentroid = prevCentroid
                                val lastAngleDeg = prevAngleDeg
                                if (lastDistance != null && lastCentroid != null && lastAngleDeg != null) {
                                    val pan = centroid - lastCentroid
                                    val zoom = (distance / lastDistance).coerceIn(0.9f, 1.1f)
                                    val rawDelta = angleDeg - lastAngleDeg
                                    val normalizedDelta =
                                        when {
                                            rawDelta > 180f -> rawDelta - 360f
                                            rawDelta < -180f -> rawDelta + 360f
                                            else -> rawDelta
                                        }

                                    val oldPpm = pinchZoomPpm
                                    val newPpm = (oldPpm * zoom).coerceIn(20f, 180f)

                                    // Keep the world point under fingers stable while zooming to avoid jumpy map motion.
                                    val canvasSize = Size(size.width.toFloat(), size.height.toFloat())
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
                                    val anchoredPan = centroid - GeometryEngine.metersToOffset(worldAtCentroid, newPpm) - newCenteredOrigin

                                    canvasPanOffsetPx = if (allowMultiTouchCanvasPan) anchoredPan + pan else anchoredPan

                                    if (kotlin.math.abs(zoom - 1f) > 0.001f) {
                                        pinchZoomPpm = newPpm
                                        if (kotlin.math.abs(newPpm - lastCommittedZoomPpm) > 0.08f) {
                                            onZoomContinuous(newPpm)
                                            lastCommittedZoomPpm = newPpm
                                        }
                                    }

                                    val hasSelectedAsset = currentTool == PlannerToolMode.Select && currentSelectedAssetIds.isNotEmpty()
                                    if (hasSelectedAsset && kotlin.math.abs(normalizedDelta) > 0.15f) {
                                        onRotateSelectedBy(normalizedDelta)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                                prevDistance = distance
                                prevCentroid = centroid
                                prevAngleDeg = angleDeg
                            }
                        }
                    }.pointerInput(state.currentTool, state.selectedAssetIds, state.selectedRopeIndex) {
                        fun currentCanvasSize() = Size(size.width.toFloat(), size.height.toFloat())

                        fun currentParcelSizePx() = parcelSizePx(state.project)

                        fun parcelOriginPx() = centeredParcelOriginPx(currentCanvasSize(), currentParcelSizePx()) + canvasPanOffsetPx

                        fun metersAt(point: Offset): Vec2 =
                            GeometryEngine.offsetToMeters(
                                point - parcelOriginPx(),
                                state.project.pixelsPerMeter,
                            )

                        fun activeRopeHandlesForSelectedAsset(
                            selectedAsset: PlacedAsset,
                            preset: TentPreset,
                        ): List<RopeHandleHit> =
                            ropeHandleHits(
                                asset = selectedAsset,
                                preset = preset,
                                pixelsPerMeter = state.project.pixelsPerMeter,
                                parcelOriginPx = parcelOriginPx(),
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
                            preset: TentPreset,
                            tapPx: Offset,
                        ): Int? {
                            val lineHitRadiusPx = ropeHandleGuardRadiusPx.coerceAtLeast(30f)
                            val parcelOrigin = parcelOriginPx()
                            val nearest =
                                GeometryEngine
                                    .ropeSegments(selectedAsset, preset)
                                    .mapIndexed { index, seg ->
                                        val startPx = seg.start.toOffsetPx(state.project.pixelsPerMeter, parcelOrigin)
                                        val endPx = seg.end.toOffsetPx(state.project.pixelsPerMeter, parcelOrigin)
                                        index to distancePointToSegmentPx(tapPx, startPx, endPx)
                                    }.minByOrNull { it.second }
                            val (index, distance) = nearest ?: return null
                            return if (distance <= lineHitRadiusPx) index else null
                        }

                        fun hitAssetAt(pointMeters: Vec2): PlacedAsset? {
                            val marginMeters = touchAssetHitSlopPx / state.project.pixelsPerMeter
                            return state.project.assets.lastOrNull { asset ->
                                val preset = presetsById[asset.presetId] ?: return@lastOrNull false
                                if (GeometryEngine.isPointInAsset(pointMeters, asset, preset)) return@lastOrNull true
                                val body = GeometryEngine.assetBodyAabb(asset, preset)
                                pointMeters.x in (body.left - marginMeters)..(body.right + marginMeters) &&
                                    pointMeters.y in (body.top - marginMeters)..(body.bottom + marginMeters)
                            }
                        }
                        detectTapGestures(
                            onTap = { tap ->
                                if (suppressTapTemporarily) return@detectTapGestures
                                val tapMeters = metersAt(tap)
                                when (state.currentTool) {
                                    PlannerToolMode.PlaceTent -> {
                                        onPlace(tapMeters)
                                    }

                                    PlannerToolMode.Select -> {
                                        val selectedAsset = state.project.assets.firstOrNull { it.id in state.selectedAssetIds }
                                        if (selectedAsset != null) {
                                            val preset = presetsById[selectedAsset.presetId]
                                            if (preset != null) {
                                                val ropeHandles = activeRopeHandlesForSelectedAsset(selectedAsset, preset)
                                                // Tap selection should be more forgiving than drag grab,
                                                // otherwise near-misses clear selection on desktop.
                                                val ropeHit = pickRopeHandleHit(ropeHandles, tap, ropeHandleGuardRadiusPx)
                                                if (ropeHit != null) {
                                                    onRopeSelect(ropeHit.ropeIndex)
                                                    return@detectTapGestures
                                                }
                                                val lineHitRopeIndex = pickRopeByLineHit(selectedAsset, preset, tap)
                                                if (lineHitRopeIndex != null) {
                                                    onRopeSelect(lineHitRopeIndex)
                                                    return@detectTapGestures
                                                }
                                            }
                                        }
                                        val hit = hitAssetAt(tapMeters)
                                        if (shiftPressed) {
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
                                if (state.currentTool == PlannerToolMode.Select) {
                                    val tapMeters = metersAt(tap)
                                    val hit = hitAssetAt(tapMeters)
                                    onSelectionToggle(hit?.id)
                                }
                            },
                        )
                    }.pointerInput(state.selectedAssetIds) {
                        fun currentCanvasSize() = Size(size.width.toFloat(), size.height.toFloat())

                        fun parcelOriginPx() = centeredParcelOriginPx(currentCanvasSize(), parcelSizePx(state.project)) + canvasPanOffsetPx

                        fun activeRopeHandlesForSelectedAsset(
                            selectedAsset: PlacedAsset,
                            preset: TentPreset,
                        ): List<RopeHandleHit> = ropeHandleHits(selectedAsset, preset, state.project.pixelsPerMeter, parcelOriginPx())

                        fun hitAssetAt(pointMeters: Vec2): PlacedAsset? {
                            val marginMeters = touchAssetHitSlopPx / state.project.pixelsPerMeter
                            return state.project.assets.lastOrNull { asset ->
                                val preset = presetsById[asset.presetId] ?: return@lastOrNull false
                                if (GeometryEngine.isPointInAsset(pointMeters, asset, preset)) return@lastOrNull true
                                val body = GeometryEngine.assetBodyAabb(asset, preset)
                                pointMeters.x in (body.left - marginMeters)..(body.right + marginMeters) &&
                                    pointMeters.y in (body.top - marginMeters)..(body.bottom + marginMeters)
                            }
                        }
                        detectDragGestures(
                            onDragStart = { start ->
                                if (state.currentTool != PlannerToolMode.Select) return@detectDragGestures
                                if (multiTouchActive) return@detectDragGestures
                                activeAssetDrag = false
                                activeCanvasPan = false
                                val startMeters = GeometryEngine.offsetToMeters(start - parcelOriginPx(), state.project.pixelsPerMeter)
                                val selectedAsset = state.project.assets.firstOrNull { it.id in state.selectedAssetIds }
                                if (selectedAsset != null) {
                                    val selectedPreset = presetsById[selectedAsset.presetId]
                                    if (selectedPreset != null) {
                                        val ropeHandles = activeRopeHandlesForSelectedAsset(selectedAsset, selectedPreset)
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
                                    if (hitAsset.id !in state.selectedAssetIds) onSelection(setOf(hitAsset.id))
                                    activeAssetDrag = true
                                } else {
                                    activeCanvasPan = allowSingleFingerCanvasPan
                                }
                            },
                            onDrag = { change, dragAmount ->
                                val ropeDrag = activeRopeDrag
                                if (ropeDrag != null && state.currentTool == PlannerToolMode.Select) {
                                    val meters =
                                        GeometryEngine.offsetToMeters(
                                            change.position - parcelOriginPx(),
                                            state.project.pixelsPerMeter,
                                        )
                                    if (ropeDrag.second ==
                                        RopeDragPart.Stake
                                    ) {
                                        onRopeStakeDrag(ropeDrag.first, meters)
                                    } else {
                                        onRopeAnchorDrag(ropeDrag.first, meters)
                                    }
                                    change.consume()
                                } else if (state.currentTool == PlannerToolMode.Select &&
                                    state.selectedAssetIds.isNotEmpty() &&
                                    activeAssetDrag
                                ) {
                                    onDrag(GeometryEngine.offsetToMeters(dragAmount, state.project.pixelsPerMeter))
                                    change.consume()
                                } else if (state.currentTool == PlannerToolMode.Select && activeCanvasPan) {
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
            drawGrid(state.project, parcelOrigin, size)
            drawParcelBounds(state.project, parcelOrigin)
            state.project.blockZones.forEach { drawBlockZone(it, state.project.pixelsPerMeter, parcelOrigin) }
            val directSelectedIds = state.selectedAssetIds
            val selectedGroupIds =
                state.project.assets
                    .asSequence()
                    .filter { it.id in directSelectedIds }
                    .mapNotNull { it.groupId }
                    .toSet()

            fun isSelectionHighlighted(asset: PlacedAsset): Boolean =
                asset.id in directSelectedIds ||
                    (asset.groupId != null && asset.groupId in selectedGroupIds)
            state.project.assets.sortedBy { it.heightRange.maxMeters }.forEach { asset ->
                val preset = presetsById[asset.presetId] ?: return@forEach
                val selectionHighlighted = isSelectionHighlighted(asset)
                val hard = asset.id in state.hardCollisionAssetIds
                val warn = asset.id in state.warningAssetIds
                drawAsset(
                    asset,
                    preset,
                    state.project.pixelsPerMeter,
                    selectionHighlighted,
                    warn,
                    hard,
                    parcelOrigin,
                    state.selectedRopeIndex,
                )
                if (asset.id in directSelectedIds) {
                    drawAnchorSlots(asset, preset, state.project.pixelsPerMeter, parcelOrigin)
                }
                drawAssetLabel(
                    asset,
                    preset,
                    state.project.pixelsPerMeter,
                    parcelOrigin,
                    selectionHighlighted,
                    textMeasurer,
                    isDarkTheme,
                )
            }
            drawParcelDimensionGuides(state.project, parcelOrigin, textMeasurer, isDarkTheme)
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

private fun DrawScope.drawAssetLabel(
    asset: PlacedAsset,
    preset: TentPreset,
    pixelsPerMeter: Float,
    origin: Offset,
    selected: Boolean,
    textMeasurer: TextMeasurer,
    darkMode: Boolean,
) {
    val raw = asset.label.ifBlank { preset.name }.trim()
    if (raw.isEmpty()) return
    val label = if (raw.length > 30) raw.take(29) + "…" else raw
    val meta = "${preset.shape.name} • ${"%.1f".format(preset.widthMeters)} x ${"%.1f".format(preset.heightMeters)} m"
    val center = GeometryEngine.metersToOffset(asset.position, pixelsPerMeter) + origin
    val normalText = if (darkMode) Color(0xFFE6DCC9) else Color(0xFF1F2933)
    val selectedText = if (darkMode) Color(0xFFCFE5FF) else Color(0xFF102A43)
    val metaText = if (darkMode) Color(0xBFD8CCB8) else Color(0xAA1F2933)
    val labelBg =
        when {
            selected && darkMode -> Color(0xCC253447)
            selected -> Color(0xFFE7F0FF)
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
                color = Color(0xFF2C5E91).copy(alpha = 0.55f),
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
        drawLine(Color(0x1A000000), Offset(x, 0f), Offset(x, canvasSizePx.height), 1f)
        x += spacing
    }
    var y = ((origin.y % spacing) + spacing) % spacing
    while (y <= canvasSizePx.height) {
        drawLine(Color(0x1A000000), Offset(0f, y), Offset(canvasSizePx.width, y), 1f)
        y += spacing
    }
}

private fun DrawScope.drawParcelBounds(
    project: PlannerProject,
    origin: Offset,
) {
    val w = project.parcel.widthMeters * project.pixelsPerMeter
    val h = project.parcel.heightMeters * project.pixelsPerMeter
    drawRect(color = Color(0x114A5A4A), topLeft = origin, size = Size(w, h), style = Fill)
    drawRect(color = Color(0xFF4A5A4A), topLeft = origin, size = Size(w, h), style = Stroke(width = 3f))
}

private fun DrawScope.drawBlockZone(
    zone: BlockZone,
    pixelsPerMeter: Float,
    origin: Offset,
) {
    if (zone.points.size < 3) return
    val path =
        Path().apply {
            moveTo(origin.x + zone.points.first().x * pixelsPerMeter, origin.y + zone.points.first().y * pixelsPerMeter)
            zone.points.drop(1).forEach { lineTo(origin.x + it.x * pixelsPerMeter, origin.y + it.y * pixelsPerMeter) }
            close()
        }
    drawPath(path, color = Color(0x66AA0000), style = Fill)
}

/** Extra outline so selection stays visible on warning/collision fills and on POLYGON presets. */
private fun DrawScope.drawSelectionHighlightRing(
    preset: TentPreset,
    center: Offset,
    pixelsPerMeter: Float,
) {
    val ring = Color(0xFF4A9EFF)
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
) {
    val bodyColor =
        when {
            hardCollision -> Color(0xAADD2C2C)
            warning -> Color(0xAAD89A2B)
            selectionHighlighted -> Color(0xFF2F4F4F)
            preset.shape == TentShapeType.POLYGON -> Color(0xAA725F45)
            else -> Color(0xCC6D5B44)
        }
    val outlineColor =
        when {
            hardCollision -> Color(0xFF8A0000)
            warning -> Color(0xFF8A6B00)
            selectionHighlighted -> Color(0xFF1C2626)
            else -> Color(0xFF3E3E3E)
        }
    val center = GeometryEngine.metersToOffset(asset.position, pixelsPerMeter) + origin
    drawSafetyBuffer(asset, preset, center, pixelsPerMeter)
    rotate(asset.rotationDeg, center) {
        when (preset.shape) {
            TentShapeType.CIRCLE -> {
                drawCircle(bodyColor, radius = preset.widthMeters * pixelsPerMeter / 2f, center = center)
                drawCircle(outlineColor, radius = preset.widthMeters * pixelsPerMeter / 2f, center = center, style = Stroke(2f))
            }

            TentShapeType.RECTANGLE -> {
                val rect =
                    Rect(
                        center.x - preset.widthMeters * pixelsPerMeter / 2f,
                        center.y - preset.heightMeters * pixelsPerMeter / 2f,
                        center.x + preset.widthMeters * pixelsPerMeter / 2f,
                        center.y + preset.heightMeters * pixelsPerMeter / 2f,
                    )
                drawRect(bodyColor, topLeft = rect.topLeft, size = rect.size)
                drawRect(outlineColor, topLeft = rect.topLeft, size = rect.size, style = Stroke(2f))
            }

            TentShapeType.CAPSULE -> {
                val radius = preset.heightMeters * pixelsPerMeter / 2f
                val halfBody = (preset.widthMeters * pixelsPerMeter / 2f) - radius
                drawRect(bodyColor, topLeft = Offset(center.x - halfBody, center.y - radius), size = Size(halfBody * 2f, radius * 2f))
                drawCircle(bodyColor, radius = radius, center = Offset(center.x - halfBody, center.y))
                drawCircle(bodyColor, radius = radius, center = Offset(center.x + halfBody, center.y))
                drawLine(outlineColor, Offset(center.x - halfBody, center.y - radius), Offset(center.x + halfBody, center.y - radius), 2f)
                drawLine(outlineColor, Offset(center.x - halfBody, center.y + radius), Offset(center.x + halfBody, center.y + radius), 2f)
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
                    drawPath(path, bodyColor, style = Fill)
                    drawPath(path, outlineColor, style = Stroke(2f))
                } else {
                    val radius = preset.heightMeters * pixelsPerMeter / 2f
                    val halfBody = (preset.widthMeters * pixelsPerMeter / 2f) - radius
                    drawRect(bodyColor, topLeft = Offset(center.x - halfBody, center.y - radius), size = Size(halfBody * 2f, radius * 2f))
                    drawCircle(bodyColor, radius = radius, center = Offset(center.x - halfBody, center.y))
                    drawCircle(bodyColor, radius = radius, center = Offset(center.x + halfBody, center.y))
                    drawLine(
                        outlineColor,
                        Offset(center.x - halfBody, center.y - radius),
                        Offset(center.x + halfBody, center.y - radius),
                        2f,
                    )
                    drawLine(
                        outlineColor,
                        Offset(center.x - halfBody, center.y + radius),
                        Offset(center.x + halfBody, center.y + radius),
                        2f,
                    )
                }
            }

            TentShapeType.A_FRAME -> {
                val halfW = preset.widthMeters * pixelsPerMeter / 2f
                val halfH = preset.heightMeters * pixelsPerMeter / 2f

                // 1. Die Plane (Rechteck) - benutzt die lokale Variable bodyColor
                drawRect(
                    color = bodyColor,
                    topLeft = Offset(center.x - halfW, center.y - halfH),
                    size = Size(halfW * 2f, halfH * 2f)
                )

                // 2. Die Firstlinie (Giebel) - benutzt die lokale Variable outlineColor
                drawLine(
                    color = outlineColor,
                    start = Offset(center.x, center.y - halfH),
                    end = Offset(center.x, center.y + halfH),
                    strokeWidth = 2.5f
                )

                // 3. Die Outline (Rahmen)
                drawRect(
                    color = outlineColor,
                    topLeft = Offset(center.x - halfW, center.y - halfH),
                    size = Size(halfW * 2f, halfH * 2f),
                    style = Stroke(2f)
                )

                // 4. Auswahl-Highlight (Direkt hier integriert)
                if (selectionHighlighted) {
                    drawRect(
                        color = Color(0xFF4A9EFF),
                        topLeft = Offset(center.x - halfW, center.y - halfH),
                        size = Size(halfW * 2f, halfH * 2f),
                        style = Stroke(width = 3.5f)
                    )
                }
            }

            TentShapeType.POLYGON -> {
                val points = preset.polygonPoints.ifEmpty { listOf(Vec2(-2f, -2f), Vec2(2f, -2f), Vec2(2f, 2f), Vec2(-2f, 2f)) }
                val path =
                    Path().apply {
                        moveTo(center.x + points.first().x * pixelsPerMeter, center.y + points.first().y * pixelsPerMeter)
                        points.drop(1).forEach { lineTo(center.x + it.x * pixelsPerMeter, center.y + it.y * pixelsPerMeter) }
                        close()
                    }
                drawPath(path, bodyColor, style = Fill)
                drawPath(path, outlineColor, style = Stroke(2f))
            }
        }
    }
    if (asset.showRopes) {
        val ropeUiScale = (pixelsPerMeter / 50f).coerceIn(1f, 2.4f)
        GeometryEngine.ropeSegments(asset, preset).forEachIndexed { idx, rope ->
            val isSelectedRope = selectedRopeIndex == idx
            drawLine(
                color = if (isSelectedRope) Color(0xFF1B4D9B) else Color(0xFF444444),
                start = rope.start.toOffsetPx(pixelsPerMeter, origin),
                end = rope.end.toOffsetPx(pixelsPerMeter, origin),
                strokeWidth = if (isSelectedRope) 2.8f * ropeUiScale else 1.8f * ropeUiScale,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * ropeUiScale, 6f * ropeUiScale)),
            )
            drawCircle(
                color = if (isSelectedRope) Color(0xFF1B4D9B) else Color(0xFF555555),
                center = rope.start.toOffsetPx(pixelsPerMeter, origin),
                radius = if (isSelectedRope) 5.5f * ropeUiScale else 4f * ropeUiScale,
            )
            drawCircle(
                color = if (isSelectedRope) Color(0xFF1B4D9B) else Color(0xFF7A1F1F),
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
    val bufferColor = Color(0x22555555)
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
    val topY = (origin.y - 20f).coerceAtLeast(8f)
    val leftX = (origin.x - 20f).coerceAtLeast(8f)

    val guideColor = if (darkMode) Color(0xBFAFA087) else Color(0xAA4A5A4A)
    val guideTextColor = if (darkMode) Color(0xFFE6DCC9) else Color(0xFF2F261A)
    val guideBgColor = if (darkMode) Color(0xCC3A3329) else Color(0xDDF2EADB)

    drawLine(
        color = guideColor,
        start = Offset(origin.x, topY),
        end = Offset(origin.x + widthPx, topY),
        strokeWidth = 2f,
    )
    drawLine(guideColor, Offset(origin.x, topY - 6f), Offset(origin.x, topY + 6f), 2f)
    drawLine(guideColor, Offset(origin.x + widthPx, topY - 6f), Offset(origin.x + widthPx, topY + 6f), 2f)

    val widthLabel =
        textMeasurer.measure(
            AnnotatedString("${"%.1f".format(project.parcel.widthMeters)} m"),
            style = TextStyle(color = guideTextColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
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
