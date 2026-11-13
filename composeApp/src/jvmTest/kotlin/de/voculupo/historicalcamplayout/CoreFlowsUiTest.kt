package de.voculupo.historicalcamplayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.voculupo.historicalcamplayout.app.App
import de.voculupo.historicalcamplayout.features.planner.canvas.PLANNER_CANVAS_TAG
import de.voculupo.historicalcamplayout.features.planner.canvas.centeredParcelOriginPx
import de.voculupo.historicalcamplayout.features.planner.canvas.parcelSizePx
import de.voculupo.historicalcamplayout.features.planner.state.PlannerViewModel
import kotlinx.coroutines.runBlocking
import lagerplaner.composeapp.generated.resources.Res
import lagerplaner.composeapp.generated.resources.auto_setup
import lagerplaner.composeapp.generated.resources.auto_setup_short
import lagerplaner.composeapp.generated.resources.undo
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The flows people use most, driven through the real desktop UI with mouse input:
 * place a tent from the inventory, select it, move it, rename it, rotate it, undo, save.
 */
@OptIn(ExperimentalTestApi::class)
class CoreFlowsUiTest {
    private fun text(res: StringResource) = runBlocking { getString(res) }

    private fun runApp(
        storage: InMemoryStorage = InMemoryStorage(),
        setup: PlannerViewModel.() -> Unit = {},
        test: DesktopComposeUiTest.(PlannerViewModel, InMemoryStorage) -> Unit,
    ) = runDesktopComposeUiTest(width = 1600, height = 1000) {
        val vm = PlannerViewModel(storage).apply(setup)
        setContent { App(viewModel = vm) }
        waitForIdle()
        test(vm, storage)
    }

    /** Canvas-local pixel position of a point given in parcel meters. */
    private fun DesktopComposeUiTest.canvasPx(
        vm: PlannerViewModel,
        meters: Vec2,
    ): Offset {
        val bounds = onNodeWithTag(PLANNER_CANVAS_TAG).fetchSemanticsNode().boundsInRoot
        val project = vm.uiState.value.project
        val origin = centeredParcelOriginPx(Size(bounds.width, bounds.height), parcelSizePx(project))
        return origin + Offset(meters.x * project.pixelsPerMeter, meters.y * project.pixelsPerMeter)
    }

    /** Lets gesture timeouts (double-click window, drop handling) elapse on the test clock. */
    private fun DesktopComposeUiTest.settle() {
        mainClock.advanceTimeBy(500)
        waitForIdle()
    }

    private fun PlannerViewModel.assets() = uiState.value.project.assets

    private fun assertNear(
        expected: Vec2,
        actual: Vec2,
    ) = assertTrue(abs(expected.x - actual.x) <= 0.5f && abs(expected.y - actual.y) <= 0.5f, "expected ~$expected, got $actual")

    @Test
    fun dragPresetFromInventory_placesTentWhereDropped() =
        runApp { vm, _ ->
            val source = onAllNodesWithText("Sachsenzelt 2x4").onFirst()
            val sourceBounds = source.fetchSemanticsNode().boundsInRoot
            val canvasBounds = onNodeWithTag(PLANNER_CANVAS_TAG).fetchSemanticsNode().boundsInRoot
            val dropRoot = canvasBounds.topLeft + canvasPx(vm, Vec2(6f, 6f))

            source.performMouseInput {
                moveTo(center)
                press()
                // Move in steps, like a real hand, so the drag gesture is recognised.
                val target = dropRoot - sourceBounds.topLeft
                (1..20).forEach { step -> moveTo(center + (target - center) * (step / 20f)) }
                release()
            }
            settle()

            val placed = vm.assets().single()
            assertEquals("saxon_2x4", placed.presetId)
            assertNear(Vec2(6f, 6f), placed.position)
        }

    @Test
    fun clickTent_selectsIt_dragMovesIt_undoButtonPutsItBack() =
        runApp(setup = {
            placePresetAt("round_4", Vec2(5f, 5f))
            setSelection(emptySet())
        }) { vm, _ ->
            val canvas = onNodeWithTag(PLANNER_CANVAS_TAG)
            val id = vm.assets().single().id

            canvas.performMouseInput { click(canvasPx(vm, Vec2(5f, 5f))) }
            settle() // a single click is confirmed once the double-click window has passed
            assertEquals(setOf(id), vm.uiState.value.selectedAssetIds, "click on the tent selects it")

            canvas.performMouseInput {
                val from = canvasPx(vm, Vec2(5f, 5f))
                val to = canvasPx(vm, Vec2(9f, 5f))
                moveTo(from)
                press()
                (1..20).forEach { step -> moveTo(from + (to - from) * (step / 20f)) }
                release()
            }
            settle()
            assertNear(Vec2(9f, 5f), vm.assets().single().position)

            onNodeWithContentDescription(text(Res.string.undo)).performClick()
            settle()
            assertEquals(Vec2(5f, 5f), vm.assets().single().position, "one undo reverts the whole drag")
        }

    /** Regression: gesture handlers used to keep the plan from before the first move, so a second drag missed the tent. */
    @Test
    fun secondDragOfSameTent_stillGrabsItAtItsNewPosition() =
        runApp(setup = { placePresetAt("round_4", Vec2(5f, 5f)) }) { vm, _ ->
            val canvas = onNodeWithTag(PLANNER_CANVAS_TAG)

            fun drag(
                fromM: Vec2,
                toM: Vec2,
            ) {
                canvas.performMouseInput {
                    val from = canvasPx(vm, fromM)
                    val to = canvasPx(vm, toM)
                    moveTo(from)
                    press()
                    (1..20).forEach { step -> moveTo(from + (to - from) * (step / 20f)) }
                    release()
                }
                settle()
            }
            drag(Vec2(5f, 5f), Vec2(9f, 5f))
            assertNear(Vec2(9f, 5f), vm.assets().single().position)
            drag(Vec2(9f, 5f), Vec2(9f, 9f))
            assertNear(Vec2(9f, 9f), vm.assets().single().position)
        }

    @Test
    fun renameTent_inPropertiesPane_undoRevertsWholeName() =
        runApp(setup = { placePresetAt("round_4", Vec2(5f, 5f)) }) { vm, _ ->
            onNode(hasSetTextAction() and hasText("Rundzelt 4m")).performTextReplacement("Küche")
            waitForIdle()
            assertEquals("Küche", vm.assets().single().label)

            onNodeWithContentDescription(text(Res.string.undo)).performClick()
            waitForIdle()
            assertEquals("Rundzelt 4m", vm.assets().single().label)
        }

    @Test
    fun rotationSlider_rotatesSelectedTent() =
        runApp(setup = { placePresetAt("round_4", Vec2(5f, 5f)) }) { vm, _ ->
            val rotationSlider =
                SemanticsMatcher("rotation slider 0..360") {
                    it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 0f..360f
                }
            onNode(rotationSlider).performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
            waitForIdle()

            assertEquals(90f, vm.assets().single().rotationDeg, absoluteTolerance = 1f)
        }

    @Test
    fun autoSetupButton_inTopBar_opensSheet() =
        runApp { _, _ ->
            onNodeWithText(text(Res.string.auto_setup_short)).performClick()
            waitForIdle()
            onNodeWithText(text(Res.string.auto_setup)).assertExists()
        }
}
