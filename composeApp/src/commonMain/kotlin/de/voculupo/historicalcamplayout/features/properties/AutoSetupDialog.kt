package de.voculupo.historicalcamplayout.features.properties

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.voculupo.historicalcamplayout.AutoSetupRequest
import de.voculupo.historicalcamplayout.BlockZone
import de.voculupo.historicalcamplayout.FreeArea
import de.voculupo.historicalcamplayout.FreeAreaKind
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.Parcel
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.RingShape
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.Vec2
import de.voculupo.historicalcamplayout.VisitorSide
import de.voculupo.historicalcamplayout.features.planner.state.AutoLayout
import de.voculupo.historicalcamplayout.features.planner.state.AutoLayoutResult
import de.voculupo.historicalcamplayout.features.planner.state.BlockKey
import de.voculupo.historicalcamplayout.features.planner.state.LayoutBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lagerplaner.composeapp.generated.resources.Res
import lagerplaner.composeapp.generated.resources.all_fits
import lagerplaner.composeapp.generated.resources.area_arena
import lagerplaner.composeapp.generated.resources.area_custom
import lagerplaner.composeapp.generated.resources.area_fire
import lagerplaner.composeapp.generated.resources.area_lane
import lagerplaner.composeapp.generated.resources.area_name
import lagerplaner.composeapp.generated.resources.auto_setup
import lagerplaner.composeapp.generated.resources.auto_setup_generate
import lagerplaner.composeapp.generated.resources.auto_setup_join_sails
import lagerplaner.composeapp.generated.resources.auto_setup_replace
import lagerplaner.composeapp.generated.resources.auto_setup_sails
import lagerplaner.composeapp.generated.resources.auto_setup_shape
import lagerplaner.composeapp.generated.resources.auto_setup_tents
import lagerplaner.composeapp.generated.resources.auto_setup_visitor_side
import lagerplaner.composeapp.generated.resources.back
import lagerplaner.composeapp.generated.resources.cancel
import lagerplaner.composeapp.generated.resources.delete
import lagerplaner.composeapp.generated.resources.depth_m
import lagerplaner.composeapp.generated.resources.diameter_m
import lagerplaner.composeapp.generated.resources.drag_preview_hint
import lagerplaner.composeapp.generated.resources.free_areas
import lagerplaner.composeapp.generated.resources.height_m
import lagerplaner.composeapp.generated.resources.move_down
import lagerplaner.composeapp.generated.resources.move_up
import lagerplaner.composeapp.generated.resources.next
import lagerplaner.composeapp.generated.resources.optimize_layout
import lagerplaner.composeapp.generated.resources.reset_positions
import lagerplaner.composeapp.generated.resources.ring_auto
import lagerplaner.composeapp.generated.resources.ring_n
import lagerplaner.composeapp.generated.resources.shape_edge
import lagerplaner.composeapp.generated.resources.shape_full_circle
import lagerplaner.composeapp.generated.resources.shape_half_circle
import lagerplaner.composeapp.generated.resources.shape_horseshoe
import lagerplaner.composeapp.generated.resources.shape_spread
import lagerplaner.composeapp.generated.resources.shape_u
import lagerplaner.composeapp.generated.resources.side_bottom
import lagerplaner.composeapp.generated.resources.side_left
import lagerplaner.composeapp.generated.resources.side_right
import lagerplaner.composeapp.generated.resources.side_top
import lagerplaner.composeapp.generated.resources.sort_by_size
import lagerplaner.composeapp.generated.resources.step_arrange
import lagerplaner.composeapp.generated.resources.step_place
import lagerplaner.composeapp.generated.resources.step_sails
import lagerplaner.composeapp.generated.resources.tent_order
import lagerplaner.composeapp.generated.resources.visitors
import lagerplaner.composeapp.generated.resources.walkway
import lagerplaner.composeapp.generated.resources.walkway_cloth
import lagerplaner.composeapp.generated.resources.warn_compressed
import lagerplaner.composeapp.generated.resources.warn_skipped
import lagerplaner.composeapp.generated.resources.width_m
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min
import kotlin.math.roundToInt

private val sideLabels =
    listOf(
        VisitorSide.TOP to Res.string.side_top,
        VisitorSide.RIGHT to Res.string.side_right,
        VisitorSide.BOTTOM to Res.string.side_bottom,
        VisitorSide.LEFT to Res.string.side_left,
    )

private val shapeLabels =
    listOf(
        RingShape.U_SHAPE to Res.string.shape_u,
        RingShape.HORSESHOE to Res.string.shape_horseshoe,
        RingShape.FULL_CIRCLE to Res.string.shape_full_circle,
        RingShape.HALF_CIRCLE to Res.string.shape_half_circle,
        RingShape.EDGE to Res.string.shape_edge,
        RingShape.SPREAD to Res.string.shape_spread,
    )

private val walkways = listOf(0f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f)

private val areaLabels =
    listOf(
        FreeAreaKind.ARENA to Res.string.area_arena,
        FreeAreaKind.FIRE to Res.string.area_fire,
        FreeAreaKind.LANE to Res.string.area_lane,
        FreeAreaKind.CUSTOM to Res.string.area_custom,
    )

/** Defaults: arena incl. 2 m rope gap, 2 m fire pit (the app keeps 2 m around it), 3 m lane. */
private fun newArea(
    kind: FreeAreaKind,
    label: String,
) = when (kind) {
    FreeAreaKind.ARENA -> FreeArea(kind, label, 12f, 10f)
    FreeAreaKind.FIRE -> FreeArea(kind, label, 2f)
    FreeAreaKind.LANE -> FreeArea(kind, label, 3f)
    FreeAreaKind.CUSTOM -> FreeArea(kind, label, 6f, 6f)
}

private fun TentPreset.area() = widthMeters * heightMeters

/**
 * Auto setup in four steps with a live preview. Sails and open areas can be dragged in the preview;
 * the setup is stored on the project, so opening the sheet again edits the last one.
 */
@Composable
internal fun AutoSetupDialog(
    presets: List<TentPreset>,
    project: PlannerProject,
    onConfirm: (AutoSetupRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val presetsById = remember(presets) { presets.associateBy { it.id } }
    var req by remember {
        mutableStateOf(
            project.autoSetup ?: AutoSetupRequest(project.parcel, emptyList(), emptyList(), VisitorSide.BOTTOM, shape = RingShape.U_SHAPE),
        )
    }
    var widthInput by remember { mutableStateOf(fmt(req.parcel.widthMeters)) }
    var heightInput by remember { mutableStateOf(fmt(req.parcel.heightMeters)) }
    var step by remember { mutableIntStateOf(0) }
    val userZones = remember(project) { project.blockZones.filterNot { it.id.startsWith(AutoLayout.AUTO_PREFIX) } }
    val kept = if (req.replaceExisting) emptyList() else project.assets
    // Computed off the main thread: the optimizer needs a moment on phones, taps must stay smooth.
    val result by produceState(
        AutoLayoutResult(emptyList(), emptyList(), emptyList(), req.walkwayMeters, emptyList()),
        req,
        presetsById,
        userZones,
        kept,
    ) {
        value =
            withContext(Dispatchers.Default) {
                var n = 0
                AutoLayout.generate(req, presetsById, userZones, kept) { "$it${n++}" }
            }
    }

    fun setParcel(
        w: String,
        h: String,
    ) {
        widthInput = w
        heightInput = h
        val width = w.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }
        val height = h.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }
        if (width != null && height != null) req = req.copy(parcel = Parcel(width, height), clusterPins = emptyList())
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f),
        ) {
            BoxWithConstraints {
                val wide = maxWidth > 700.dp
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(Res.string.auto_setup), style = MaterialTheme.typography.titleLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Res.string.step_place, Res.string.step_sails, Res.string.auto_setup_tents, Res.string.step_arrange)
                            .forEachIndexed { i, label ->
                                FilterChip(
                                    selected = step == i,
                                    onClick = { step = i },
                                    label = { Text("${i + 1} · ${stringResource(label)}") },
                                )
                            }
                    }
                    HorizontalDivider()
                    val content: @Composable () -> Unit = {
                        when (step) {
                            0 -> StepPlace(widthInput, heightInput, req, ::setParcel) { req = it }
                            1 -> StepSailsAndAreas(req, presets) { req = it }
                            2 -> StepTents(req, presets, presetsById) { req = it }
                            else -> StepArrange(req) { req = it }
                        }
                    }
                    val preview: @Composable (Modifier) -> Unit = { canvasModifier ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Preview(result, req, presetsById, userZones, onChange = { req = it }, modifier = canvasModifier)
                            // Fixed height: warnings coming and going must not shift the list under the user's finger.
                            Column(modifier = Modifier.fillMaxWidth().height(72.dp).verticalScroll(rememberScrollState())) {
                                Warnings(result, req)
                            }
                        }
                    }
                    if (wide) {
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(
                                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) { content() }
                            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                preview(
                                    Modifier.fillMaxWidth().aspectRatio(
                                        (req.parcel.widthMeters / req.parcel.heightMeters).coerceIn(0.6f, 2.2f),
                                    ),
                                )
                            }
                        }
                    } else {
                        // Phones: preview fixed on top so + taps show their effect and dragging in it doesn't scroll.
                        preview(Modifier.fillMaxWidth().height(220.dp))
                        Column(
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) { content() }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                        if (step > 0) OutlinedButton(onClick = { step-- }) { Text(stringResource(Res.string.back)) }
                        if (step < 3) {
                            Button(onClick = { step++ }) { Text(stringResource(Res.string.next)) }
                        } else {
                            Button(
                                enabled = req.tentPresetIds.isNotEmpty() || req.sailPresetIds.isNotEmpty(),
                                onClick = { onConfirm(req) },
                            ) { Text(stringResource(Res.string.auto_setup_generate)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepPlace(
    widthInput: String,
    heightInput: String,
    req: AutoSetupRequest,
    onParcel: (String, String) -> Unit,
    onChange: (AutoSetupRequest) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = widthInput,
                onValueChange = { onParcel(it, heightInput) },
                singleLine = true,
                isError = parsePositive(widthInput) == null,
                label = { Text(stringResource(Res.string.width_m)) },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = heightInput,
                onValueChange = { onParcel(widthInput, it) },
                singleLine = true,
                isError = parsePositive(heightInput) == null,
                label = { Text(stringResource(Res.string.height_m)) },
                modifier = Modifier.weight(1f),
            )
        }
        ChipRow(Res.string.auto_setup_visitor_side, sideLabels.map { (v, l) -> v to stringResource(l) }, req.visitorSide) {
            onChange(req.copy(visitorSide = it, clusterPins = emptyList(), freeAreas = req.freeAreas.map { a -> a.copy(position = null) }))
        }
    }
}

@Composable
private fun StepSailsAndAreas(
    req: AutoSetupRequest,
    presets: List<TentPreset>,
    onChange: (AutoSetupRequest) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.auto_setup_sails), style = MaterialTheme.typography.titleSmall)
        presets.filter(GeometryEngine::isSunSailPreset).forEach { p ->
            CountRow(p.name, req.sailPresetIds.count { it == p.id }) { add ->
                // Pins belong to rings by index; a different sail count reshuffles the rings.
                val sails = if (add) req.sailPresetIds + p.id else req.sailPresetIds.withoutLast(p.id)
                onChange(req.copy(sailPresetIds = sails, clusterPins = emptyList()))
            }
        }
        SwitchRow(
            stringResource(Res.string.auto_setup_join_sails),
            req.joinSails,
        ) { onChange(req.copy(joinSails = it, clusterPins = emptyList())) }
        HorizontalDivider()
        Text(stringResource(Res.string.free_areas), style = MaterialTheme.typography.titleSmall)
        req.freeAreas.forEachIndexed { i, area ->
            AreaRow(area, onChange = { onChange(req.copy(freeAreas = req.freeAreas.replaced(i, it))) }) {
                onChange(req.copy(freeAreas = req.freeAreas.removedAt(i)))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            areaLabels.forEach { (kind, res) ->
                val label = stringResource(res)
                OutlinedButton(onClick = { onChange(req.copy(freeAreas = req.freeAreas + newArea(kind, label))) }) { Text("+ $label") }
            }
        }
    }
}

@Composable
private fun AreaRow(
    area: FreeArea,
    onChange: (FreeArea) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (area.kind == FreeAreaKind.CUSTOM) {
                OutlinedTextField(
                    value = area.label,
                    onValueChange = { onChange(area.copy(label = it)) },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.area_name)) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(area.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onRemove) { Text(stringResource(Res.string.delete)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val firstLabel = if (area.kind == FreeAreaKind.FIRE) Res.string.diameter_m else Res.string.width_m
            MeterField(area.widthMeters, firstLabel, onValue = { onChange(area.copy(widthMeters = it)) }, modifier = Modifier.weight(1f))
            if (area.kind == FreeAreaKind.ARENA || area.kind == FreeAreaKind.CUSTOM) {
                MeterField(
                    area.depthMeters,
                    Res.string.depth_m,
                    onValue = { onChange(area.copy(depthMeters = it)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ktlint's parameter-order rule moves modifier before the lambda; this rule then objects to the trailing lambda.
@Suppress("ktlint:compose:lambda-param-event-trailing")
@Composable
private fun MeterField(
    value: Float,
    label: StringResource,
    modifier: Modifier = Modifier,
    onValue: (Float) -> Unit,
) {
    var text by remember { mutableStateOf(fmt(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            parsePositive(it)?.let(onValue)
        },
        singleLine = true,
        isError = parsePositive(text) == null,
        label = { Text(stringResource(label)) },
        modifier = modifier,
    )
}

@Composable
private fun StepTents(
    req: AutoSetupRequest,
    presets: List<TentPreset>,
    presetsById: Map<String, TentPreset>,
    onChange: (AutoSetupRequest) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val ids = req.tentPresetIds
        val rings = req.tentRings.take(ids.size) + List((ids.size - req.tentRings.size).coerceAtLeast(0)) { null }
        presets.filterNot(GeometryEngine::isSunSailPreset).forEach { p ->
            CountRow(p.name, ids.count { it == p.id }) { add ->
                if (add) {
                    // New tents slot in by size, so the default order is biggest first.
                    val at = ids.indexOfFirst { (presetsById[it]?.area() ?: 0f) < p.area() }.let { if (it < 0) ids.size else it }
                    onChange(req.copy(tentPresetIds = ids.inserted(at, p.id), tentRings = rings.inserted(at, null)))
                } else {
                    val at = ids.lastIndexOf(p.id)
                    if (at >= 0) onChange(req.copy(tentPresetIds = ids.removedAt(at), tentRings = rings.removedAt(at)))
                }
            }
        }
        if (ids.isEmpty()) return
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.tent_order), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = {
                val order = ids.indices.sortedByDescending { presetsById[ids[it]]?.area() ?: 0f }
                onChange(req.copy(tentPresetIds = order.map { ids[it] }, tentRings = order.map { rings[it] }))
            }) { Text(stringResource(Res.string.sort_by_size)) }
        }
        val ringCount = AutoLayout.ringCount(req, presetsById)
        val up = stringResource(Res.string.move_up)
        val down = stringResource(Res.string.move_down)
        ids.forEachIndexed { i, id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}. ${presetsById[id]?.name ?: id}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                if (ringCount > 1) {
                    val ring = rings[i]?.takeIf { it < ringCount }
                    TextButton(onClick = {
                        // Cycles auto -> 1 -> 2 -> ... -> auto.
                        val next =
                            when (ring) {
                                null -> 0
                                ringCount - 1 -> null
                                else -> ring + 1
                            }
                        onChange(req.copy(tentRings = rings.replaced(i, next)))
                    }) { Text(if (ring == null) stringResource(Res.string.ring_auto) else stringResource(Res.string.ring_n, ring + 1)) }
                }
                TextButton(
                    onClick = { onChange(req.copy(tentPresetIds = ids.swapped(i, i - 1), tentRings = rings.swapped(i, i - 1))) },
                    enabled = i > 0,
                    modifier = Modifier.semantics { contentDescription = up },
                ) { Text("↑") }
                TextButton(
                    onClick = { onChange(req.copy(tentPresetIds = ids.swapped(i, i + 1), tentRings = rings.swapped(i, i + 1))) },
                    enabled = i < ids.lastIndex,
                    modifier = Modifier.semantics { contentDescription = down },
                ) { Text("↓") }
            }
        }
    }
}

@Composable
private fun StepArrange(
    req: AutoSetupRequest,
    onChange: (AutoSetupRequest) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipRow(
            Res.string.auto_setup_shape,
            shapeLabels.map { (v, l) ->
                v to stringResource(l)
            },
            req.shape,
        ) { onChange(req.copy(shape = it)) }
        val cloth = stringResource(Res.string.walkway_cloth)
        ChipRow(Res.string.walkway, walkways.map { it to if (it == 0f) cloth else "${fmt(it)} m" }, req.walkwayMeters) {
            onChange(req.copy(walkwayMeters = it))
        }
        SwitchRow(stringResource(Res.string.optimize_layout), req.optimize) { onChange(req.copy(optimize = it)) }
        SwitchRow(stringResource(Res.string.auto_setup_replace), req.replaceExisting) { onChange(req.copy(replaceExisting = it)) }
        Text(stringResource(Res.string.drag_preview_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { onChange(req.copy(clusterPins = emptyList(), freeAreas = req.freeAreas.map { it.copy(position = null) })) },
        ) {
            Text(stringResource(Res.string.reset_positions))
        }
    }
}

/** Scaled parcel with the draft; drag a block (sails / open area) to pin it somewhere else. */
@Composable
private fun Preview(
    result: AutoLayoutResult,
    req: AutoSetupRequest,
    presetsById: Map<String, TentPreset>,
    userZones: List<BlockZone>,
    onChange: (AutoSetupRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val visitors = stringResource(Res.string.visitors)
    val colors = MaterialTheme.colorScheme
    var dragged by remember { mutableStateOf<LayoutBlock?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    val current by rememberUpdatedState(result)
    val currentReq by rememberUpdatedState(req)
    val parcel = req.parcel
    Canvas(
        modifier =
            modifier
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val p = currentReq.parcel
                            val m =
                                Vec2(
                                    pos.x / scale - originX(size.width.toFloat(), scale, p),
                                    pos.y / scale - originY(size.height.toFloat(), scale, p),
                                )
                            dragged =
                                current.blocks.lastOrNull {
                                    m.x in it.bounds.left..it.bounds.right && m.y in it.bounds.top..it.bounds.bottom
                                }
                            dragOffset = Offset.Zero
                        },
                        onDrag = { change, amount ->
                            if (dragged != null) {
                                change.consume()
                                dragOffset += amount / scale
                            }
                        },
                        onDragEnd = {
                            dragged?.let { block ->
                                onChange(currentReq.pinned(block.key, Vec2(block.anchor.x + dragOffset.x, block.anchor.y + dragOffset.y)))
                            }
                            dragged = null
                        },
                        onDragCancel = { dragged = null },
                    )
                },
    ) {
        scale = min(size.width / parcel.widthMeters, size.height / parcel.heightMeters)
        val ox = originX(size.width, scale, parcel)
        val oy = originY(size.height, scale, parcel)

        fun px(
            p: Vec2,
            shift: Offset = Offset.Zero,
        ) = Offset((p.x + ox + shift.x) * scale, (p.y + oy + shift.y) * scale)

        fun poly(
            points: List<Vec2>,
            shift: Offset,
        ) = Path().apply {
            points.forEachIndexed { i, p -> px(p, shift).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            close()
        }
        val moving = dragged
        val parcelSize = Size(parcel.widthMeters * scale, parcel.heightMeters * scale)
        drawRect(colors.surfaceVariant, topLeft = px(Vec2(0f, 0f)), size = parcelSize)
        drawRect(colors.outline, topLeft = px(Vec2(0f, 0f)), size = parcelSize, style = Stroke(2f))
        val (a, b) =
            when (req.visitorSide) {
                VisitorSide.TOP -> Vec2(0f, 0f) to Vec2(parcel.widthMeters, 0f)
                VisitorSide.BOTTOM -> Vec2(0f, parcel.heightMeters) to Vec2(parcel.widthMeters, parcel.heightMeters)
                VisitorSide.LEFT -> Vec2(0f, 0f) to Vec2(0f, parcel.heightMeters)
                VisitorSide.RIGHT -> Vec2(parcel.widthMeters, 0f) to Vec2(parcel.widthMeters, parcel.heightMeters)
            }
        drawLine(colors.primary, px(a), px(b), strokeWidth = 6f)
        val label = textMeasurer.measure(AnnotatedString(visitors), TextStyle(color = colors.primary, fontSize = 11.sp))
        val mid = px(Vec2((a.x + b.x) / 2, (a.y + b.y) / 2))
        drawText(
            label,
            topLeft =
                Offset(
                    (mid.x - label.size.width / 2).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f)),
                    (mid.y - label.size.height / 2).coerceIn(0f, (size.height - label.size.height).coerceAtLeast(0f)),
                ),
        )
        userZones.filter { it.points.size >= 3 }.forEach { drawPath(poly(it.points, Offset.Zero), Color(0x55666666)) }
        result.zones.forEach { zone ->
            val shift = if (moving != null && zone.id in moving.zoneIds) dragOffset else Offset.Zero
            drawPath(poly(zone.points, shift), Color(0x66AA0000))
            if (zone.label.isNotBlank()) {
                val t = textMeasurer.measure(AnnotatedString(zone.label), TextStyle(color = colors.onSurface, fontSize = 10.sp))
                val c =
                    px(
                        Vec2(
                            zone.points
                                .map { it.x }
                                .average()
                                .toFloat(),
                            zone.points
                                .map { it.y }
                                .average()
                                .toFloat(),
                        ),
                        shift,
                    )
                drawText(t, topLeft = Offset(c.x - t.size.width / 2, c.y - t.size.height / 2))
            }
        }
        result.assets.forEach { asset ->
            val preset = presetsById[asset.presetId] ?: return@forEach
            val shift = if (moving != null && asset.id in moving.assetIds) dragOffset else Offset.Zero
            val path = poly(GeometryEngine.assetWorldOutline(asset, preset), shift)
            val sail = GeometryEngine.isSunSailPreset(preset)
            drawPath(path, if (sail) Color(0x88D9B97A) else Color(0xFF7A5C3E))
            drawPath(path, colors.onSurface.copy(alpha = 0.6f), style = Stroke(1.2f))
            GeometryEngine.entryWorld(asset, preset)?.let { (entry, out) ->
                val tip = Vec2(entry.x + out.x * 0.9f, entry.y + out.y * 0.9f)
                val l = Vec2(entry.x + out.x * 0.2f - out.y * 0.45f, entry.y + out.y * 0.2f + out.x * 0.45f)
                val r = Vec2(entry.x + out.x * 0.2f + out.y * 0.45f, entry.y + out.y * 0.2f - out.x * 0.45f)
                drawPath(poly(listOf(tip, l, r), shift), colors.primary)
            }
        }
    }
}

private fun originX(
    widthPx: Float,
    scale: Float,
    parcel: Parcel,
) = (widthPx / scale - parcel.widthMeters) / 2f

private fun originY(
    heightPx: Float,
    scale: Float,
    parcel: Parcel,
) = (heightPx / scale - parcel.heightMeters) / 2f

@Composable
private fun Warnings(
    result: AutoLayoutResult,
    req: AutoSetupRequest,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val warn = MaterialTheme.colorScheme.error
        if (result.walkwayMeters < req.walkwayMeters) {
            Text(
                stringResource(Res.string.warn_compressed, fmt(result.walkwayMeters)),
                color = warn,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (result.skipped.isNotEmpty()) {
            Text(
                stringResource(Res.string.warn_skipped, result.skipped.size, result.skipped.joinToString()),
                color = warn,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (result.skipped.isEmpty() && result.walkwayMeters >= req.walkwayMeters && result.assets.isNotEmpty()) {
            Text(stringResource(Res.string.all_fits), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun <T> ChipRow(
    title: StringResource,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Name, count and -/+ buttons; [onStep] gets true for +. */
@Composable
private fun CountRow(
    name: String,
    count: Int,
    onStep: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onStep(false) }, enabled = count > 0) { Text("−") }
        Text("$count", modifier = Modifier.width(24.dp))
        TextButton(onClick = { onStep(true) }) { Text("+") }
    }
}

private fun parsePositive(text: String) = text.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }

private fun fmt(v: Float): String = ((v * 10).roundToInt() / 10f).toString()

private fun <T> List<T>.replaced(
    i: Int,
    value: T,
) = toMutableList().also { it[i] = value }

private fun <T> List<T>.inserted(
    i: Int,
    value: T,
) = toMutableList().also { it.add(i, value) }

private fun <T> List<T>.removedAt(i: Int) = toMutableList().also { it.removeAt(i) }

private fun <T> List<T>.withoutLast(value: T) = lastIndexOf(value).let { if (it < 0) this else removedAt(it) }

private fun <T> List<T>.swapped(
    i: Int,
    j: Int,
) = toMutableList().also {
    val t = it[i]
    it[i] = it[j]
    it[j] = t
}

private fun AutoSetupRequest.pinned(
    key: BlockKey,
    pin: Vec2,
): AutoSetupRequest =
    if (key.isRing) {
        val pins = clusterPins + List((key.index + 1 - clusterPins.size).coerceAtLeast(0)) { null }
        copy(clusterPins = pins.replaced(key.index, pin))
    } else {
        copy(freeAreas = freeAreas.replaced(key.index, freeAreas[key.index].copy(position = pin)))
    }
