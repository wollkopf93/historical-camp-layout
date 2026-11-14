package de.voculupo.historicalcamplayout

import de.voculupo.historicalcamplayout.AutoSetupRequest
import de.voculupo.historicalcamplayout.FreeArea
import de.voculupo.historicalcamplayout.FreeAreaKind
import de.voculupo.historicalcamplayout.VisitorSide
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

    @Test
    fun autoSetup_replacesLayoutAsOneUndoStep() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.placePresetAt("round_4", Vec2(5f, 5f))
        val request =
            AutoSetupRequest(Parcel(60f, 50f), List(6) { "saxon_2x4" }, listOf("sun_sail_6x6_flat"), VisitorSide.BOTTOM)

        vm.autoSetup(request)
        assertEquals(7, vm.assets().size)
        assertTrue(
            vm.uiState.value.hardCollisionAssetIds
                .isEmpty(),
        )

        vm.autoSetup(request.copy(replaceExisting = false))
        assertEquals(14, vm.assets().size, "add mode keeps the previous draft")

        vm.undo()
        vm.undo()
        assertEquals("round_4", vm.assets().single().presetId, "undo restores the hand-made layout")
    }

    @Test
    fun autoSetup_replacesOwnZonesKeepsDrawnOnesAndRemembersSetup() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.onBlockZoneTap(Vec2(1f, 1f))
        vm.onBlockZoneTap(Vec2(4f, 4f)) // hand-drawn zone in the top-left corner
        val arena = FreeArea(FreeAreaKind.ARENA, "Kampfplatz", 12f, 10f)
        val request =
            AutoSetupRequest(
                Parcel(60f, 50f),
                List(6) { "saxon_2x4" },
                listOf("sun_sail_6x6_flat"),
                VisitorSide.BOTTOM,
                freeAreas = listOf(arena),
            )

        vm.autoSetup(request)
        vm.autoSetup(request)

        val project = vm.uiState.value.project
        assertEquals(2, project.blockZones.size, "one drawn zone + one arena, the first arena was replaced")
        assertEquals(listOf("", "Kampfplatz"), project.blockZones.map { it.label })
        assertEquals(request, project.autoSetup)
        assertTrue(
            vm.uiState.value.hardCollisionAssetIds
                .isEmpty(),
        )
    }

    @Test
    fun editingTentType_updatesUntouchedPlacedTents_andSaveAsNewAddsType() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.placePresetAt("saxon_2x4", Vec2(5f, 5f))
        vm.placePresetAt("saxon_2x4", Vec2(15f, 5f))
        vm.selectRopeIndex(0)
        vm.setSelectedRopeLengthScale(0.5f) // the second tent's first rope is tuned by hand
        val type =
            vm.uiState.value.presets
                .first { it.id == "saxon_2x4" }

        vm.savePreset(type.copy(ropeAnchors = type.ropeAnchors.map { it.copy(lengthMeters = 3f) }))

        val (untouched, tuned) = vm.assets()
        assertTrue(untouched.ropes.all { it.lengthMeters == 3f }, "untouched tent follows the type")
        assertEquals(0.5f, tuned.ropes.first().lengthMeters, "hand-tuned rope stays")
        assertTrue(
            vm.uiState.value.presets
                .first { it.id == "saxon_2x4" }
                .userModified,
        )

        vm.savePreset(type.copy(id = "custom_1", name = "Mein Zelt"))
        assertTrue(
            vm.uiState.value.presets
                .any { it.id == "custom_1" && it.name == "Mein Zelt" },
        )
        assertTrue(
            vm.uiState.value.presets
                .any { it.id == "saxon_2x4" },
            "the original stays",
        )
    }

    @Test
    fun setSpacing_changesWarnings_syncsAutoSetup_andUndoes() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.autoSetup(AutoSetupRequest(Parcel(30f, 20f), listOf("round_3x3"), emptyList(), VisitorSide.TOP))
        vm.placePresetAt("round_3x3", Vec2(5f, 15f))
        vm.placePresetAt("round_3x3", Vec2(13f, 15f))
        val before =
            vm.uiState.value.warnings
                .count { it.kind == IssueKind.NARROW_WALKWAY }

        vm.setSpacing(SpacingRules(tentToTent = 10f))
        assertEquals(10f, vm.uiState.value.project.spacing.tentToTent)
        assertEquals(
            10f,
            vm.uiState.value.project.autoSetup!!
                .spacing.tentToTent,
            "reopening the auto setup shows the same gaps",
        )
        assertTrue(
            vm.uiState.value.warnings
                .count { it.kind == IssueKind.NARROW_WALKWAY } > before,
            "a wider walkway warns about the close pair",
        )

        vm.undo()
        assertEquals(SpacingRules(), vm.uiState.value.project.spacing)
    }

    @Test
    fun autoSetup_storesItsSpacingOnTheProject() {
        val vm = PlannerViewModel(InMemoryStorage())
        val rules = SpacingRules(tentToTent = 2f, tentToSailMax = 5f)
        vm.autoSetup(
            AutoSetupRequest(Parcel(40f, 30f), listOf("round_4", "round_4"), listOf("sun_sail_6x6_flat"), VisitorSide.TOP, spacing = rules),
        )
        assertEquals(rules, vm.uiState.value.project.spacing)
        assertTrue(
            vm.uiState.value.warnings
                .none { it.kind == IssueKind.NARROW_WALKWAY },
            vm.uiState.value.warnings
                .toString(),
        )
    }

    @Test
    fun spacing_survivesSaveAndRestart() {
        val storage = InMemoryStorage()
        val vm = PlannerViewModel(storage)
        vm.setSpacing(SpacingRules(tentToPath = 0.7f))
        vm.saveProject()
        assertEquals(
            0.7f,
            PlannerViewModel(storage)
                .uiState.value.project.spacing.tentToPath,
        )
    }

    @Test
    fun editingTentType_marginFollowsUnlessChanged_andNewTypeCanBePlaced() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.placePresetAt("round_4", Vec2(5f, 5f))
        val type =
            vm.uiState.value.presets
                .first { it.id == "round_4" }
        vm.savePreset(type.copy(defaultSafetyMarginMeters = 0.25f))
        assertEquals(0.25f, vm.assets().single().safetyMarginMeters)

        vm.savePreset(type.copy(id = "custom_x", name = "Eigenes Rundzelt", defaultSafetyMarginMeters = 0.1f))
        vm.placePresetAt("custom_x", Vec2(15f, 5f))
        val placed = vm.assets().last()
        assertEquals("custom_x", placed.presetId)
        assertEquals(0.1f, placed.safetyMarginMeters)
        assertEquals(0.25f, vm.assets().first().safetyMarginMeters, "the original type's tent is untouched")
    }

    @Test
    fun editingTentType_resizeMovesNothingButRecomputesWarnings() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.placePresetAt("round_3x3", Vec2(5f, 5f))
        vm.placePresetAt("round_3x3", Vec2(9f, 5f))
        val positions = vm.assets().map { it.position }
        val type =
            vm.uiState.value.presets
                .first { it.id == "round_3x3" }
        vm.savePreset(
            de.voculupo.historicalcamplayout.features.planner.state.PlannerDomainOps
                .resized(type, 5f, 5f),
        ) // now the two overlap
        assertEquals(positions, vm.assets().map { it.position })
        assertTrue(
            vm.uiState.value.hardCollisionAssetIds.size == 2,
            vm.uiState.value.warnings
                .toString(),
        )
    }

    @Test
    fun ownSpacingOnType_changesWarnings() {
        val vm = PlannerViewModel(InMemoryStorage())
        // Default margin 1 m: padded boxes 3 m apart, fine for the camp's 1.5 m.
        vm.placePresetAt("round_3x3", Vec2(5f, 15f))
        vm.placePresetAt("round_3x3", Vec2(13f, 15f))
        assertTrue(
            vm.uiState.value.warnings
                .none { it.kind == IssueKind.NARROW_WALKWAY },
        )
        val type =
            vm.uiState.value.presets
                .first { it.id == "round_3x3" }
        vm.savePreset(type.copy(spacing = SpacingOverride(toTents = 6f)))
        assertTrue(
            vm.uiState.value.warnings
                .any { it.kind == IssueKind.NARROW_WALKWAY },
        )
    }

    // --- Found by the monkey test: tents must never end up hanging over the parcel edge ---

    private fun PlannerViewModel.inside(): Boolean {
        val s = uiState.value
        val byId = s.presets.associateBy { it.id }
        return s.project.assets.all { a ->
            val b = GeometryEngine.assetBodyAabb(a, byId.getValue(a.presetId))
            b.left >= -0.001f && b.top >= -0.001f && b.right <= s.project.parcel.widthMeters + 0.001f &&
                b.bottom <= s.project.parcel.heightMeters + 0.001f
        }
    }

    @Test
    fun rotatingAtTheEdge_keepsTheTentInside_andRestartMovesNothing() {
        val storage = InMemoryStorage()
        val vm = PlannerViewModel(storage)
        vm.placePresetAt("sun_sail_6x2_flat", Vec2(22f, 1f)) // long side along the top edge
        vm.setSelectedRotation(90f) // now 6 m deep at y = 1
        vm.commitHistory()
        assertTrue(vm.inside(), "slider rotation pushed it back in")
        vm.rotateSelected(45f)
        assertTrue(vm.inside(), "gesture rotation pushed it back in")
        assertEquals(90f + 45f, vm.assets().single().rotationDeg)
        vm.saveProject()
        assertEquals(vm.assets(), PlannerViewModel(storage).assets(), "nothing jumps on restart")
    }

    @Test
    fun autoSetupOnASmallerParcel_movesKeptTentsInside() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.updateParcel(60f, 40f)
        vm.placePresetAt("round_4", Vec2(55f, 35f))
        vm.autoSetup(AutoSetupRequest(Parcel(20f, 15f), listOf("round_3x3"), emptyList(), VisitorSide.TOP, replaceExisting = false))
        assertTrue(vm.inside(), vm.assets().toString())
    }

    @Test
    fun placingFarOutside_landsExactlyInside() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.placePresetAt("saxon_4x6_closed", Vec2(1e6f, 1e6f)) // floats are coarse out there
        assertTrue(vm.inside(), vm.assets().toString())
    }

    @Test
    fun parcelLargerThanTheLimit_isRejected() {
        val vm = PlannerViewModel(InMemoryStorage())
        vm.updateParcel(Parcel.MAX_METERS + 1f, 20f)
        assertEquals(Parcel(), vm.uiState.value.project.parcel)
        vm.updateParcel(Parcel.MAX_METERS, 20f)
        assertEquals(Parcel.MAX_METERS, vm.uiState.value.project.parcel.widthMeters)
    }
}
