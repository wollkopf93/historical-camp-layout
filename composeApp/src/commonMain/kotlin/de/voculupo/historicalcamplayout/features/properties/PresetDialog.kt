package de.voculupo.historicalcamplayout.features.properties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.voculupo.historicalcamplayout.TentPreset
import lagerplaner.composeapp.generated.resources.Res
import lagerplaner.composeapp.generated.resources.cancel
import lagerplaner.composeapp.generated.resources.edit_tent_type
import lagerplaner.composeapp.generated.resources.own_spacing
import lagerplaner.composeapp.generated.resources.own_spacing_hint
import lagerplaner.composeapp.generated.resources.own_spacing_path
import lagerplaner.composeapp.generated.resources.own_spacing_sails
import lagerplaner.composeapp.generated.resources.own_spacing_tents
import lagerplaner.composeapp.generated.resources.spacing_camp_default
import lagerplaner.composeapp.generated.resources.take_over
import org.jetbrains.compose.resources.stringResource

/** Edits a tent type: its own gaps that replace the camp's spacing rules. */
@Composable
internal fun PresetDialog(
    preset: TentPreset,
    onConfirm: (TentPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(preset.id) { mutableStateOf(preset) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit_tent_type) + ": " + preset.name) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.own_spacing), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.own_spacing_hint), style = MaterialTheme.typography.bodySmall)
                val own = draft.spacing
                val fallback = Res.string.spacing_camp_default
                MetersField(Res.string.own_spacing_tents, own.toTents, fallback) { draft = draft.copy(spacing = own.copy(toTents = it)) }
                MetersField(Res.string.own_spacing_sails, own.toSails, fallback) { draft = draft.copy(spacing = own.copy(toSails = it)) }
                MetersField(Res.string.own_spacing_path, own.toPath, fallback) { draft = draft.copy(spacing = own.copy(toPath = it)) }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(draft) }) { Text(stringResource(Res.string.take_over)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
