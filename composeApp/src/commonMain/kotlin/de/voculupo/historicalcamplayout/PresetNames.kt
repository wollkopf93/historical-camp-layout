package de.voculupo.historicalcamplayout

import androidx.compose.runtime.Composable
import lagerplaner.composeapp.generated.resources.Res
import lagerplaner.composeapp.generated.resources.allStringResources
import org.jetbrains.compose.resources.stringResource

// The German names in defaultPresets() are the stored data; the preset_<id> strings translate them for display.
private val builtInNames: Map<String, String> = defaultPresets().associate { it.id to it.name }

/** Name to show for [preset]: built-in presets follow the app language, user-edited ones keep their own name. */
@Composable
fun presetDisplayName(preset: TentPreset): String {
    val res = Res.allStringResources["preset_${preset.id}"]
    return if (res != null && preset.name == builtInNames[preset.id]) stringResource(res) else preset.name
}

/** Preset id to display name, for drawing code that can't read resources itself. */
@Composable
fun rememberPresetNames(presets: List<TentPreset>): Map<String, String> = presets.associate { it.id to presetDisplayName(it) }

/** True when [asset] still carries its preset's built-in name, which placing a tent stores as its label. */
fun hasDefaultLabel(
    asset: PlacedAsset,
    preset: TentPreset,
): Boolean = asset.label.isBlank() || asset.label.trim() == builtInNames[preset.id]

/** Label to show for [asset]: a name the user typed, else the preset's [presetName] in the app language. */
fun assetDisplayLabel(
    asset: PlacedAsset,
    preset: TentPreset,
    presetName: String,
): String = if (hasDefaultLabel(asset, preset)) presetName else asset.label
