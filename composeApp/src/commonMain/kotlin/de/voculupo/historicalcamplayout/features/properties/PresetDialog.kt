package de.voculupo.historicalcamplayout.features.properties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.voculupo.historicalcamplayout.TentPreset
import de.voculupo.historicalcamplayout.features.planner.state.PlannerDomainOps
import de.voculupo.historicalcamplayout.presetDisplayName
import lagerplaner.composeapp.generated.resources.Res
import lagerplaner.composeapp.generated.resources.cancel
import lagerplaner.composeapp.generated.resources.edit_tent_type
import lagerplaner.composeapp.generated.resources.own_spacing
import lagerplaner.composeapp.generated.resources.own_spacing_hint
import lagerplaner.composeapp.generated.resources.own_spacing_path
import lagerplaner.composeapp.generated.resources.own_spacing_sails
import lagerplaner.composeapp.generated.resources.own_spacing_tents
import lagerplaner.composeapp.generated.resources.rope_n_stake
import lagerplaner.composeapp.generated.resources.rope_remove
import lagerplaner.composeapp.generated.resources.rope_slope_apply
import lagerplaner.composeapp.generated.resources.rope_slope_deg
import lagerplaner.composeapp.generated.resources.rope_slope_height
import lagerplaner.composeapp.generated.resources.rope_slope_result
import lagerplaner.composeapp.generated.resources.rope_slope_title
import lagerplaner.composeapp.generated.resources.spacing_camp_default
import lagerplaner.composeapp.generated.resources.take_over
import lagerplaner.composeapp.generated.resources.type_copy_suffix
import lagerplaner.composeapp.generated.resources.type_depth
import lagerplaner.composeapp.generated.resources.type_is_sail
import lagerplaner.composeapp.generated.resources.type_margin
import lagerplaner.composeapp.generated.resources.type_name
import lagerplaner.composeapp.generated.resources.type_ropes
import lagerplaner.composeapp.generated.resources.type_ropes_hint
import lagerplaner.composeapp.generated.resources.type_save_as_new
import lagerplaner.composeapp.generated.resources.type_width
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

/**
 * Edits a tent type: name, size (outline and rope anchors stretch along), safety margin, own gaps that
 * replace the camp's spacing rules, and the guy ropes' peg distance, optionally worked out from the
 * attachment height and the wanted rope slope. "Save as new" keeps the original and adds a copy.
 */
@Composable
internal fun PresetDialog(
    preset: TentPreset,
    onConfirm: (TentPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    // Built-in types show their name in the app language; it is only stored when the user changes it.
    val shownName = presetDisplayName(preset)
    var draft by remember(preset.id) { mutableStateOf(preset.copy(name = shownName)) }

    fun finalName(name: String) = if (name == shownName) preset.name else name
    // Rope helper: kept in the dialog only, it just fills in the peg distances.
    var ropeHeight by remember { mutableStateOf<Float?>(null) }
    var ropeSlope by remember { mutableStateOf<Float?>(SLOPE_DEFAULT_DEG) }
    // Bumped when ropes change from outside their own field (removal, slope helper) so the fields reload.
    var ropeRevision by remember { mutableIntStateOf(0) }
    val builtInSail = preset.id.startsWith("sun_sail_")
    val copySuffix = stringResource(Res.string.type_copy_suffix)
    val valid = draft.name.isNotBlank() && draft.widthMeters > 0f && draft.heightMeters > 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit_tent_type)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it.take(NAME_MAX)) },
                    singleLine = true,
                    isError = draft.name.isBlank(),
                    label = { Text(stringResource(Res.string.type_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        MetersField(Res.string.type_width, draft.widthMeters) {
                            if (it!! > 0f) draft = PlannerDomainOps.resized(draft, it, draft.heightMeters)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        MetersField(Res.string.type_depth, draft.heightMeters) {
                            if (it!! > 0f) draft = PlannerDomainOps.resized(draft, draft.widthMeters, it)
                        }
                    }
                }
                MetersField(Res.string.type_margin, draft.defaultSafetyMarginMeters) {
                    draft =
                        draft.copy(defaultSafetyMarginMeters = it!!)
                }
                if (!builtInSail) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.type_is_sail), modifier = Modifier.weight(1f))
                        Switch(checked = draft.isSunSail, onCheckedChange = { draft = draft.copy(isSunSail = it) })
                    }
                }

                HorizontalDivider()
                Text(stringResource(Res.string.own_spacing), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.own_spacing_hint), style = MaterialTheme.typography.bodySmall)
                val own = draft.spacing
                val fallback = Res.string.spacing_camp_default
                MetersField(Res.string.own_spacing_tents, own.toTents, fallback) { draft = draft.copy(spacing = own.copy(toTents = it)) }
                MetersField(Res.string.own_spacing_sails, own.toSails, fallback) { draft = draft.copy(spacing = own.copy(toSails = it)) }
                MetersField(Res.string.own_spacing_path, own.toPath, fallback) { draft = draft.copy(spacing = own.copy(toPath = it)) }

                HorizontalDivider()
                Text(stringResource(Res.string.type_ropes, draft.ropeAnchors.size), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.type_ropes_hint), style = MaterialTheme.typography.bodySmall)
                draft.ropeAnchors.forEachIndexed { i, rope ->
                    key(i, ropeRevision) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                MetersField(Res.string.rope_n_stake, rope.lengthMeters, labelArg = i + 1) {
                                    if (it!! >
                                        0f
                                    ) {
                                        draft =
                                            draft.copy(
                                                ropeAnchors =
                                                    draft.ropeAnchors.toMutableList().also { l ->
                                                        l[i] =
                                                            rope.copy(lengthMeters = it)
                                                    },
                                            )
                                    }
                                }
                            }
                            TextButton(
                                onClick = {
                                    draft = draft.copy(ropeAnchors = draft.ropeAnchors.filterIndexed { j, _ -> j != i })
                                    ropeRevision++
                                },
                            ) {
                                Text(stringResource(Res.string.rope_remove))
                            }
                        }
                    }
                }
                if (draft.ropeAnchors.isNotEmpty()) {
                    Text(stringResource(Res.string.rope_slope_title), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { MetersField(Res.string.rope_slope_height, ropeHeight) { ropeHeight = it } }
                        Column(Modifier.weight(1f)) { MetersField(Res.string.rope_slope_deg, ropeSlope, suffix = "°") { ropeSlope = it } }
                    }
                    val height = ropeHeight
                    val slope = ropeSlope?.takeIf { it in SLOPE_MIN_DEG..SLOPE_MAX_DEG }
                    if (height != null && height > 0f && slope != null) {
                        val distance = PlannerDomainOps.stakeDistance(height, slope)
                        Text(
                            stringResource(Res.string.rope_slope_result, formatMeters(round2(distance))),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = {
                                draft = draft.copy(ropeAnchors = draft.ropeAnchors.map { it.copy(lengthMeters = round2(distance)) })
                                ropeRevision++
                            },
                        ) {
                            Text(stringResource(Res.string.rope_slope_apply))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = valid,
                    onClick = {
                        val name = if (draft.name == shownName) "$shownName $copySuffix" else draft.name
                        val sail = draft.isSunSail || builtInSail
                        onConfirm(draft.copy(id = "custom_" + Random.nextLong().toULong().toString(36), name = name, isSunSail = sail))
                    },
                ) { Text(stringResource(Res.string.type_save_as_new)) }
                Button(
                    enabled = valid,
                    onClick = { onConfirm(draft.copy(name = finalName(draft.name))) },
                ) { Text(stringResource(Res.string.take_over)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

private const val NAME_MAX = 60
private const val SLOPE_DEFAULT_DEG = 45f
private const val SLOPE_MIN_DEG = 10f
private const val SLOPE_MAX_DEG = 80f

private fun round2(v: Float) = kotlin.math.round(v * 100f) / 100f
