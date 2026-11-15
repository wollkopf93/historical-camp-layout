package de.voculupo.historicalcamplayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.voculupo.historicalcamplayout.app.App
import de.voculupo.historicalcamplayout.features.planner.canvas.PLANNER_CANVAS_TAG
import de.voculupo.historicalcamplayout.features.planner.canvas.centeredParcelOriginPx
import de.voculupo.historicalcamplayout.features.planner.canvas.parcelSizePx
import de.voculupo.historicalcamplayout.features.planner.state.PlannerViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Finger gestures on the map (phones and tablets): moving, panning and pinching must only change what
 * the user aimed at. Each test is a regression for a bug seen on a real device.
 */
@OptIn(ExperimentalTestApi::class)
class CanvasTouchUiTest {
    private val smallCamp =
        AutoSetupRequest(Parcel(30f, 20f), List(4) { "round_3x3" }, emptyList(), VisitorSide.BOTTOM)

    private fun runApp(
        setup: PlannerViewModel.() -> Unit = {},
        test: DesktopComposeUiTest.(PlannerViewModel) -> Unit,
    ) = runDesktopComposeUiTest(width = 1600, height = 1000) {
        val vm = PlannerViewModel(InMemoryStorage()).apply(setup)
        setContent { App(viewModel = vm) }
        waitForIdle()
        test(vm)
    }

    /** Canvas-local pixel position of a point in parcel meters (map not panned). */
    private fun DesktopComposeUiTest.canvasPx(
        vm: PlannerViewModel,
        meters: Vec2,
    ): Offset {
        val bounds = onNodeWithTag(PLANNER_CANVAS_TAG).fetchSemanticsNode().boundsInRoot
        val project = vm.uiState.value.project
        val origin = centeredParcelOriginPx(Size(bounds.width, bounds.height), parcelSizePx(project))
        return origin + Offset(meters.x * project.pixelsPerMeter, meters.y * project.pixelsPerMeter)
    }

    /** One finger down, moved in small steps through [path], then lifted. */
    private fun DesktopComposeUiTest.fingerDrag(path: List<Offset>) {
        onNodeWithTag(PLANNER_CANVAS_TAG).performTouchInput {
            down(path.first())
            path.zipWithNext().forEach { (a, b) -> (1..10).forEach { moveTo(a + (b - a) * (it / 10f)) } }
            up()
        }
        mainClock.advanceTimeBy(500)
        waitForIdle()
    }

    private fun PlannerViewModel.positions() = uiState.value.project.assets.associate { it.id to it.position }

    /** A parcel point at least [clear] m from every tent, so a drag there starts on empty grass. */
    private fun PlannerViewModel.emptySpot(clear: Float = 4f): Vec2 {
        val parcel = uiState.value.project.parcel
        val spots = (1 until parcel.widthMeters.toInt()).flatMap { x -> (1 until parcel.heightMeters.toInt()).map { y -> Vec2(x.toFloat(), y.toFloat()) } }
        return assertNotNull(
            spots.firstOrNull { s -> positions().values.all { GeometryEngine.distance(it, s) > clear } },
            "no empty spot",
        )
    }

    @Test
    fun afterAutoSetup_smallFingerNudge_doesNotJumpOrAddWarnings() =
        runApp(setup = {
            autoSetup(smallCamp)
            setZoomLive(25f)
        }) { vm ->
            val tent = vm.uiState.value.project.assets.first()
            val before = vm.positions()
            val warningsBefore = vm.uiState.value.warnings
            val start = canvasPx(vm, tent.position)
            // Out 1 m and back 0.9 m: a real 10 cm nudge, past the touch slop.
            fingerDrag(listOf(start, start + Offset(25f, 0f), start + Offset(2.5f, 0f)))

            assertEquals(before, vm.positions(), "a 10 cm nudge keeps every tent on its exact spot")
            assertEquals(warningsBefore, vm.uiState.value.warnings)
        }

    @Test
    fun panningTheMap_withTentsSelected_movesNoTentAndAddsNoUndoStep() =
        runApp(setup = {
            autoSetup(smallCamp)
            setZoomLive(25f)
        }) { vm ->
            vm.setSelection(vm.positions().keys)
            waitForIdle()
            val before = vm.positions()
            val canUndoBefore = vm.uiState.value.canUndo
            val projectBefore = vm.uiState.value.project
            val start = canvasPx(vm, vm.emptySpot())
            fingerDrag(listOf(start, start + Offset(150f, 80f)))

            assertEquals(before, vm.positions(), "a pan only moves the view")
            assertEquals(canUndoBefore, vm.uiState.value.canUndo)
            assertEquals(projectBefore, vm.uiState.value.project)
        }

    @Test
    fun dragStartingJustBesideAnUnselectedTent_pansInsteadOfMovingIt() =
        runApp(setup = {
            placePresetAt("round_4", Vec2(10f, 8f))
            setSelection(emptySet())
        }) { vm ->
            val asset = vm.uiState.value.project.assets.single()
            val preset = vm.uiState.value.presets.first { it.id == asset.presetId }
            val body = GeometryEngine.assetBodyAabb(asset, preset)
            val start = canvasPx(vm, Vec2(body.right + 0.3f, asset.position.y))
            fingerDrag(listOf(start, start + Offset(200f, 0f)))

            assertEquals(Vec2(10f, 8f), vm.uiState.value.project.assets.single().position)
        }

    @Test
    fun fingerDragOnATent_movesItOnTheGrid() =
        runApp(setup = { placePresetAt("round_4", Vec2(10f, 8f)) }) { vm ->
            val ppm = vm.uiState.value.project.pixelsPerMeter
            val start = canvasPx(vm, Vec2(10f, 8f))
            fingerDrag(listOf(start, start + Offset(3f * ppm, 0f)))

            assertEquals(Vec2(13f, 8f), vm.uiState.value.project.assets.single().position)
        }

    @Test
    fun pinchOut_zoomsInWithinLimits_andMovesNothing() =
        runApp(setup = { placePresetAt("round_4", Vec2(4f, 4f)) }) { vm ->
            val before = vm.positions()
            val ppmBefore = vm.uiState.value.project.pixelsPerMeter
            val c = canvasPx(vm, Vec2(12f, 10f)) // well clear of the tent
            onNodeWithTag(PLANNER_CANVAS_TAG).performTouchInput {
                pinch(c - Offset(40f, 0f), c - Offset(250f, 0f), c + Offset(40f, 0f), c + Offset(250f, 0f), durationMillis = 400)
            }
            waitForIdle()

            val ppm = vm.uiState.value.project.pixelsPerMeter
            assertTrue(ppm > ppmBefore && ppm <= 180f, "zoom $ppmBefore -> $ppm")
            assertEquals(before, vm.positions())
        }
}
