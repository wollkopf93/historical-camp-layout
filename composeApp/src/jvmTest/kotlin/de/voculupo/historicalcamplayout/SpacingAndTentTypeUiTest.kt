package de.voculupo.historicalcamplayout

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.voculupo.historicalcamplayout.app.App
import de.voculupo.historicalcamplayout.features.planner.state.PlannerViewModel
import kotlinx.coroutines.runBlocking
import lagerplaner.composeapp.generated.resources.Res
import lagerplaner.composeapp.generated.resources.auto_setup_generate
import lagerplaner.composeapp.generated.resources.auto_setup_short
import lagerplaner.composeapp.generated.resources.cancel
import lagerplaner.composeapp.generated.resources.edit_tent_type
import lagerplaner.composeapp.generated.resources.next
import lagerplaner.composeapp.generated.resources.preset_copy_suffix
import lagerplaner.composeapp.generated.resources.preset_name
import lagerplaner.composeapp.generated.resources.preset_save_as_new
import lagerplaner.composeapp.generated.resources.preset_width
import lagerplaner.composeapp.generated.resources.required_area
import lagerplaner.composeapp.generated.resources.rope_remove
import lagerplaner.composeapp.generated.resources.rope_slope_apply
import lagerplaner.composeapp.generated.resources.rope_slope_height
import lagerplaner.composeapp.generated.resources.spacing_tent_sail
import lagerplaner.composeapp.generated.resources.spacing_tent_sail_max
import lagerplaner.composeapp.generated.resources.spacing_tent_tent
import lagerplaner.composeapp.generated.resources.spacing_title
import lagerplaner.composeapp.generated.resources.take_over
import lagerplaner.composeapp.generated.resources.width_m
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The spacing dialog, the auto setup's spacing and area search, and the tent type editor, driven through the desktop UI. */
@OptIn(ExperimentalTestApi::class)
class SpacingAndTentTypeUiTest {
    private fun text(res: StringResource) = runBlocking { getString(res) }

    private fun runApp(
        setup: PlannerViewModel.() -> Unit = {},
        test: DesktopComposeUiTest.(PlannerViewModel) -> Unit,
    ) = runDesktopComposeUiTest(width = 1600, height = 1000) {
        val vm = PlannerViewModel(InMemoryStorage()).apply(setup)
        setContent { App(viewModel = vm) }
        waitForIdle()
        test(vm)
    }

    private fun DesktopComposeUiTest.field(label: StringResource) = onNode(hasSetTextAction() and hasText(text(label)))

    private fun DesktopComposeUiTest.click(res: StringResource) {
        onNodeWithText(text(res)).performClick()
        waitForIdle()
    }

    private fun PlannerViewModel.spacing() = uiState.value.project.spacing

    private fun PlannerViewModel.type(id: String) = uiState.value.presets.first { it.id == id }

    // --- Spacing dialog ---

    @Test
    fun spacingDialog_cancel_keepsTheRules() =
        runApp { vm ->
            click(Res.string.spacing_title)
            field(Res.string.spacing_tent_tent).performTextReplacement("4")
            click(Res.string.cancel)
            assertEquals(SpacingRules(), vm.spacing())
        }

    @Test
    fun spacingDialog_invalidInput_isNotApplied() =
        runApp { vm ->
            click(Res.string.spacing_title)
            field(Res.string.spacing_tent_tent).performTextReplacement("abc")
            field(Res.string.spacing_tent_sail).performTextReplacement("-1")
            click(Res.string.take_over)
            assertEquals(SpacingRules(), vm.spacing())
        }

    @Test
    fun spacingDialog_maxField_isOptional() =
        runApp { vm ->
            click(Res.string.spacing_title)
            field(Res.string.spacing_tent_sail_max).performTextReplacement("4")
            click(Res.string.take_over)
            assertEquals(4f, vm.spacing().tentToSailMax)

            click(Res.string.spacing_title)
            field(Res.string.spacing_tent_sail_max).performTextReplacement("")
            click(Res.string.take_over)
            assertNull(vm.spacing().tentToSailMax)
        }

    // --- Auto setup ---

    private val camp =
        AutoSetupRequest(Parcel(60f, 50f), listOf("round_4", "round_4", "saxon_2x4"), listOf("sun_sail_6x6_flat"), VisitorSide.BOTTOM)

    private fun DesktopComposeUiTest.toLastStep() = repeat(3) { click(Res.string.next) }

    @Test
    fun autoSetup_spacingFieldsInLastStep_reachTheProject() =
        runApp(setup = { autoSetup(camp) }) { vm ->
            click(Res.string.auto_setup_short)
            toLastStep()
            field(Res.string.spacing_tent_sail).performTextReplacement("0,5")
            click(Res.string.auto_setup_generate)
            assertEquals(0.5f, vm.spacing().tentToSail)
            assertEquals(
                0.5f,
                vm.uiState.value.project.autoSetup!!
                    .spacing.tentToSail,
            )
        }

    @Test
    fun autoSetup_requiredArea_shrinksTheParcelAndEverythingFits() =
        runApp(setup = { autoSetup(camp) }) { vm ->
            click(Res.string.auto_setup_short)
            click(Res.string.required_area)
            // The search runs off the main thread; the dialog's width field changes when it is done.
            waitUntil(timeoutMillis = 30_000) {
                onAllNodes(hasSetTextAction() and hasText(text(Res.string.width_m)))
                    .fetchSemanticsNodes()
                    .any { n ->
                        n.config
                            .getOrNull(SemanticsProperties.EditableText)
                            ?.text
                            .let { it != null && it != "60.0" }
                    }
            }
            toLastStep()
            click(Res.string.auto_setup_generate)
            val project = vm.uiState.value.project
            assertTrue(project.parcel.widthMeters * project.parcel.heightMeters < 60f * 50f, "parcel ${project.parcel} did not shrink")
            assertEquals(4, project.assets.size, "every tent and the sail fit on ${project.parcel}")
        }

    // --- Tent type editor ---

    @Test
    fun tentEditor_saveAsNew_addsACopyAndKeepsTheOriginal() =
        runApp(setup = { placePresetAt("round_4", Vec2(5f, 5f)) }) { vm ->
            val original = vm.type("round_4")
            click(Res.string.edit_tent_type)
            click(Res.string.preset_save_as_new)
            val copy =
                vm.uiState.value.presets
                    .single { it.name == "${original.name} ${text(Res.string.preset_copy_suffix)}" }
            assertTrue(copy.id.startsWith("custom_"))
            assertEquals(original.copy(id = copy.id, name = copy.name, userModified = true), copy)
            assertEquals(original, vm.type("round_4"))
        }

    @Test
    fun tentEditor_slopeHelper_setsEveryPegDistance_andPlacedTentFollows() =
        runApp(setup = { placePresetAt("saxon_2x4", Vec2(8f, 8f)) }) { vm ->
            click(Res.string.edit_tent_type)
            field(Res.string.rope_slope_height).performTextReplacement("2") // 45 degrees by default
            // Below the fold of the scrolling dialog: a click on hidden bounds would land outside and close it.
            onNodeWithText(text(Res.string.rope_slope_apply)).performScrollTo().performClick()
            waitForIdle()
            click(Res.string.take_over)
            assertTrue(vm.type("saxon_2x4").ropeAnchors.all { it.lengthMeters == 2f }, vm.type("saxon_2x4").ropeAnchors.toString())
            assertTrue(
                vm.uiState.value.project.assets
                    .single()
                    .ropes
                    .all { it.lengthMeters == 2f },
            )
        }

    @Test
    fun tentEditor_removeRope() =
        runApp(setup = { placePresetAt("saxon_2x4", Vec2(8f, 8f)) }) { vm ->
            val before = vm.type("saxon_2x4").ropeAnchors
            click(Res.string.edit_tent_type)
            onAllNodesWithText(text(Res.string.rope_remove)).onFirst().performClick()
            waitForIdle()
            click(Res.string.take_over)
            assertEquals(before.drop(1), vm.type("saxon_2x4").ropeAnchors)
        }

    @Test
    fun tentEditor_resize_stretchesTheOutline() =
        runApp(setup = { placePresetAt("saxon_2x4", Vec2(8f, 8f)) }) { vm ->
            val before = vm.type("saxon_2x4")
            click(Res.string.edit_tent_type)
            field(Res.string.preset_width).performTextReplacement("8")
            click(Res.string.take_over)
            val after = vm.type("saxon_2x4")
            assertEquals(8f, after.widthMeters)
            assertEquals(before.heightMeters, after.heightMeters)
            assertEquals(before.polygonPoints.map { Vec2(it.x * 2f, it.y) }, after.polygonPoints)
        }

    @Test
    fun tentEditor_cancel_changesNothing() =
        runApp(setup = { placePresetAt("round_4", Vec2(5f, 5f)) }) { vm ->
            val before = vm.uiState.value.presets
            click(Res.string.edit_tent_type)
            field(Res.string.preset_name).performTextReplacement("Anders")
            click(Res.string.cancel)
            assertEquals(before, vm.uiState.value.presets)
        }

    @Test
    fun tentEditor_emptyName_cannotBeSaved() =
        runApp(setup = { placePresetAt("round_4", Vec2(5f, 5f)) }) { _ ->
            click(Res.string.edit_tent_type)
            field(Res.string.preset_name).performTextReplacement("")
            waitForIdle()
            onNodeWithText(text(Res.string.take_over)).assertIsNotEnabled()
            onNodeWithText(text(Res.string.preset_save_as_new)).assertIsNotEnabled()
        }
}
