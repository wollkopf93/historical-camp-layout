package de.voculupo.historicalcamplayout

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import de.voculupo.historicalcamplayout.app.App
import de.voculupo.historicalcamplayout.features.planner.state.PlannerViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

/** Renders the real desktop App and presses real keys (audit finding 1). */
@OptIn(ExperimentalTestApi::class)
class AppKeyboardUiTest {
    @Test
    fun deleteAndEscape_inLabelField_editText_andKeepTent() =
        runComposeUiTest {
            val vm = PlannerViewModel(InMemoryStorage())
            vm.placePresetAt("round_4", Vec2(5f, 5f))
            setContent { App(viewModel = vm) }

            val labelField = onNode(hasSetTextAction() and hasText("Rundzelt 4m"))
            labelField.performClick()
            labelField.performKeyInput {
                pressKey(Key.MoveHome) // caret to start, so forward-Delete removes "R"
                pressKey(Key.Delete)
                pressKey(Key.Escape)
            }
            waitForIdle()

            val assets = vm.uiState.value.project.assets
            assertEquals(1, assets.size, "tent must survive Delete/Escape typed in the label field")
            assertEquals("undzelt 4m", assets.single().label, "Delete should have removed one character")
        }

    @Test
    fun deleteKey_outsideTextField_deletesSelectedTent() =
        runComposeUiTest {
            val vm = PlannerViewModel(InMemoryStorage())
            vm.placePresetAt("round_4", Vec2(5f, 5f))
            setContent { App(viewModel = vm) }
            waitForIdle()

            onRoot().performKeyInput { pressKey(Key.Delete) }
            waitForIdle()

            assertEquals(0, vm.uiState.value.project.assets.size)
        }

    @Test
    fun escapeKey_outsideTextField_keepsSelectedTent() =
        runComposeUiTest {
            val vm = PlannerViewModel(InMemoryStorage())
            vm.placePresetAt("round_4", Vec2(5f, 5f))
            setContent { App(viewModel = vm) }
            waitForIdle()

            onRoot().performKeyInput { pressKey(Key.Escape) }
            waitForIdle()

            assertEquals(1, vm.uiState.value.project.assets.size)
        }
}
