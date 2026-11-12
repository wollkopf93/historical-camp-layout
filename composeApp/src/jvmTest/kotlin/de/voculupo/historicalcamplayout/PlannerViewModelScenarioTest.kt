package de.voculupo.historicalcamplayout

import de.voculupo.historicalcamplayout.features.planner.state.PlannerViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** User flows from the audit, driven through the real ViewModel with in-memory storage. */
class PlannerViewModelScenarioTest {
    private fun PlannerViewModel.assets() = uiState.value.project.assets

    @Test
    fun closingWindow_savesAndNextStartRestores() {
        val storage = InMemoryStorage()
        val vm = PlannerViewModel(storage)
        vm.placePresetAt("round_4", Vec2(5f, 5f))
        vm.saveProject() // what the desktop close handler calls

        assertEquals(1, PlannerViewModel(storage).assets().size)
    }

    @Test
    fun autosave_writesWithoutClickingSave() {
        val storage = InMemoryStorage()
        val vm = PlannerViewModel(storage)
        Thread.sleep(300) // let the autosave watcher start, as it has long before a user's first edit
        vm.placePresetAt("round_4", Vec2(5f, 5f))
        Thread.sleep(2500) // autosave debounce is 1.2 s

        assertEquals(1, storage.saved?.assets?.size)
    }

    @Test
    fun sliderDrag_isOneUndoStep() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.placePresetAt("round_4", Vec2(5f, 5f))
        (1..120).forEach { vm.setSelectedRotation(it.toFloat()) } // 120 slider frames > history limit
        vm.commitHistory() // slider released

        vm.undo()
        assertEquals(0f, vm.assets().single().rotationDeg, "one undo returns to the angle before the drag")
        vm.undo()
        assertTrue(vm.assets().isEmpty(), "the placement before the drag is still undoable")
    }

    @Test
    fun movingTent_isOneUndoStep() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.placePresetAt("round_4", Vec2(5f, 5f))
        (1..40).forEach { vm.dragSelection(Vec2(0.0999f, 0f)) } // drag frames, ending off-grid
        vm.finishInteractionWithGridSnap() // mouse released: grid snap

        vm.undo()
        assertEquals(Vec2(5f, 5f), vm.assets().single().position, "one undo reverts drag and snap together")
    }

    @Test
    fun zoom_isNotAnUndoStep() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.placePresetAt("round_4", Vec2(5f, 5f))
        repeat(10) { vm.zoomIn() }
        val zoom = vm.uiState.value.project.pixelsPerMeter

        vm.undo()
        assertTrue(vm.assets().isEmpty(), "undo skips zoom and removes the tent")
        assertEquals(zoom, vm.uiState.value.project.pixelsPerMeter, "undo keeps the current zoom")
    }

    @Test
    fun rotatedTent_collidesWithNeighbour() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.updateParcel(30f, 30f)
        vm.placePresetAt("oval_6x4", Vec2(10f, 10f))
        vm.setSelectedRotation(90f)
        vm.toggleRopesForSelected()
        vm.placePresetAt("round_3x3", Vec2(10f, 13.5f))
        vm.toggleRopesForSelected()

        assertEquals(2, vm.uiState.value.hardCollisionAssetIds.size)
    }

    @Test
    fun rotatedTent_staysInsideParcelWhenDragged() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.placePresetAt("sun_sail_6x2_flat", Vec2(5f, 5f))
        vm.setSelectedRotation(90f)
        vm.dragSelection(Vec2(0f, -10f))
        vm.finishInteractionWithGridSnap()

        val sail = vm.assets().single()
        assertTrue(sail.position.y >= 3f - 0.001f, "rotated 6 m sail must keep its top edge in the parcel, y=${sail.position.y}")
    }

    @Test
    fun removedRopes_stayRemovedAfterRestart() {
        val storage = InMemoryStorage()
        val vm = PlannerViewModel(storage)
        vm.placePresetAt("round_4", Vec2(5f, 5f))
        repeat(4) { vm.removeRopeFromSelected() }
        vm.saveProject()

        val reloaded = PlannerViewModel(storage).assets().single()
        assertTrue(reloaded.ropes.isEmpty())
        assertTrue(GeometryEngine.ropeSegments(reloaded).isEmpty())
    }

    @Test
    fun sunSail_droppedFarAway_doesNotJumpToOtherSail() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.placePresetAt("sun_sail_4x4_flat", Vec2(5f, 5f))
        vm.placePresetAt("sun_sail_4x4_flat", Vec2(15f, 10f))
        vm.dragSelection(Vec2(0.1f, 0f))
        vm.finishInteractionWithGridSnap()

        val moved = assertNotNull(vm.assets().lastOrNull())
        assertEquals(Vec2(15f, 10f), moved.position)
    }
}
