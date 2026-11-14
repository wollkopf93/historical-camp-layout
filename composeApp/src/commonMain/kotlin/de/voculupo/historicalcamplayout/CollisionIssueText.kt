package de.voculupo.historicalcamplayout

import androidx.compose.runtime.Composable
import lagerplaner.composeapp.generated.resources.Res
import lagerplaner.composeapp.generated.resources.issue_collision
import lagerplaner.composeapp.generated.resources.issue_entry_blocked
import lagerplaner.composeapp.generated.resources.issue_in_no_go_zone
import lagerplaner.composeapp.generated.resources.issue_narrow_walkway
import lagerplaner.composeapp.generated.resources.issue_near_path
import lagerplaner.composeapp.generated.resources.issue_outside_parcel
import lagerplaner.composeapp.generated.resources.issue_rope_collision
import lagerplaner.composeapp.generated.resources.issue_sail_overlap
import lagerplaner.composeapp.generated.resources.issue_too_far_from_sail
import org.jetbrains.compose.resources.stringResource

/** The plan's warnings as sentences in the app language, naming tents as the canvas labels them. */
@Composable
fun rememberWarningTexts(state: PlannerUiState): List<String> {
    val presetNames = rememberPresetNames(state.presets)
    val assetNames =
        state.project.assets.associate { asset ->
            asset.id to (presetNames[asset.presetId]?.let { assetDisplayLabel(asset, it) } ?: asset.label)
        }
    return state.warnings.map { issueText(it, assetNames) }
}

@Composable
private fun issueText(
    issue: CollisionIssue,
    assetNames: Map<String, String>,
): String {
    val (a, b) = issue.assetIds.map { assetNames[it] ?: it } + listOf("", "")
    return when (issue.kind) {
        IssueKind.OUTSIDE_PARCEL -> {
            stringResource(Res.string.issue_outside_parcel, a)
        }

        IssueKind.SAIL_OVERLAP -> {
            stringResource(Res.string.issue_sail_overlap, a, b)
        }

        IssueKind.COLLISION -> {
            stringResource(Res.string.issue_collision, a, b)
        }

        IssueKind.ROPE_COLLISION -> {
            stringResource(Res.string.issue_rope_collision, a, b)
        }

        IssueKind.ENTRY_BLOCKED -> {
            stringResource(Res.string.issue_entry_blocked, a, b)
        }

        IssueKind.NARROW_WALKWAY -> {
            stringResource(Res.string.issue_narrow_walkway, a, b, "%.1f".format(issue.walkwayMeters), "%.2f".format(issue.gapMeters))
        }

        IssueKind.IN_NO_GO_ZONE -> {
            stringResource(Res.string.issue_in_no_go_zone, a)
        }

        IssueKind.TOO_FAR_FROM_SAIL -> {
            stringResource(Res.string.issue_too_far_from_sail, a, "%.1f".format(issue.walkwayMeters), "%.2f".format(issue.gapMeters))
        }

        IssueKind.NEAR_PATH -> {
            stringResource(Res.string.issue_near_path, a, "%.1f".format(issue.walkwayMeters), "%.2f".format(issue.gapMeters))
        }
    }
}
