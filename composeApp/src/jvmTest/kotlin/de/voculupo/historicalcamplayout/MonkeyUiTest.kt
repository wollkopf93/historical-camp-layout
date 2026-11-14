package de.voculupo.historicalcamplayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.withKeyDown
import de.voculupo.historicalcamplayout.app.App
import de.voculupo.historicalcamplayout.features.planner.canvas.PLANNER_CANVAS_TAG
import de.voculupo.historicalcamplayout.features.planner.state.PlannerViewModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Monkey test on the real desktop UI: random clicks on anything clickable (buttons, chips, menu items,
 * dialogs), random text in any text field, random slider values, canvas clicks and drags, and keyboard
 * shortcuts. Any exception from the app fails the test; after every action the state must stay sane.
 */
@OptIn(ExperimentalTestApi::class)
class MonkeyUiTest {
    private val hasProgress = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)

    private fun DesktopComposeUiTest.nodes(matcher: SemanticsMatcher): List<SemanticsNodeInteraction> {
        val count = onAllNodes(matcher).fetchSemanticsNodes().size
        return List(count) { onAllNodes(matcher)[it] }
    }

    private fun DesktopComposeUiTest.labelOf(node: SemanticsNodeInteraction): String {
        val config = node.fetchSemanticsNode().config
        return (
            config.getOrNull(SemanticsProperties.Text)?.joinToString()
                ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                ?: "?"
        ).take(40)
    }

    // Inventory cards react to pointer taps only (no semantics click), so the monkey taps them by their text.
    private val presetNames = defaultPresets().map { it.name }.toSet()
    private val isPresetCard =
        SemanticsMatcher("tent card") { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.any {
                it.text in
                    presetNames
            } ==
                true
        }

    private val texts = listOf("", "0", "-3", "abc", "2,5", "1.5", "99999", "Küche", " ", "1e9", "0.01", "7")

    // Only enabled controls: a disabled button cannot be clicked by a user, its semantics action still could.

    /** One random action; returns what it did. Test-framework misses (a node gone or off screen) are not app bugs. */
    private fun DesktopComposeUiTest.step(rnd: Random): String {
        val kind = rnd.nextInt(100)
        return try {
            when {
                kind < 14 -> {
                    val cards = nodes(isPresetCard)
                    if (cards.isEmpty()) return "no tent card"
                    val card = cards.random(rnd)
                    val label = labelOf(card)
                    if (rnd.nextInt(4) == 0) {
                        card.performMouseInput { click() }
                        "tap card '$label'"
                    } else {
                        // Desktop places tents by drag and drop onto the canvas (sometimes dropped anywhere else).
                        val from = card.fetchSemanticsNode().boundsInRoot
                        val canvas = onNodeWithTag(PLANNER_CANVAS_TAG).fetchSemanticsNode().boundsInRoot
                        val drop =
                            Offset(
                                canvas.left + rnd.nextFloat() * canvas.width * 1.2f,
                                canvas.top + rnd.nextFloat() * canvas.height * 1.2f,
                            )
                        card.performMouseInput {
                            moveTo(center)
                            press()
                            val target = drop - from.topLeft
                            (1..12).forEach { k -> moveTo(center + (target - center) * (k / 12f)) }
                            release()
                        }
                        "drop card '$label' at $drop"
                    }
                }

                kind < 45 -> {
                    val all = nodes(hasClickAction() and isEnabled())
                    if (all.isEmpty()) return "no clickable node"
                    val node = all.random(rnd)
                    val label = labelOf(node)
                    // Semantics click: also reaches items scrolled out of view, like a user who scrolls first.
                    node.performSemanticsAction(SemanticsActions.OnClick)
                    "click '$label'"
                }

                kind < 60 -> {
                    val fields = nodes(hasSetTextAction() and isEnabled())
                    if (fields.isEmpty()) return "no text field"
                    val field = fields.random(rnd)
                    val text = texts.random(rnd)
                    val label = labelOf(field)
                    field.performTextReplacement(text)
                    "type '$text' into '$label'"
                }

                kind < 68 -> {
                    val sliders = nodes(hasProgress and isEnabled())
                    if (sliders.isEmpty()) return "no slider"
                    val v = rnd.nextFloat() * 400f - 50f
                    sliders.random(rnd).performSemanticsAction(SemanticsActions.SetProgress) { it(v) }
                    "slider $v"
                }

                kind < 88 -> {
                    val canvas = onNodeWithTag(PLANNER_CANVAS_TAG)
                    val size = canvas.fetchSemanticsNode().size

                    fun spot() = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
                    val a = spot()
                    if (rnd.nextBoolean()) {
                        canvas.performMouseInput { click(a) }
                        "canvas click $a"
                    } else {
                        val b = spot()
                        canvas.performMouseInput {
                            moveTo(a)
                            press()
                            repeat(5) { i -> moveTo(a + (b - a) * ((i + 1) / 5f)) }
                            release()
                        }
                        "canvas drag $a -> $b"
                    }
                }

                else -> {
                    val key = listOf(Key.Delete, Key.Escape, Key.Z, Key.Y, Key.D, Key.Backspace).random(rnd)
                    val ctrl = rnd.nextBoolean()
                    onNode(isRoot()).performKeyInput { if (ctrl) withKeyDown(Key.CtrlLeft) { pressKey(key) } else pressKey(key) }
                    "key ${if (ctrl) "ctrl+" else ""}$key"
                }
            }
        } catch (e: AssertionError) {
            "skipped (${e.message?.lineSequence()?.firstOrNull()?.take(60)})"
        }
    }

    private fun runMonkey(
        seed: Int,
        steps: Int,
    ): List<String> {
        val problems = mutableListOf<String>()
        val log = mutableListOf<String>()
        val rnd = Random(seed)
        var maxAssets = 0
        runDesktopComposeUiTest(width = 1600, height = 1000) {
            val vm =
                PlannerViewModel(InMemoryStorage()).apply {
                    // Every other run starts from a camp, so selection, properties and the editor get used early.
                    if (seed % 2 == 0) {
                        autoSetup(
                            AutoSetupRequest(
                                Parcel(30f, 20f),
                                listOf("saxon_2x4", "round_4", "a_tent_2_5x3_full", "saxon_3x5"),
                                listOf("sun_sail_6x6_flat"),
                                VisitorSide.BOTTOM,
                                optimize = false,
                            ),
                        )
                        setSelection(
                            setOf(
                                uiState.value.project.assets
                                    .first()
                                    .id,
                            ),
                        )
                    }
                }
            setContent { App(viewModel = vm) }
            waitForIdle()
            for (i in 0 until steps) {
                val t0 = System.currentTimeMillis()
                val what =
                    try {
                        step(rnd).also {
                            mainClock.advanceTimeBy(300) // let gesture timeouts and animations finish
                            waitForIdle()
                        }
                    } catch (e: Throwable) {
                        val trace =
                            e
                                .stackTraceToString()
                                .lines()
                                .filter { "voculupo" in it || "Caused" in it }
                                .take(12)
                        problems += "seed $seed step $i crashed: $e\n      ${trace.joinToString("\n      ")}\n    last: ${log.takeLast(8)}"
                        break
                    }
                log += what
                val ms = System.currentTimeMillis() - t0
                if (ms > SLOW_MS) println("monkey seed $seed slow step $i ($ms ms): $what")
                maxAssets = maxOf(maxAssets, vm.uiState.value.project.assets.size)
                val bad = saneStateProblems(vm.uiState.value)
                if (bad.isNotEmpty()) {
                    problems += "seed $seed after step $i ($what): $bad\n    last: ${log.takeLast(8)}"
                    break
                }
            }
            println(
                "monkey seed $seed: ${log.size} actions, ${log.count { it.startsWith("skipped") }} skipped, " +
                    "up to $maxAssets assets, ${log.filter {
                        it.startsWith(
                            "click",
                        )
                    }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(25)}",
            )
        }
        return problems
    }

    @Test
    fun randomUiFlows_neverCrashAndKeepTheStateSane() {
        val failures = SEEDS.flatMap { runMonkey(it, STEPS) }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private companion object {
        val SEEDS = listOf(1, 2, 3, 4)
        const val STEPS = 150
        const val SLOW_MS = 3000L
    }
}
