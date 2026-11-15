package de.voculupo.historicalcamplayout

import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.voculupo.historicalcamplayout.app.App
import de.voculupo.historicalcamplayout.features.planner.state.PlannerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The map's finger gestures on a real phone/emulator (compact layout, real touch slop and density).
 * Run with: ./gradlew :androidApp:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class TouchGesturesDeviceTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private lateinit var vm: PlannerViewModel

    private val canvas get() = rule.onNodeWithTag("planner-canvas") // PLANNER_CANVAS_TAG

    /** Fresh in-memory plan, so tests never touch the plan saved on the device. */
    @Before
    fun setUp() {
        vm = PlannerViewModel(MemoryStorage())
        rule.runOnUiThread { rule.activity.setContent { App(viewModel = vm) } }
        rule.waitForIdle()
    }

    /** Canvas pixel of a parcel point; mirrors centeredParcelOriginPx with the map not panned. */
    private fun px(meters: Vec2): Offset {
        val size = canvas.fetchSemanticsNode().size
        val p = vm.uiState.value.project
        val ppm = p.pixelsPerMeter
        val ox = ((size.width - p.parcel.widthMeters * ppm) / 2f).coerceAtLeast(16f)
        val oy = ((size.height - p.parcel.heightMeters * ppm) / 2f).coerceAtLeast(16f)
        return Offset(ox + meters.x * ppm, oy + meters.y * ppm)
    }

    private fun fingerDrag(path: List<Offset>) {
        canvas.performTouchInput {
            down(path.first())
            path.zipWithNext().forEach { (a, b) -> (1..10).forEach { moveTo(a + (b - a) * (it / 10f)) } }
            up()
        }
        rule.waitForIdle()
    }

    private fun positions() = vm.uiState.value.project.assets.associate { it.id to it.position }

    @Test
    fun fingerDragOnTent_movesItUnderTheFinger() {
        rule.runOnUiThread { vm.placePresetAt("round_4", Vec2(6f, 6f)) }
        val start = px(Vec2(6f, 6f))
        fingerDrag(listOf(start, start + Offset(3f * vm.uiState.value.project.pixelsPerMeter, 0f)))
        assertEquals(Vec2(9f, 6f), vm.uiState.value.project.assets.single().position)
    }

    @Test
    fun panBesideUnselectedTent_doesNotMoveIt() {
        rule.runOnUiThread {
            vm.placePresetAt("round_4", Vec2(6f, 6f))
            vm.setSelection(emptySet())
        }
        val asset = vm.uiState.value.project.assets.single()
        val body = GeometryEngine.assetBodyAabb(asset, vm.uiState.value.presets.first { it.id == asset.presetId })
        val start = px(Vec2(body.right + 0.3f, 6f))
        fingerDrag(listOf(start, start + Offset(200f, 0f)))
        assertEquals(Vec2(6f, 6f), vm.uiState.value.project.assets.single().position)
    }

    @Test
    fun afterAutoSetup_panWithAllSelected_movesNothing_andSmallNudgeKeepsSpot() {
        rule.runOnUiThread {
            vm.autoSetup(AutoSetupRequest(Parcel(20f, 14f), List(3) { "round_3x3" }, emptyList(), VisitorSide.BOTTOM))
            vm.setZoomLive(25f)
        }
        rule.waitForIdle()
        val before = positions()
        val tent = vm.uiState.value.project.assets.first()
        val ppm = vm.uiState.value.project.pixelsPerMeter
        val s = px(tent.position)
        fingerDrag(listOf(s, s + Offset(ppm, 0f), s + Offset(0.1f * ppm, 0f))) // net 10 cm
        assertEquals(before, positions())

        rule.runOnUiThread { vm.setSelection(before.keys) }
        val corner = px(Vec2(0.5f, 0.5f))
        fingerDrag(listOf(corner, corner + Offset(150f, 150f)))
        assertEquals(before, positions())
    }

    @Test
    fun pinchOut_zoomsIn() {
        val before = vm.uiState.value.project.pixelsPerMeter
        val c = px(Vec2(12f, 7f))
        canvas.performTouchInput {
            pinch(c - Offset(40f, 0f), c - Offset(250f, 0f), c + Offset(40f, 0f), c + Offset(250f, 0f), durationMillis = 400)
        }
        rule.waitForIdle()
        val after = vm.uiState.value.project.pixelsPerMeter
        assertTrue("zoom $before -> $after", after > before && after <= 180f)
    }
}

private class MemoryStorage : ProjectStorage {
    private var saved: PlannerProject? = null

    override fun saveProject(project: PlannerProject) {
        saved = project
    }

    override fun loadProject(): PlannerProject? = saved

    override fun exportProjectFile(project: PlannerProject) = Unit

    override fun importProjectFile(): PlannerProject? = null

    override fun saveInventory(presets: List<TentPreset>) = Unit

    override fun loadInventory(): List<TentPreset>? = null

    override fun exportProjectPng(image: ImageBitmap) = Unit
}
