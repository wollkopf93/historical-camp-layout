package de.voculupo.historicalcamplayout.features.properties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.voculupo.historicalcamplayout.GeometryEngine
import de.voculupo.historicalcamplayout.Parcel
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.features.planner.state.AutoSetupRequest
import de.voculupo.historicalcamplayout.features.planner.state.RingShape
import de.voculupo.historicalcamplayout.features.planner.state.VisitorSide
import lagerplaner.composeapp.generated.resources.Res
import lagerplaner.composeapp.generated.resources.auto_setup
import lagerplaner.composeapp.generated.resources.auto_setup_generate
import lagerplaner.composeapp.generated.resources.auto_setup_join_sails
import lagerplaner.composeapp.generated.resources.auto_setup_replace
import lagerplaner.composeapp.generated.resources.auto_setup_sails
import lagerplaner.composeapp.generated.resources.auto_setup_shape
import lagerplaner.composeapp.generated.resources.auto_setup_tents
import lagerplaner.composeapp.generated.resources.auto_setup_visitor_side
import lagerplaner.composeapp.generated.resources.cancel
import lagerplaner.composeapp.generated.resources.height_m
import lagerplaner.composeapp.generated.resources.shape_full_circle
import lagerplaner.composeapp.generated.resources.shape_half_circle
import lagerplaner.composeapp.generated.resources.shape_horseshoe
import lagerplaner.composeapp.generated.resources.side_bottom
import lagerplaner.composeapp.generated.resources.side_left
import lagerplaner.composeapp.generated.resources.side_right
import lagerplaner.composeapp.generated.resources.side_top
import lagerplaner.composeapp.generated.resources.width_m
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val sideLabels =
    listOf(
        VisitorSide.TOP to Res.string.side_top,
        VisitorSide.RIGHT to Res.string.side_right,
        VisitorSide.BOTTOM to Res.string.side_bottom,
        VisitorSide.LEFT to Res.string.side_left,
    )

private val shapeLabels =
    listOf(
        RingShape.HORSESHOE to Res.string.shape_horseshoe,
        RingShape.FULL_CIRCLE to Res.string.shape_full_circle,
        RingShape.HALF_CIRCLE to Res.string.shape_half_circle,
    )

/** Onboarding: what you bring and how your group likes to set up; generates a draft camp. */
@Composable
internal fun AutoSetupDialog(
    presets: List<TentPreset>,
    parcel: Parcel,
    onConfirm: (AutoSetupRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var widthInput by remember { mutableStateOf(parcel.widthMeters.toString()) }
    var heightInput by remember { mutableStateOf(parcel.heightMeters.toString()) }
    val counts = remember { mutableStateMapOf<String, Int>() }
    var side by remember { mutableStateOf(VisitorSide.BOTTOM) }
    var shape by remember { mutableStateOf(RingShape.HORSESHOE) }
    var joinSails by remember { mutableStateOf(false) }
    var replace by remember { mutableStateOf(true) }
    val (sails, tents) = presets.partition(GeometryEngine::isSunSailPreset)
    val width = widthInput.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }
    val height = heightInput.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }

    fun ids(list: List<TentPreset>) = list.flatMap { p -> List(counts[p.id] ?: 0) { p.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.auto_setup)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = widthInput,
                        onValueChange = { widthInput = it },
                        singleLine = true,
                        isError = width == null,
                        label = { Text(stringResource(Res.string.width_m)) },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { heightInput = it },
                        singleLine = true,
                        isError = height == null,
                        label = { Text(stringResource(Res.string.height_m)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                ChipRow(Res.string.auto_setup_visitor_side, sideLabels, side) { side = it }
                ChipRow(Res.string.auto_setup_shape, shapeLabels, shape) { shape = it }
                SwitchRow(stringResource(Res.string.auto_setup_join_sails), joinSails) { joinSails = it }
                SwitchRow(stringResource(Res.string.auto_setup_replace), replace) { replace = it }
                Text(stringResource(Res.string.auto_setup_sails), style = MaterialTheme.typography.titleSmall)
                sails.forEach { p -> CountRow(p.name, counts[p.id] ?: 0) { counts[p.id] = it } }
                Text(stringResource(Res.string.auto_setup_tents), style = MaterialTheme.typography.titleSmall)
                tents.forEach { p -> CountRow(p.name, counts[p.id] ?: 0) { counts[p.id] = it } }
            }
        },
        confirmButton = {
            Button(
                enabled = width != null && height != null && counts.values.any { it > 0 },
                onClick = {
                    onConfirm(
                        AutoSetupRequest(
                            parcel = Parcel(width ?: return@Button, height ?: return@Button),
                            tentPresetIds = ids(tents),
                            sailPresetIds = ids(sails),
                            visitorSide = side,
                            shape = shape,
                            joinSails = joinSails,
                            replaceExisting = replace,
                        ),
                    )
                },
            ) { Text(stringResource(Res.string.auto_setup_generate)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun <T> ChipRow(
    title: StringResource,
    options: List<Pair<T, StringResource>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(stringResource(label)) })
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

@Composable
private fun CountRow(
    name: String,
    count: Int,
    onCountChange: (Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onCountChange(count - 1) }, enabled = count > 0) { Text("−") }
        Text("$count", modifier = Modifier.width(24.dp))
        TextButton(onClick = { onCountChange(count + 1) }) { Text("+") }
    }
}
