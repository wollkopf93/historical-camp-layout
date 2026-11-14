package de.voculupo.historicalcamplayout.features.properties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.voculupo.historicalcamplayout.SpacingRules
import lagerplaner.composeapp.generated.resources.Res
import lagerplaner.composeapp.generated.resources.cancel
import lagerplaner.composeapp.generated.resources.spacing_hint
import lagerplaner.composeapp.generated.resources.spacing_sail_path
import lagerplaner.composeapp.generated.resources.spacing_tent_path
import lagerplaner.composeapp.generated.resources.spacing_tent_sail
import lagerplaner.composeapp.generated.resources.spacing_tent_sail_max
import lagerplaner.composeapp.generated.resources.spacing_tent_tent
import lagerplaner.composeapp.generated.resources.spacing_title
import lagerplaner.composeapp.generated.resources.spacing_unlimited
import lagerplaner.composeapp.generated.resources.take_over
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Number fields for the camp's spacing rules; [withTentToTent] false when the caller shows that one itself. */
@Composable
internal fun SpacingEditor(
    rules: SpacingRules,
    onChange: (SpacingRules) -> Unit,
    withTentToTent: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (withTentToTent) MetersField(Res.string.spacing_tent_tent, rules.tentToTent) { onChange(rules.copy(tentToTent = it!!)) }
        MetersField(Res.string.spacing_tent_sail, rules.tentToSail) { onChange(rules.copy(tentToSail = it!!)) }
        MetersField(Res.string.spacing_tent_sail_max, rules.tentToSailMax, placeholder = Res.string.spacing_unlimited) {
            onChange(rules.copy(tentToSailMax = it))
        }
        MetersField(Res.string.spacing_tent_path, rules.tentToPath) { onChange(rules.copy(tentToPath = it!!)) }
        MetersField(Res.string.spacing_sail_path, rules.sailToPath) { onChange(rules.copy(sailToPath = it!!)) }
    }
}

/**
 * Metres >= 0. With a [placeholder] the field is optional: empty means "no value" (null), the placeholder
 * says what applies then. Invalid text is kept but not passed on.
 */
@Composable
internal fun MetersField(
    label: StringResource,
    value: Float?,
    placeholder: StringResource? = null,
    onValue: (Float?) -> Unit,
) {
    val optional = placeholder != null
    var text by remember { mutableStateOf(value?.let(::formatMeters).orEmpty()) }
    val parsed = text.replace(',', '.').toFloatOrNull()?.takeIf { it >= 0f }
    val valid = parsed != null || (optional && text.isBlank())
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            val v = it.replace(',', '.').toFloatOrNull()?.takeIf { f -> f >= 0f }
            if (v != null || (optional && it.isBlank())) onValue(v)
        },
        singleLine = true,
        isError = !valid,
        label = { Text(stringResource(label)) },
        placeholder = { placeholder?.let { Text(stringResource(it)) } },
        suffix = { Text("m") },
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun formatMeters(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()

@Composable
internal fun SpacingDialog(
    rules: SpacingRules,
    onConfirm: (SpacingRules) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(rules) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.spacing_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.spacing_hint), style = MaterialTheme.typography.bodySmall)
                SpacingEditor(draft, { draft = it })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(draft) }) { Text(stringResource(Res.string.take_over)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
